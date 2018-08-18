package pbandk.gen

import com.google.protobuf.compiler.PluginProtos
import pbandk.gen.pb.CodeGeneratorRequest
import pbandk.gen.pb.CodeGeneratorResponse
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

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

    actual fun interfaceIncludesProperty(ctx: FileBuilder.Context, prop: String, interfaceName: String): Boolean {
        return PropertyOverrideChecker.classIncludesProperty(interfaceName, prop, ctx.params.getOrDefault("kotlin_extra_classpath", ""))
    }
}

object PropertyOverrideChecker {
    private val classloaderCache = ConcurrentHashMap<String, ClassLoader>()
    private val classCache = ConcurrentHashMap<ClassCacheKey, KClass<out Any>>()
    private val overridesCache = ConcurrentHashMap<OverridesCacheKey, Boolean>()

    private fun getClassLoader(classpath: String): ClassLoader {
        return classloaderCache.computeIfAbsent(classpath) { classPathString ->
            when {
                classpath.isEmpty() -> javaClass.classLoader
                else -> URLClassLoader(classPathString.split(';').map { p -> File(p).toURI().toURL() }.toTypedArray())
            }
        }
    }

    private fun getClass(className: String, classPath: String): KClass<out Any> {
        return classCache.computeIfAbsent(ClassCacheKey(className, classPath)) { key -> getClassLoader(key.classPath).loadClass(key.className).kotlin }
    }

    fun classIncludesProperty(className: String, propertyName: String, classPath: String): Boolean {
        return overridesCache.computeIfAbsent(OverridesCacheKey(propertyName, className, classPath)) { key -> getClass(key.className, key.classPath).members.map { it.name }.contains(propertyName) }
    }
}

private data class ClassCacheKey(val className: String, val classPath: String)
private data class OverridesCacheKey(val propertyName: String, val className: String, val classPath: String)