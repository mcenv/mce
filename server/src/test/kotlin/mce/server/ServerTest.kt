package mce.server

import kotlinx.coroutines.runBlocking
import mce.graph.Dsl.boolean
import mce.graph.Dsl.definition
import mce.graph.Dsl.ff
import mce.graph.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerTest {
    @Test
    fun testHover() {
        runBlocking {
            val server = Server()
            val ff = ff()
            server.register(definition("a", false, boolean(), ff))
            assertIs<Surface.Term.Boolean>(server.hover("a", ff.id).type)
        }
    }

    @Test
    fun testCounter() {
        runBlocking {
            val server = Server()
            val ff = ff()
            server.register(definition("a", false, boolean(), ff))
            server.hover("a", ff.id)
            assertEquals(1, server.getCount("a"))
            server.hover("a", ff.id)
            assertEquals(1, server.getCount("a"))
        }
    }
}
