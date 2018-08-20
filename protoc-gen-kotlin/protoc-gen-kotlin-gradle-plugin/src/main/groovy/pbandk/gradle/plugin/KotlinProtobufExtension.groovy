package pbandk.gradle.plugin

class KotlinProtobufExtension {
    def kotlinPackage
    def protocVersion = '3.6.1'
    def toolsVersion
    def addRuntimeDependencies = true
}
