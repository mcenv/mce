package mce.server

import kotlinx.coroutines.runBlocking
import mce.Id
import mce.phase.frontend.decode.Term
import mce.protocol.CompletionRequest
import mce.protocol.HoverRequest
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
            val target = Id(0, 0)
            assertIs<Term.Bool>(hover(HoverRequest(name, target, 0)).type)
        }
    }

    @Test
    fun counter() {
        runServer {
            val name = "server"
            val target = Id(0, 0)
            hover(HoverRequest(name, target, 0))
            assertEquals(1, build.getCount(Key.ElabResult(name)))
            hover(HoverRequest(name, target, 1))
            assertEquals(1, build.getCount(Key.ElabResult(name)))
        }
    }

    @Test
    fun completion() {
        runServer {
            val name = "completion"
            val id = Id(0, 0)
            val item = completion(CompletionRequest(name, id, 0)).first()
            assertEquals("a", item.name)
            assertIs<Term.Int>(item.type)
        }
    }
}
