package mce.server

import kotlinx.coroutines.runBlocking
import mce.graph.Surface
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
                assertIs<Surface.Term.Bool>(hover(name, UUID(0, 0)).type)
            }
        }
    }

    @Test
    fun counter() {
        runBlocking {
            Server().run {
                val name = "server"
                hover(name, UUID(0, 0))
                assertEquals(1, getCount(Key.ElaborateResult(name)))
                hover(name, UUID(0, 0))
                assertEquals(1, getCount(Key.ElaborateResult(name)))
            }
        }
    }
}
