package mce.phase

import mce.Diagnostic
import mce.fetch
import mce.server.Key
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    private fun elaborate(name: String): Elaborate.Output = fetch(Key.ElaboratedOutput(name))

    private fun Elaborate.Output.success(): Elaborate.Output = apply {
        assert(diagnostics.isEmpty()) { diagnostics.joinToString("\n") }
    }

    @Test
    fun elaborate() {
        val (item, _, _, types) = elaborate("elaborate").success()
        assertIs<S.Term.Type>(types[UUID(0, 0)]!!.value)
        assertIs<S.Term.Bool>(types[UUID(0, 1)]!!.value)
        assertEquals(C.Item.Def(emptyList(), emptyList(), emptySet(), "elaborate", C.Value.Bool, C.Term.BoolOf(false)), item)
    }

    @Test
    fun apply() {
        elaborate("apply").success()
    }

    @Test
    fun functionIntro() {
        elaborate("function_intro").success()
    }

    @Test
    fun functionElim() {
        elaborate("function_elim").success()
    }

    @Test
    fun functionClosed() {
        val (_, _, diagnostics, _) = elaborate("function_closed")
        assert(diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun let() {
        elaborate("let").success()
    }

    @Test
    fun letEscape() {
        val (_, _, diagnostics, _) = elaborate("let_escape")
        assert(diagnostics.contains(Diagnostic.NameNotFound("b", UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun nameNotFound() {
        val (_, _, diagnostics, _) = elaborate("name_not_found")
        assert(diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun compound() {
        elaborate("compound").success()
    }

    @Test
    fun codeIntro() {
        elaborate("code_intro").success()
    }

    @Test
    fun codeElim() {
        elaborate("code_elim").success()
    }

    @Test
    fun stageMismatch() {
        val (_, _, diagnostics, _) = elaborate("stage_mismatch")
        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun phaseMismatch() {
        val (_, _, diagnostics, _) = elaborate("phase_mismatch")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun phaseMismatchLocal() {
        val (_, _, diagnostics, _) = elaborate("phase_mismatch_local")
        assert(diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun matchBoolean() {
        elaborate("match_boolean").success()
    }

    @Test
    fun matchVariable() {
        elaborate("match_variable").success()
    }

    @Test
    fun matchEscape() {
        val (_, _, diagnostics, _) = elaborate("match_escape")
        assert(diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun nestedPatterns() {
        elaborate("nested_patterns").success()
    }

    @Test
    fun thunkIntro() {
        elaborate("thunk_intro").success()
    }

    @Test
    fun thunkElim() {
        elaborate("thunk_elim").success()
    }

    @Test
    fun impureDefinition() {
        val (_, _, diagnostics, _) = elaborate("impure_definition")
        assert(diagnostics.contains(Diagnostic.EffectMismatch(UUID(0, 0)))) { diagnostics.joinToString("\n") }
    }

    @Test
    fun eqIntro() {
        elaborate("eq_intro").success()
    }

    @Test
    fun eqElim() {
        elaborate("eq_elim").success()
    }

    @Test
    fun notEq() {
        val (_, _, diagnostics, _) = elaborate("not_eq")
        assert(diagnostics.any { it is Diagnostic.TypeMismatch }) { diagnostics.joinToString("\n") }
    }

    @Test
    fun meta() {
        val (_, state, _, _) = elaborate("meta").success()
        assertEquals(C.Value.Bool, state.getSolution(0))
    }

    @Test
    fun sym() {
        elaborate("sym").success()
    }

    @Test
    fun symBad() {
        val (_, _, diagnostics, _) = elaborate("sym_bad")
        assert(diagnostics.any { it is Diagnostic.TypeMismatch }) { diagnostics.joinToString("\n") }
    }

    @Test
    fun trans() {
        elaborate("trans").success()
    }

    @Test
    fun transBad() {
        val (_, _, diagnostics, _) = elaborate("trans_bad")
        assert(diagnostics.any { it is Diagnostic.TypeMismatch }) { diagnostics.joinToString("\n") }
    }

    @Test
    fun cong() {
        elaborate("cong").success()
    }

    @Test
    fun inj() {
        elaborate("inj").success()
    }

    @Test
    fun invalidImport() {
        val (_, _, diagnostics, _) = elaborate("invalid_import")
        assert(diagnostics.any { it is Diagnostic.NameNotFound }) { diagnostics.joinToString("\n") }
    }

    @Test
    fun cast() {
        elaborate("cast").success()
    }

    @Test
    fun unfoldDef() {
        elaborate("unfold_def").success()
    }

    @Test
    fun subtypeTrans() {
        elaborate("subtype_trans").success()
    }

    @Test
    fun builtin() {
        elaborate("builtin").success()
    }

    @Test
    fun builtinIllTyped() {
        val (_, _, diagnostics, _) = elaborate("builtin_ill_typed")
        assert(diagnostics.any { it is Diagnostic.TypeMismatch }) { diagnostics.joinToString("\n") }
    }

    @Test
    fun useBuiltin() {
        elaborate("use_builtin").success()
    }

    @Test
    fun builtinOpen() {
        elaborate("builtin_open").success()
    }

    @Test
    fun zeroAdd() {
        elaborate("zero_add").success()
    }

    @Test
    fun addZero() {
        elaborate("add_zero").success()
    }

    @Test
    fun subZero() {
        elaborate("sub_zero").success()
    }

    @Test
    fun zeroMul() {
        elaborate("zero_mul").success()
    }

    @Test
    fun mulZero() {
        elaborate("mul_zero").success()
    }

    @Test
    fun oneMul() {
        elaborate("one_mul").success()
    }

    @Test
    fun mulOne() {
        elaborate("mul_one").success()
    }

    @Test
    fun divOne() {
        elaborate("div_one").success()
    }

    @Test
    fun modOne() {
        elaborate("mod_one").success()
    }
}
