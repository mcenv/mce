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
                val name = "server"
                register(name, read("/$name.mce"))
                assertIs<Surface.Term.Bool>(hover(name, UUID(0, 0)).type)
            }
        }
    }

    @Test
    fun counter() {
        runBlocking {
            Server().run {
                val name = "server"
                register(name, read("/$name.mce"))
                hover(name, UUID(0, 0))
                assertEquals(1, getCount(Key.ElaboratedOutput(name)))
                hover(name, UUID(0, 0))
                assertEquals(1, getCount(Key.ElaboratedOutput(name)))
            }
        }
    }
}
