package pbandk.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinProtobufPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply('com.google.protobuf')

        def ext = project.extensions.create("kotlinProtobuf", KotlinProtobufExtension)

        configureExtensions(project)

        def pbandkVersion = determineVersion(project, ext)

        if (ext.addRuntimeDependencies) {
            project.dependencies.add("compile", [group: "com.github.cretz.pbandk",
                                      name: "pbandk-runtime-jvm",
                                      version: pbandkVersion])
        }

        def kotlinProtoExecutable = configureBinary(project, pbandkVersion)

        project.protobuf {
            generatedFilesBaseDir = "${project.buildDir}/generated-sources"
            protoc {
                artifact = "com.google.protobuf:protoc:${ext.protocVersion}"
            }

            // This must be set to the batch file on Windows
            plugins {
                kotlin {
                    path = kotlinProtoExecutable
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
                            option "kotlin_extra_classpath=${project.configurations.kotlinProtobufExtensions.asPath.replace(':', ';')}"
                        }
                    }
                }
            }
        }
    }

    def configureExtensions(Project project) {
        def extensionsConfiguration = project.configurations.create("kotlinProtobufExtensions")
        project.configurations.getByName("compile").extendsFrom(extensionsConfiguration)
    }

    def determineVersion(Project project, KotlinProtobufExtension extension) {
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

    def configureBinary(Project project, String version) {
        def toolsConfiguration = project.configurations.create("kotlinProtobufTools")
        def tools = project.dependencies.add("kotlinProtobufTools", [
                group: "com.github.cretz.pbandk",
                name: "pbandk-protoc-gen-kotlin-jvm",
                version: version,
                classifier: "dist",
                ext: "zip"
        ])

        def toolsDir = project.file("${project.rootProject.projectDir}/.gradle/tools")
        toolsDir.mkdirs()
        def targetDir = project.file("$toolsDir/protoc-gen-kotlin-$version")

        if (version.endsWith("-SNAPSHOT") || !targetDir.exists()) {
            def toolsArchive = project.zipTree(toolsConfiguration.fileCollection(tools).singleFile)

            project.copy {
                from toolsArchive
                into toolsDir
            }
        }

        return "$targetDir/bin/protoc-gen-kotlin"
    }
}
