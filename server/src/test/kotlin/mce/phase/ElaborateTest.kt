package mce.phase

import mce.Diagnostic
import mce.read
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    private fun elaborate(items: Map<String, C.Item>, name: String): Elaborate.Output = Elaborate(items, Parse(name, read("/$name.mce")))

    @Test
    fun elaborate() {
        val bool = S.Term.Bool(UUID(0, 0))
        val ff = S.Term.BoolOf(false, UUID(0, 1))
        val (elaborated, _, diagnostics, types) = elaborate(emptyMap(), "elaborate")

        assert(diagnostics.isEmpty())
        assertIs<S.Term.Type>(types[bool.id]!!.value)
        assertIs<S.Term.Bool>(types[ff.id]!!.value)
        assertEquals(C.Item.Def(emptyList(), "elaborate", C.Value.Bool, C.Term.BoolOf(false)), elaborated)
    }

    @Test
    fun apply() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "apply")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionIntro() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "function_intro")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionElim() {
        val (identity, _, diagnostics1, _) = elaborate(emptyMap(), "identity")
        val (_, _, diagnostics2, _) = elaborate(mapOf(identity.name to identity), "function_elim")
        assert(diagnostics1.isEmpty())
        assert(diagnostics2.isEmpty())
    }

    @Test
    fun functionClosed() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "function_closed")
        assert(diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0))))
    }

    @Test
    fun let() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "let")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun nameNotFound() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "name_not_found")
        assert(diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0))))
    }

    @Test
    fun compound() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "compound")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun codeIntro() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "code_intro")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun codeElim() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "code_elim")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun stageMismatch() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "stage_mismatch")
        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, UUID(0, 0))))
    }

    @Test
    fun phaseMismatch() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "phase_mismatch")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun phaseMismatchLocal() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "phase_mismatch_local")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun matchBoolean() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "match_boolean")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun matchVariable() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "match_variable")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun thunkIntro() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "thunk_intro")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun thunkElim() {
        val (_, _, diagnostics, _) = elaborate(emptyMap(), "thunk_elim")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun impureDefinition() {
        val (thunkIntro, _, diagnostics1, _) = elaborate(emptyMap(), "thunk_intro")
        val (_, _, diagnostics2, _) = elaborate(mapOf(thunkIntro.name to thunkIntro), "impure_definition")
        assert(diagnostics1.isEmpty())
        assert(diagnostics2.contains(Diagnostic.EffectMismatch(UUID(0, 0))))
    }
}
