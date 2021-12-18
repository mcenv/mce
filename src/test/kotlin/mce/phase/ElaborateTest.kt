package mce.phase

import mce.Diagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    @Test
    fun testFalse() {
        val boolean = S.Term.Boolean()
        val `false` = S.Term.BooleanOf(false)
        val (elaborated, diagnostics, types) = Elaborate(
            emptyMap(),
            S.Item.Definition("a", emptyList(), boolean, `false`)
        )

        assert(diagnostics.isEmpty())
        assertEquals(C.Value.Type, types[boolean.id])
        assertEquals(C.Value.Boolean, types[`false`.id])
        assertEquals(
            C.Item.Definition(
                "a",
                emptyList(),
                C.Term.BooleanOf(false),
                C.Value.Boolean
            ),
            elaborated
        )
    }

    @Test
    fun testApply() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            S.Item.Definition(
                "a",
                emptyList(),
                S.Term.Boolean(),
                S.Term.Apply(S.Term.FunctionOf(listOf("x"), S.Term.Name("x")), listOf(S.Term.BooleanOf(false)))
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testDependentFunction() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            S.Item.Definition(
                "a",
                emptyList(),
                S.Term.Function(listOf("x" to S.Term.Type(), "y" to S.Term.Name("x")), S.Term.Name("x")),
                S.Term.FunctionOf(listOf("x", "y"), S.Term.Name("y"))
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testLet() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            S.Item.Definition(
                "a",
                emptyList(),
                S.Term.Boolean(),
                S.Term.Let("x", S.Term.BooleanOf(false), S.Term.Name("x"))
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testValidDefinition() {
        val (a, _, _) = Elaborate(
            emptyMap(),
            S.Item.Definition(
                "a",
                emptyList(),
                S.Term.Boolean(),
                S.Term.BooleanOf(false)
            )
        )
        val (_, diagnostics, _) = Elaborate(
            mapOf(a.name to a),
            S.Item.Definition(
                "b",
                emptyList(),
                S.Term.Boolean(),
                S.Term.Name("a")
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testInvalidDefinition() {
        val a = S.Term.Name("a")
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            S.Item.Definition(
                "b",
                emptyList(),
                S.Term.Boolean(),
                a
            )
        )

        assert(diagnostics.contains(Diagnostic.NameNotFound("a", a.id)))
    }
}
