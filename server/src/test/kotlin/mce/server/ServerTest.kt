package mce.server

import kotlinx.coroutines.runBlocking
import mce.graph.Surface
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerTest {
    @Test
    fun testHover() {
        runBlocking {
            Server().run {
                register("a", """(definition a () false boolean #0-0-0-0-0 false)""")
                assertIs<Surface.Term.Boolean>(hover("a", UUID(0, 0)).type)
            }
        }
    }

    @Test
    fun testCounter() {
        runBlocking {
            Server().run {
                register("a", """(definition a () false boolean #0-0-0-0-0 false)""")
                hover("a", UUID(0, 0))
                assertEquals(1, getCount(Key.Elaborated("a")))
                hover("a", UUID(0, 0))
                assertEquals(1, getCount(Key.Elaborated("a")))
            }
        }
    }
}
