package pbandk.gen

import pbandk.gen.pb.CodeGeneratorRequest
import pbandk.gen.pb.CodeGeneratorResponse

data class InterfaceDescriptor (val name: String, val properties: Set<String>, val visitorType: String?)

expect object Platform {
    fun stderrPrintln(str: String)
    fun stdinReadRequest(): CodeGeneratorRequest
    fun stdoutWriteResponse(resp: CodeGeneratorResponse)

    fun describeInterface(ctx: FileBuilder.Context, interfaceName: String): InterfaceDescriptor
}