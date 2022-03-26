package mce.phase

import mce.fetch
import mce.phase.frontend.Diagnostic
import mce.phase.frontend.elab.Elab
import mce.phase.frontend.elab.VTerm
import mce.phase.frontend.parse.Eff
import mce.server.Key
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class ElabTest {
    private fun elaborate(name: String): Elab.Result = fetch(Key.ElabResult(name))

    private fun Elab.Result.success(): Elab.Result = apply {
        assert(diagnostics.isEmpty()) { diagnostics.joinToString("\n") }
    }

    @Test
    fun elaborate() {
        val result = elaborate("elaborate").success()
        assertIs<VTerm.Type>(result.types[Id(UUID(0, 0))])
        assertIs<VTerm.Bool>(result.types[Id(UUID(0, 1))])
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
    fun function_intro() {
        elaborate("function_intro").success()
    }

    @Test
    fun function_elim() {
        elaborate("function_elim").success()
    }

    @Test
    fun function_closed() {
        val result = elaborate("function_closed")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(UUID(0, 0))))
    }

    @Test
    fun let() {
        elaborate("let").success()
    }

    @Test
    fun let_escape() {
        val result = elaborate("let_escape")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("b", Id(UUID(0, 0))))
    }

    @Test
    fun var_not_found() {
        val result = elaborate("var_not_found")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(UUID(0, 0))))
    }

    @Test
    fun compound() {
        elaborate("compound").success()
    }

    @Test
    fun code_intro() {
        elaborate("code_intro").success()
    }

    @Test
    fun code_elim() {
        elaborate("code_elim").success()
    }

    @Test
    fun stage_mismatch() {
        val result = elaborate("stage_mismatch")
        assertContains(result.diagnostics, Diagnostic.StageMismatch(1, 0, Id(UUID(0, 0))))
    }

    @Test
    fun phase_mismatch() {
        val result = elaborate("phase_mismatch")
        assertContains(result.diagnostics, Diagnostic.PhaseMismatch(Id(UUID(0, 0))))
    }

    @Test
    fun phase_mismatch_local() {
        val result = elaborate("phase_mismatch_local")
        assertContains(result.diagnostics, Diagnostic.PhaseMismatch(Id(UUID(0, 0))))
    }

    @Test
    fun match_boolean() {
        elaborate("match_boolean").success()
    }

    @Test
    fun match_variable() {
        elaborate("match_variable").success()
    }

    @Test
    fun match_escape() {
        val result = elaborate("match_escape")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(UUID(0, 0))))
    }

    @Test
    fun nested_patterns() {
        elaborate("nested_patterns").success()
    }

    @Test
    fun eq_intro() {
        elaborate("eq_intro").success()
    }

    @Test
    fun eq_elim() {
        elaborate("eq_elim").success()
    }

    @Test
    fun not_eq() {
        val result = elaborate("not_eq")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun meta() {
        val result = elaborate("meta").success()
        assertIs<VTerm.Bool>(result.normalizer.getSolution(0))
    }

    @Test
    fun sym() {
        elaborate("sym").success()
    }

    @Test
    fun sym_bad() {
        val result = elaborate("sym_bad")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun trans() {
        elaborate("trans").success()
    }

    @Test
    fun trans_bad() {
        val result = elaborate("trans_bad")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
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
    fun invalid_import() {
        val result = elaborate("invalid_import")
        assert(result.diagnostics.any { it is Diagnostic.DefNotFound }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun cast() {
        elaborate("cast").success()
    }

    @Test
    fun unfold_def() {
        elaborate("unfold_def").success()
    }

    @Test
    fun subtype_trans() {
        elaborate("subtype_trans").success()
    }

    @Test
    fun builtin() {
        elaborate("builtin").success()
    }

    @Test
    fun builtin_ill_typed() {
        val result = elaborate("builtin_ill_typed")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun use_builtin() {
        elaborate("use_builtin").success()
    }

    @Test
    fun builtin_open() {
        elaborate("builtin_open").success()
    }

    @Test
    fun zero_add() {
        elaborate("zero_add").success()
    }

    @Test
    fun add_zero() {
        elaborate("add_zero").success()
    }

    @Test
    fun sub_zero() {
        elaborate("sub_zero").success()
    }

    @Test
    fun zero_mul() {
        elaborate("zero_mul").success()
    }

    @Test
    fun mul_zero() {
        elaborate("mul_zero").success()
    }

    @Test
    fun one_mul() {
        elaborate("one_mul").success()
    }

    @Test
    fun mul_one() {
        elaborate("mul_one").success()
    }

    @Test
    fun div_one() {
        elaborate("div_one").success()
    }

    @Test
    fun mod_one() {
        elaborate("mod_one").success()
    }

    @Test
    fun add_comm() {
        elaborate("add_comm").success()
    }

    @Test
    fun mul_comm() {
        elaborate("mul_comm").success()
    }

    @Test
    fun add_const_comm() {
        elaborate("add_const_comm").success()
    }

    @Test
    fun sub_self() {
        elaborate("sub_self").success()
    }

    @Test
    fun div_self() {
        elaborate("div_self").success()
    }

    @Test
    fun mod_self() {
        elaborate("mod_self").success()
    }

    @Test
    fun arity_mismatch() {
        val result = elaborate("arity_mismatch")
        assertContains(result.diagnostics, Diagnostic.SizeMismatch(2, 1, Id(UUID(0, 0))))
    }

    @Test
    fun impure_def() {
        val result = elaborate("impure_def")
        assertContains(result.diagnostics, Diagnostic.EffMismatch(emptyList(), listOf(Eff.Name("a")), Id(UUID(0, 0))))
    }

    @Test
    fun homogeneous_list() {
        elaborate("homogeneous_list").success()
    }

    @Test
    fun heterogeneous_list() {
        elaborate("heterogeneous_list").success()
    }

    @Test
    fun poly_var() {
        val result = elaborate("poly_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(UUID(0, 0))))
    }

    @Test
    fun any_var() {
        val result = elaborate("any_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(UUID(0, 0))))
    }

    @Test
    fun or_invalid_join_var() {
        val result = elaborate("or_invalid_join_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(UUID(0, 0))))
    }

    @Test
    fun or_join_var() {
        elaborate("or_join_var").success()
    }

    @Test
    fun anno() {
        elaborate("anno").success()
    }

    @Test
    fun box_intro() {
        elaborate("box_intro").success()
    }

    @Test
    fun box_elim() {
        elaborate("box_elim").success()
    }

    @Test
    fun box_ill_typed_content() {
        val result = elaborate("box_ill_typed_content")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun box_ill_typed_tag() {
        val result = elaborate("box_ill_typed_tag")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun irrelevant() {
        elaborate("irrelevant").success()
    }

    @Test
    fun relevance_mismatch() {
        val result = elaborate("relevance_mismatch")
        assertContains(result.diagnostics, Diagnostic.RelevanceMismatch(Id(UUID(0, 0))))
    }

    @Test
    fun wildcard_import() {
        elaborate("wildcard_import").success()
    }

    @Test
    fun typecase() {
        elaborate("typecase").success()
    }

    @Test
    fun poly_discard() {
        elaborate("poly_discard").success()
    }

    @Test
    fun function_eval() {
        elaborate("function_eval").success()
    }

    @Test
    fun match_eval() {
        elaborate("match_eval").success()
    }

    @Test
    fun var_sized_list() {
        elaborate("var_sized_list").success()
    }

    @Test
    fun list_size() {
        elaborate("list_size").success()
    }

    @Test
    fun box_inst() {
        elaborate("box_inst").success()
    }

    @Test
    fun eval_error() {
        val result = elaborate("eval_error")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(UUID(0, 0))))
    }

    @Test
    fun empty_module() {
        elaborate("empty_module").success()
    }

    @Test
    fun non_empty_module() {
        elaborate("non_empty_module").success()
    }

    @Test
    fun signature() {
        elaborate("signature").success()
    }

    @Test
    fun structure() {
        elaborate("structure").success()
    }

    @Test
    fun unit_intro() {
        elaborate("unit_intro").success()
    }

    @Test
    fun unit_elim() {
        elaborate("unit_elim").success()
    }

    @Test
    fun test_success() {
        elaborate("test_success").success()
    }

    @Test
    fun test_ill_typed() {
        val result = elaborate("test_ill_typed")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun identity_test() {
        elaborate("identity_test").success()
    }

    @Test
    fun irrelevant_compound_entries() {
        elaborate("irrelevant_compound_entries").success()
    }

    @Test
    fun type_relevance_mismatch() {
        val result = elaborate("type_relevance_mismatch")
        assertContains(result.diagnostics, Diagnostic.RelevanceMismatch(Id(UUID(0, 0))))
    }
}
