package pbandk

actual object Util : UtilInterface {
    override fun stringToUtf8(str: String) = str.toByteArray()
    override fun utf8ToString(bytes: ByteArray) = bytes.toString(Charsets.UTF_8)
}