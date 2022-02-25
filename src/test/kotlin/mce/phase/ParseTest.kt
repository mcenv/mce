package mce.phase

import mce.fetch
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertFails
import mce.graph.Surface as S

class ParseTest {
    private fun parse(name: String): S.Item = fetch(Key.SurfaceItem(name))

    @Test
    fun parse() {
        parse("parse")
    }

    @Test
    fun infixNonAssociative() {
        assertFails {
            parse("infix_non_associative")
        }
    }
}