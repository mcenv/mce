package mce.pass

import mce.Id
import mce.fetch
import mce.pass.frontend.Diagnostic
import mce.pass.frontend.decode.Eff
import mce.pass.frontend.elab.Elab
import mce.pass.frontend.elab.VTerm
import mce.server.build.Key
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class ElabTest {
    private fun elaborate(name: String): Elab.Result = fetch(Key.ElabResult(name))

    private fun Elab.Result.success(): Elab.Result = apply {
        assert(diagnostics.isEmpty()) { diagnostics.joinToString("\n") }
    }

    @TestFactory
    fun success(): List<DynamicTest> = listOf(
        "apply",
        "identity",
        "function_intro",
        "function_elim",
        "let",
        "compound",
        "code_intro",
        "code_elim",
        "match_boolean",
        "match_variable",
        "nested_patterns",
        "eq_intro",
        "eq_elim",
        "sym",
        "trans",
        "cong",
        "inj",
        "cast",
        "unfold_def",
        "subtype_trans",
        "builtin",
        "use_builtin",
        "builtin_open",
        "zero_add",
        "add_zero",
        "sub_zero",
        "zero_mul",
        "mul_zero",
        "one_mul",
        "mul_one",
        "div_one",
        "mod_one",
        "add_comm",
        "mul_comm",
        "add_const_comm",
        "sub_self",
        "div_self",
        "mod_self",
        "homogeneous_list",
        "heterogeneous_list",
        "or_join_var",
        "anno",
        "irrelevant",
        "wildcard_import",
        "typecase",
        "poly_discard",
        "function_eval",
        "match_eval",
        "var_sized_list",
        "list_size",
        "empty_module",
        "non_empty_module",
        "signature",
        "structure",
        "unit_intro",
        "unit_elim",
        "test_success",
        "identity_test",
        "irrelevant_compound_entries",
        "abstract_eq",
        "block_multi_let",
        "empty_block",
        "let_unit",
    ).map {
        dynamicTest(it) {
            val result = elaborate(it)
            assert(result.diagnostics.isEmpty()) { result.diagnostics.joinToString("\n") }
        }
    }

    @Test
    fun elaborate() {
        val result = elaborate("elaborate").success()
        assertIs<VTerm.Type>(result.types[Id(0, 0)])
        assertIs<VTerm.Bool>(result.types[Id(0, 1)])
    }

    @Test
    fun function_closed() {
        val result = elaborate("function_closed")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(0, 0)))
    }

    @Test
    fun let_escape() {
        val result = elaborate("let_escape")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("b", Id(0, 0)))
    }

    @Test
    fun var_not_found() {
        val result = elaborate("var_not_found")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(0, 0)))
    }

    @Test
    fun stage_mismatch() {
        val result = elaborate("stage_mismatch")
        assertContains(result.diagnostics, Diagnostic.StageMismatch(1, 0, Id(0, 0)))
    }

    @Test
    fun phase_mismatch() {
        val result = elaborate("phase_mismatch")
        assertContains(result.diagnostics, Diagnostic.PhaseMismatch(Id(0, 0)))
    }

    @Test
    fun phase_mismatch_local() {
        val result = elaborate("phase_mismatch_local")
        assertContains(result.diagnostics, Diagnostic.PhaseMismatch(Id(0, 0)))
    }

    @Test
    fun match_escape() {
        val result = elaborate("match_escape")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(0, 0)))
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
    fun sym_bad() {
        val result = elaborate("sym_bad")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun trans_bad() {
        val result = elaborate("trans_bad")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun invalid_import() {
        val result = elaborate("invalid_import")
        assert(result.diagnostics.any { it is Diagnostic.DefNotFound }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun builtin_ill_typed() {
        val result = elaborate("builtin_ill_typed")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun arity_mismatch() {
        val result = elaborate("arity_mismatch")
        assertContains(result.diagnostics, Diagnostic.SizeMismatch(2, 1, Id(0, 0)))
    }

    @Test
    fun impure_def() {
        val result = elaborate("impure_def")
        assertContains(result.diagnostics, Diagnostic.EffMismatch(emptyList(), listOf(Eff.Name("a")), Id(0, 0)))
    }

    @Test
    fun poly_var() {
        val result = elaborate("poly_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(0, 0)))
    }

    @Test
    fun any_var() {
        val result = elaborate("any_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(0, 0)))
    }

    @Test
    fun or_invalid_join_var() {
        val result = elaborate("or_invalid_join_var")
        assertContains(result.diagnostics, Diagnostic.PolyRepr(Id(0, 0)))
    }

    @Test
    fun relevance_mismatch() {
        val result = elaborate("relevance_mismatch")
        assertContains(result.diagnostics, Diagnostic.RelevanceMismatch(Id(0, 0)))
    }

    @Test
    fun eval_error() {
        val result = elaborate("eval_error")
        assertContains(result.diagnostics, Diagnostic.VarNotFound("a", Id(0, 0)))
    }

    @Test
    fun test_ill_typed() {
        val result = elaborate("test_ill_typed")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }

    @Test
    fun type_relevance_mismatch() {
        val result = elaborate("type_relevance_mismatch")
        assertContains(result.diagnostics, Diagnostic.RelevanceMismatch(Id(0, 0)))
    }

    @Test
    fun abstract_not_unfolded() {
        val result = elaborate("abstract_not_unfolded")
        assert(result.diagnostics.any { it is Diagnostic.TermMismatch }) { result.diagnostics.joinToString("\n") }
    }
}
