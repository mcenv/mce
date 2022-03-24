package mce.phase

import mce.fetch
import mce.phase.frontend.parse.Item
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertFails

class ParseTest {
    private fun parse(name: String): Item = fetch(Key.SurfaceItem(name))

    @Test
    fun infixNonAssociative() {
        assertFails {
            parse("infix_non_associative")
        }
    }
}
