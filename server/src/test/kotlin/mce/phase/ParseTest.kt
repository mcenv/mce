package mce.phase

import mce.fetch
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertIs
import mce.graph.Surface as S

class ParseTest {
    private fun parse(name: String): S.Item = fetch(Key.SurfaceItem(name))

    @Test
    fun parse() {
        parse("parse")
    }

    @Test
    fun infixLeftAssociative() {
        val item = parse("infix_left_associative")
        println(item)
        assertIs<S.Item.Def>(item)
        val left = item.body
        assertIs<S.Term.Apply>(left)
        assertIs<S.Term.Apply>(left.arguments.first())
    }
}
