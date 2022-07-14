package pbandk

interface UtilInterface {
    fun stringToUtf8(str: String): ByteArray
    fun utf8ToString(bytes: ByteArray): String
}
expect object Util : UtilInterface