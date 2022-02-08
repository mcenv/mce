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
        val boolean = S.Term.Boolean(UUID(0, 0))
        val ff = S.Term.BooleanOf(false, UUID(0, 1))
        val (elaborated, diagnostics, types) = elaborate(emptyMap(), "elaborate")

        assert(diagnostics.isEmpty())
        assertIs<S.Term.Type>(types[boolean.id]!!.value)
        assertIs<S.Term.Boolean>(types[ff.id]!!.value)
        assertEquals(C.Item.Definition(emptyList(), "elaborate", C.Term.BooleanOf(false), C.Value.Boolean), elaborated)
    }

    @Test
    fun apply() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "apply")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionIntro() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "function_intro")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionElim() {
        val (identity, diagnostics1, _) = elaborate(emptyMap(), "identity")
        val (_, diagnostics2, _) = elaborate(mapOf(identity.name to identity), "function_elim")
        assert(diagnostics1.isEmpty())
        assert(diagnostics2.isEmpty())
    }

    @Test
    fun functionClosed() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "function_closed")
        assert(diagnostics.contains(Diagnostic.VariableNotFound("a", UUID(0, 0))))
    }

    @Test
    fun let() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "let")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun invalidDefinition() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "invalid_definition")
        assert(diagnostics.contains(Diagnostic.DefinitionNotFound("a", UUID(0, 0))))
    }

    @Test
    fun compound() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "compound")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun codeIntro() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "code_intro")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun codeElim() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "code_elim")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun stageMismatch() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "stage_mismatch")
        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, UUID(0, 0))))
    }

    @Test
    fun phaseMismatch() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "phase_mismatch")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun phaseMismatchLocal() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "phase_mismatch_local")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun matchBoolean() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "match_boolean")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun matchVariable() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "match_variable")
        assert(diagnostics.isEmpty())
    }
}
