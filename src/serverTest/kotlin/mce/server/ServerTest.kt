package mce.server

import kotlinx.coroutines.runBlocking
import mce.phase.Id
import mce.phase.frontend.parse.Term
import mce.server.build.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerTest {
    private fun runServer(action: suspend Server.() -> Unit) {
        runBlocking {
            Server().action()
        }
    }

    @Test
    fun hover() {
        runServer {
            val name = "server"
            val id = Id(0, 0)
            assertIs<Term.Bool>(hover(name, id).type)
        }
    }

    @Test
    fun counter() {
        runServer {
            val name = "server"
            val id = Id(0, 0)
            hover(name, id)
            assertEquals(1, build.getCount(Key.ElabResult(name)))
            hover(name, id)
            assertEquals(1, build.getCount(Key.ElabResult(name)))
        }
    }

    @Test
    fun completion() {
        runServer {
            val name = "completion"
            val id = Id(0, 0)
            val item = completion(name, id).first()
            assertEquals("a", item.name)
            assertIs<Term.Int>(item.type)
        }
    }
}
