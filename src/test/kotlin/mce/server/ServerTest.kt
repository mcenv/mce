package mce.server

import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import mce.graph.Surface as S

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
            val id = UUID(0, 0)
            assertIs<S.Term.Bool>(hover(name, id).type)
        }
    }

    @Test
    fun counter() {
        runServer {
            val name = "server"
            val id = UUID(0, 0)
            hover(name, id)
            assertEquals(1, getCount(Key.ElaborateResult(name)))
            hover(name, id)
            assertEquals(1, getCount(Key.ElaborateResult(name)))
        }
    }

    @Test
    fun completion() {
        runServer {
            val name = "completion"
            val id = UUID(0, 0)
            val item = completion(name, id).first()
            assertEquals("a", item.name)
            assertIs<S.Term.Int>(item.type)
        }
    }
}
