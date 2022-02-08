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
    private fun elaborate(items: Map<String, C.Item>, path: String): Elaborate.Output = Elaborate(items, Parse(read(path)))

    @Test
    fun elaborate() {
        val boolean = S.Term.Boolean(UUID(0, 0))
        val ff = S.Term.BooleanOf(false, UUID(0, 1))
        val (elaborated, diagnostics, types) = elaborate(emptyMap(), "/elaborate.mce")

        assert(diagnostics.isEmpty())
        assertIs<S.Term.Type>(types[boolean.id]!!.value)
        assertIs<S.Term.Boolean>(types[ff.id]!!.value)
        assertEquals(C.Item.Definition(emptyList(), "a", C.Term.BooleanOf(false), C.Value.Boolean), elaborated)
    }

    @Test
    fun apply() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/apply.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionIntro() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/function_intro.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun functionElim() {
        val (identity, diagnostics1, _) = elaborate(emptyMap(), "/identity.mce")
        val (_, diagnostics2, _) = elaborate(mapOf(identity.name to identity), "/function_elim.mce")
        assert(diagnostics1.isEmpty())
        assert(diagnostics2.isEmpty())
    }

    @Test
    fun testFunctionClosed() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/function_closed.mce")
        assert(diagnostics.contains(Diagnostic.VariableNotFound("a", UUID(0, 0))))
    }

    @Test
    fun testLet() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/let.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun testInvalidDefinition() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/invalid_definition.mce")
        assert(diagnostics.contains(Diagnostic.DefinitionNotFound("a", UUID(0, 0))))
    }

    @Test
    fun testDependentCompound() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/compound.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun testCodeIntroduction() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/code_intro.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun testCodeElimination() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/code_elim.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun testStageMismatch() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/stage_mismatch.mce")
        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, UUID(0, 0))))
    }

    @Test
    fun testPhaseMismatch() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/phase_mismatch.mce")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun testPhaseMismatchLocal() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/phase_mismatch_local.mce")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0))))
    }

    @Test
    fun testMatchBoolean() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/match_boolean.mce")
        assert(diagnostics.isEmpty())
    }

    @Test
    fun testMatchVariable() {
        val (_, diagnostics, _) = elaborate(emptyMap(), "/match_variable.mce")
        assert(diagnostics.isEmpty())
    }
}
