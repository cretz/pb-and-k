package pbandk.gen

import com.google.protobuf.compiler.PluginProtos
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import pbandk.gen.pb.CodeGeneratorRequest
import pbandk.gen.pb.CodeGeneratorResponse
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmName

// Set this to false to use the JVM marshal/unmarshal for code gen proto
const val useJvmProto = false

actual object Platform {
    actual fun stderrPrintln(str: String) = System.err.println(str)
    actual fun stdinReadRequest() = System.`in`.readBytes().let { bytes ->
        if (useJvmProto) BootstrapConverter.fromReq(PluginProtos.CodeGeneratorRequest.parseFrom(bytes))
        else CodeGeneratorRequest.protoUnmarshal(bytes)
    }
    actual fun stdoutWriteResponse(resp: CodeGeneratorResponse) =
        if (useJvmProto) BootstrapConverter.toResp(resp).writeTo(System.out)
        else System.out.write(resp.protoMarshal())

    actual fun describeInterface(ctx: FileBuilder.Context, interfaceName: String) = AstInspector.describe(interfaceName, sourcepath(ctx)) ?: ReflectionInspector.describe(interfaceName, classpath(ctx))

    private fun classpath(ctx: FileBuilder.Context) = ctx.params.getOrDefault("kotlin_extra_classpath", "")
    private fun sourcepath(ctx: FileBuilder.Context) = ctx.params.getOrDefault("kotlin_extra_sourcepath", "")
}

private data class AstSourcePackage (val pkg: String, val sourcepath: String)
private data class AstSourcePackageInspector (val sourcePackage: AstSourcePackage, val manager: PsiManager, val fileSystem: CoreLocalFileSystem) {
    val sourcePaths = sourcePackage.sourcepath.split(';')

    val filesInPackage = sourcePaths.map { File(it + "/" + sourcePackage.pkg.replace('.', '/')) }
        .filter { it.isDirectory }
        .flatMap { it.listFiles().toList() }
        .filter { it.name.endsWith(".kt") }
        .map { manager.findFile(CoreLocalVirtualFile(fileSystem, it)) }
        .filterIsInstance<KtFile>()

    val classesInPackage = filesInPackage.flatMap { it.declarations }.filterIsInstance<KtClass>()
    val classNames = classesInPackage.asSequence().map { it.name }.filterNotNull().toSet()
    val importsByFile = filesInPackage.associate { file -> file.name to (file.importList?.imports ?: emptyList()).map { it.importedFqName.toString() } }

    private fun resolveType(shortName: String, referencingClass: KtClass) = when {
        shortName in classNames -> "${sourcePackage.pkg}.$shortName"
        else -> (importsByFile[referencingClass.containingKtFile.name] ?: emptyList()).firstOrNull { it.endsWith(shortName) } ?: shortName
    }

    fun resolveProperties(cls: KtClass) = cls.declarations.asSequence().filterIsInstance<KtProperty>().map { it.name }.filterNotNull().toSet()

    fun resolveVisitorType(cls: KtClass): String? {
        val acceptFun = cls.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == "accept" }
        val typeElement = acceptFun?.children?.filterIsInstance<KtParameterList>()?.firstOrNull()?.parameters?.get(0)?.typeReference?.typeElement

        return if (typeElement is KtUserType) {
            typeElement.referencedName?.let { resolveType(it, cls) }
        } else {
            null
        }
    }

    fun describeInterfaces() = classesInPackage.asSequence().filter { it.isInterface() }.map { cls ->
        InterfaceDescriptor(cls.fqName.toString(), resolveProperties(cls), resolveVisitorType(cls))
    }
}
object AstInspector {
    private val manager: PsiManager by lazy {
        PsiManager.getInstance(
            KotlinCoreEnvironment.createForProduction(
                Disposer.newDisposable(),
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            ).project
        )
    }

    private val filesystem: CoreLocalFileSystem by lazy {
        CoreLocalFileSystem()
    }

    private val packageCache = ConcurrentHashMap<AstSourcePackage, Map<String, InterfaceDescriptor>>()

    fun describe(className: String, sourcePath: String) = packageCache.computeIfAbsent(AstSourcePackage(className.substringBeforeLast("."), sourcePath)) { sourcePackage ->
        AstSourcePackageInspector(sourcePackage, manager, filesystem).describeInterfaces().associateBy { it.name }
    }[className]
}

object ReflectionInspector {
    private val classloaderCache = ConcurrentHashMap<String, ClassLoader>()
    private val classCache = ConcurrentHashMap<ReflectionClass, InterfaceDescriptor>()

    private fun getClassLoader(classpath: String): ClassLoader {
        return classloaderCache.computeIfAbsent(classpath) { classPathString ->
            when {
                classpath.isEmpty() -> javaClass.classLoader
                else -> URLClassLoader(classPathString.split(';').map { p -> File(p).toURI().toURL() }.toTypedArray())
            }
        }
    }

    fun describe(className: String, classPath: String) = classCache.computeIfAbsent(ReflectionClass(className, classPath)) { key ->
        val cls = getClassLoader(key.classPath).loadClass(key.className).kotlin
        InterfaceDescriptor(cls.jvmName, cls.memberProperties.map { it.name }.toSet(), null)
    }
}

private data class ReflectionClass(val className: String, val classPath: String)