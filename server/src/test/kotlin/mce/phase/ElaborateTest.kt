package mce.phase

import mce.Diagnostic
import mce.graph.Dsl.any
import mce.graph.Dsl.boolean
import mce.graph.Dsl.code
import mce.graph.Dsl.code_of
import mce.graph.Dsl.compound
import mce.graph.Dsl.compound_of
import mce.graph.Dsl.definition
import mce.graph.Dsl.end
import mce.graph.Dsl.ff
import mce.graph.Dsl.function
import mce.graph.Dsl.function_of
import mce.graph.Dsl.invoke
import mce.graph.Dsl.let_in
import mce.graph.Dsl.not
import mce.graph.Dsl.parameter
import mce.graph.Dsl.type
import mce.graph.Dsl.variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    @Test
    fun testFalse() {
        val boolean = boolean()
        val ff = ff()
        val (elaborated, diagnostics, types) = Elaborate(
            emptyMap(),
            definition("a", false, boolean, ff)
        )

        assert(diagnostics.isEmpty())
        assertIs<S.Term.Type>(types[boolean.id]!!.value)
        assertIs<S.Term.Boolean>(types[ff.id]!!.value)
        assertEquals(C.Item.Definition("a", emptyList(), ff, C.Value.Boolean), elaborated)
    }

    @Test
    fun testApply() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                function_of(variable("x", 0), "x")(ff())
            )
        )

        assert(diagnostics.isEmpty())
    }
    @Test
    fun testDependentFunctionIntroduction() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                function(variable("x", 0), parameter("x", end(), any(), type()), parameter("y", end(), any(), variable("x", 0))),
                function_of(variable("y", 1), "x", "y")
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testDependentFunctionElimination() {
        val (identity, diagnostics1, _) = Elaborate(
            emptyMap(),
            definition(
                "identity",
                false,
                function(variable("α", 0), parameter("α", end(), any(), type()), parameter("a", end(), any(), variable("α", 0))),
                function_of(variable("a", 1), "α", "a")
            )
        )
        val (_, diagnostics2, _) = Elaborate(
            mapOf(identity.name to identity),
            definition(
                "f",
                false,
                boolean(),
                definition("identity")(boolean(), ff())
            )
        )

        assert(diagnostics1.isEmpty())
        assert(diagnostics2.isEmpty())
    }

    @Test
    fun testLet() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                let_in("x", ff(), variable("x", 0))
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testValidDefinition() {
        val (a, _, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                ff()
            )
        )
        val (_, diagnostics, _) = Elaborate(
            mapOf(a.name to a),
            definition(
                "b",
                false,
                boolean(),
                definition("a")
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testInvalidDefinition() {
        val a = definition("a")
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "b",
                false,
                boolean(),
                a
            )
        )

        assert(diagnostics.contains(Diagnostic.DefinitionNotFound("a", a.id)))
    }

    @Test
    fun testDependentCompound() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                compound("α" to type(), "a" to variable("α", 0)),
                compound_of(boolean(), ff())
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testCodeIntroduction() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                code(boolean()),
                code_of(ff())
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testCodeElimination() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                !code_of(ff())
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testStageMismatch() {
        val b = variable("b", 0)
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                code(boolean()),
                let_in("b", ff(), code_of(b))
            )
        )

        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, b.id)))
    }
}
