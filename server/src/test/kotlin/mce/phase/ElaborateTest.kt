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
    private fun elaborate(name: String): Elaborate.Result = fetch(Key.ElaborateResult(name))

    private fun Elaborate.Result.success(): Elaborate.Result = apply {
        assert(diagnostics.isEmpty()) { diagnostics.joinToString("\n") }
    }

    @Test
    fun elaborate() {
        val result = elaborate("elaborate").success()
        assertIs<C.Value.Type>(result.types[UUID(0, 0)])
        assertIs<C.Value.Bool>(result.types[UUID(0, 1)])
        assertEquals(C.Item.Def(emptyList(), emptyList(), emptySet(), "elaborate", C.Value.Bool(UUID(0, 0)), C.Term.BoolOf(false, UUID(0, 1))), result.item)
    }

    @Test
    fun apply() {
        elaborate("apply").success()
    }

    @Test
    fun identity() {
        elaborate("identity").success()
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
        val result = elaborate("function_closed")
        assert(result.diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun let() {
        elaborate("let").success()
    }

    @Test
    fun letEscape() {
        val result = elaborate("let_escape")
        assert(result.diagnostics.contains(Diagnostic.NameNotFound("b", UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun nameNotFound() {
        val result = elaborate("name_not_found")
        assert(result.diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
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
        val result = elaborate("stage_mismatch")
        assert(result.diagnostics.contains(Diagnostic.StageMismatch(1, 0, UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun phaseMismatch() {
        val result = elaborate("phase_mismatch")
        assert(result.diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun phaseMismatchLocal() {
        val result = elaborate("phase_mismatch_local")
        assert(result.diagnostics.contains(Diagnostic.PhaseMismatch(UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
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
        val result = elaborate("match_escape")
        assert(result.diagnostics.contains(Diagnostic.NameNotFound("a", UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun nestedPatterns() {
        elaborate("nested_patterns").success()
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
        val result = elaborate("not_eq")
        assert(result.diagnostics.any { it is Diagnostic.TypeMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun meta() {
        val result = elaborate("meta").success()
        assertIs<C.Value.Bool>(result.normalizer.getSolution(0))
    }

    @Test
    fun sym() {
        elaborate("sym").success()
    }

    @Test
    fun symBad() {
        val result = elaborate("sym_bad")
        assert(result.diagnostics.any { it is Diagnostic.TypeMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun trans() {
        elaborate("trans").success()
    }

    @Test
    fun transBad() {
        val result = elaborate("trans_bad")
        assert(result.diagnostics.any { it is Diagnostic.TypeMismatch }) { result.diagnostics.joinToString("\n") }
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
        val result = elaborate("invalid_import")
        assert(result.diagnostics.any { it is Diagnostic.NameNotFound }) { result.diagnostics.joinToString("\n") }
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
        val result = elaborate("builtin_ill_typed")
        println(result.diagnostics)
        assert(result.diagnostics.any { it is Diagnostic.TypeMismatch }) { result.diagnostics.joinToString("\n") }
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

    @Test
    fun addComm() {
        elaborate("add_comm").success()
    }

    @Test
    fun mulComm() {
        elaborate("mul_comm").success()
    }

    @Test
    fun addConstComm() {
        elaborate("add_const_comm").success()
    }

    @Test
    fun subSelf() {
        elaborate("sub_self").success()
    }

    @Test
    fun divSelf() {
        elaborate("div_self").success()
    }

    @Test
    fun modSelf() {
        elaborate("mod_self").success()
    }

    @Test
    fun arityMismatch() {
        val result = elaborate("arity_mismatch")
        assert(result.diagnostics.contains(Diagnostic.ArityMismatch(2, 1, UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun impureDef() {
        val result = elaborate("impure_def")
        assert(result.diagnostics.contains(Diagnostic.EffectMismatch(emptyList(), listOf(S.Effect.Name("a")), UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun homogeneousList() {
        elaborate("homogeneous_list").success()
    }

    @Test
    fun heterogeneousList() {
        elaborate("heterogeneous_list").success()
    }

    @Test
    fun usePolymorphicVar() {
        val result = elaborate("use_polymorphic_var")
        assert(result.diagnostics.contains(Diagnostic.PolymorphicRepresentation(UUID(0, 0)))) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun anno() {
        elaborate("anno").success()
    }

    @Test
    fun boxIntro() {
        elaborate("box_intro").success()
    }

    @Test
    fun boxElim() {
        elaborate("box_elim").success()
    }

    @Test
    fun boxIllTypedTag() {
        val result = elaborate("box_ill_typed_tag")
        assert(result.diagnostics.any { it is Diagnostic.TypeMismatch }) { result.diagnostics.joinToString("\n") }
    }
}
