package mce.phase

import mce.ast.surface.Item
import mce.fetch
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
