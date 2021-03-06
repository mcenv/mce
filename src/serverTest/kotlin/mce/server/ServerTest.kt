package mce.server

import kotlinx.coroutines.runBlocking
import mce.Id
import mce.ast.surface.Term
import mce.pass.Config
import mce.protocol.Request
import mce.server.build.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerTest {
    private fun runServer(action: suspend Server.() -> Unit) {
        runBlocking {
            Server(Config).action()
        }
    }

    @Test
    fun hover() {
        runServer {
            val name = "server"
            val target = Id(0, 0)
            assertIs<Term.Bool>(hover(Request.Hover(name, target, 0)).type)
        }
    }

    @Test
    fun counter() {
        runServer {
            val name = "server"
            val target = Id(0, 0)
            hover(Request.Hover(name, target, 0))
            assertEquals(1, build.getCount(Key.ElabResult(name)))
            hover(Request.Hover(name, target, 1))
            assertEquals(1, build.getCount(Key.ElabResult(name)))
        }
    }

    @Test
    fun completion() {
        runServer {
            val name = "completion"
            val id = Id(0, 0)
            val completion = completion(Request.Completion(name, id, 0))
            assertEquals(1, completion.items.size)
            val item = completion.items.first()
            assertEquals("a", item.name)
            assertIs<Term.Int>(item.type)
        }
    }
}
