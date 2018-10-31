package pbandk.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

class KotlinProtobufPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply('com.google.protobuf')

        def ext = project.extensions.create("kotlinProtobuf", KotlinProtobufExtension)

        configureExtensions(project)

        def tools = configureTools(project)

        def pbandkVersion = determineVersion(ext)

        if (ext.addRuntimeDependencies) {
            project.dependencies.add("compile", [group: "com.github.cretz.pbandk",
                                      name: "pbandk-runtime-jvm",
                                      version: pbandkVersion])
        }

        project.protobuf {
            generatedFilesBaseDir = "${project.buildDir}/generated-sources"
            protoc {
                artifact = "com.google.protobuf:protoc:${ext.protocVersion}"
            }

            // This must be set to the batch file on Windows
            plugins {
                kotlin {
                    path = getBinaryDirectory(getTargetDirectory(project, getToolsDirectory(project), pbandkVersion))
                }
            }

            generateProtoTasks {
                all().each { task ->
                    task.builtins {
                        remove java
                    }

                    task.plugins {
                        kotlin {
                            option "kotlin_package=${ext.kotlinPackage}"
                            option "kotlin_extra_classpath=${project.configurations.getByName("kotlinProtobufExtensions").asPath.replace(':', ';')}"
                            def kotlinSourceSetRoot = project.hasProperty("android") ? project.android : project
                            option "kotlin_extra_sourcepath=${kotlinSourceSetRoot.sourceSets.main.kotlin.srcDirs.join(';')}"
                        }
                    }
                }
            }
        }

        project.afterEvaluate {

            installBinary(project, pbandkVersion, tools)

        }
    }

    def configureExtensions(Project project) {
        def extensionsConfiguration = project.configurations.create("kotlinProtobufExtensions")
        project.configurations.getByName("compile").extendsFrom(extensionsConfiguration)
    }

    def configureTools(Project project) {
        project.configurations.create("kotlinProtobufTools")
        def tools = project.dependencies.add("kotlinProtobufTools", [
                group: "com.github.cretz.pbandk",
                name: "pbandk-protoc-gen-kotlin-jvm",
                version: version,
                classifier: "dist",
                ext: "zip"
        ])
        return tools
    }

    def getToolsDirectory(Project project) {
        return project.file("${project.rootProject.projectDir}/.gradle/tools")
    }

    def getTargetDirectory(Project project, File toolsDir, String version) {
        return project.file("$toolsDir/protoc-gen-kotlin-$version")
    }

    def getBinaryDirectory(File targetDir) {
        return "$targetDir/bin/protoc-gen-kotlin"
    }

    def determineVersion(KotlinProtobufExtension extension) {
        if (extension.toolsVersion) {
            return extension.toolsVersion
        } else {
            def properties = new Properties()
            this.getClass().getResource( '/META-INF/gradle-plugins/com.github.cretz.pbandk.pbandk-gradle-plugin.properties').withInputStream {
                properties.load(it)
            }

            return properties.version
        }
    }

    def installBinary(Project project, String version, Dependency tools) {
        def toolsDir = getToolsDirectory(project)
        toolsDir.mkdirs()
        def targetDir = getTargetDirectory(project, toolsDir, version)

        if (version.endsWith("-SNAPSHOT") || !targetDir.exists()) {
            def toolsArchive = project.zipTree(project.configurations.getByName("kotlinProtobufTools").fileCollection(tools).singleFile)

            project.copy {
                from toolsArchive
                into toolsDir
            }
        }
    }
}
