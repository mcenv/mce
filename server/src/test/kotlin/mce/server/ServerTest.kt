package mce.server

import kotlinx.coroutines.runBlocking
import mce.graph.Surface
import mce.read
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerTest {
    @Test
    fun hover() {
        runBlocking {
            Server().run {
                register("a", read("/server.mce"))
                assertIs<Surface.Term.Boolean>(hover("a", UUID(0, 0)).type)
            }
        }
    }

    @Test
    fun counter() {
        runBlocking {
            Server().run {
                register("a", read("/server.mce"))
                hover("a", UUID(0, 0))
                assertEquals(1, getCount(Key.Elaborated("a")))
                hover("a", UUID(0, 0))
                assertEquals(1, getCount(Key.Elaborated("a")))
            }
        }
    }
}
