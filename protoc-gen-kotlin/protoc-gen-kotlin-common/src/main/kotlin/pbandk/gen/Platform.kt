package pbandk.gen

import pbandk.gen.pb.CodeGeneratorRequest
import pbandk.gen.pb.CodeGeneratorResponse

expect object Platform {
    fun stderrPrintln(str: String)
    fun stdinReadRequest(): CodeGeneratorRequest
    fun stdoutWriteResponse(resp: CodeGeneratorResponse)

    fun interfaceIncludesProperty(prop: String, interfaceName: String): Boolean
}