package mce.pass

import mce.ast.surface.Item
import mce.fetch
import mce.server.build.Key
import kotlin.test.Test
import kotlin.test.assertFails

class ParseTest {
    private fun parse(name: String): Item = fetch(Key.SurfaceItem(name))

    @Test
    fun infix_non_associative() {
        assertFails {
            parse("infix_non_associative")
        }
    }
}
