package mce.pass

import mce.Id
import mce.ast.core.Item
import mce.ast.core.Term
import mce.fetch
import mce.pass.backend.Stage
import mce.server.build.Key
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StageTest {
    private fun stage(name: String): Stage.Result = fetch(Key.StageResult(name))

    @Test
    fun reduce() {
        val result = stage("code_elim")
        val def = result.item as Item.Def
        assertEquals(Term.BoolOf(false, Id(0, 0)), def.body)
    }

    @Test
    fun command() {
        val result = stage("command")
        val def = result.item as Item.Def
        val body = def.body
        assertIs<Term.Command>(body)
        val command = body.body
        assertIs<Term.StringOf>(command)
        assertEquals("say command", command.value)
    }

    @Test
    fun stage_command() {
        val result = stage("stage_command")
        val def = result.item as Item.Def
        val body = def.body
        assertIs<Term.Command>(body)
        val command = body.body
        assertIs<Term.StringOf>(command)
        assertEquals("say command", command.value)
    }

    @Test
    fun concat_command() {
        val result = stage("concat_command")
        val def = result.item as Item.Def
        val body = def.body
        assertIs<Term.Command>(body)
        val command = body.body
        assertIs<Term.StringOf>(command)
        assertEquals("say command", command.value)
    }
}
