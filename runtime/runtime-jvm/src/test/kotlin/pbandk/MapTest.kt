package pbandk

import org.junit.Test
import pbandk.testpb.Bar
import pbandk.testpb.Foo
import pbandk.testpb.MessageWithMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapTest {
    @Test
    fun testMap() {

        // This used to fail because it assumed messages could be packed
        val bytes = byteArrayOf(10, 6, 10, 1, 49, 18, 1, 97, 10, 6, 10, 1, 50, 18, 1, 98)
        val expected = MessageWithMap(map = mapOf("1" to "a", "2" to "b"))

        assertTrue(expected.protoMarshal().contentEquals(bytes))
        assertEquals(expected, MessageWithMap.protoUnmarshal(bytes))
    }

    @Test
    fun testJavaKotlinInterop() {
        val java = pbandk.testpb.Test.MessageWithMap
                .newBuilder()
                .putMap("1", "a")
                .putMap("2", "b")
                .build()
        val kotlin = MessageWithMap(map = mapOf("1" to "a", "2" to "b"))

        assertEquals(kotlin, MessageWithMap.protoUnmarshal(java.toByteArray()))
        assertEquals(java, pbandk.testpb.Test.MessageWithMap.parseFrom(kotlin.protoMarshal()))
        assertTrue(kotlin.protoMarshal().contentEquals(java.toByteArray()))
    }
}