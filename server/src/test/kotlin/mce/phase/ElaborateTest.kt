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
import mce.graph.Dsl.match
import mce.graph.Dsl.not
import mce.graph.Dsl.p_ff
import mce.graph.Dsl.p_tt
import mce.graph.Dsl.p_variable
import mce.graph.Dsl.parameter
import mce.graph.Dsl.tt
import mce.graph.Dsl.type
import mce.graph.Dsl.variable
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
    fun testFunctionClosed() {
        val a = variable("a", 0)
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                function(function(boolean(), parameter("b", end(), any(), boolean())), parameter("a", end(), any(), boolean())),
                function_of(function_of(a, "b"), "a")
            )
        )

        assert(diagnostics.contains(Diagnostic.VariableNotFound("a", a.id)))
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
                true,
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
                true,
                code(boolean()),
                let_in("b", ff(), code_of(b))
            )
        )

        assert(diagnostics.contains(Diagnostic.StageMismatch(1, 0, b.id)))
    }

    @Test
    fun testPhaseMismatch() {
        val d = definition(
            "a",
            false,
            code(boolean()),
            code_of(ff())
        )
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            d
        )

        assert(diagnostics.contains(Diagnostic.PhaseMismatch(d.id)))
    }

    @Test
    fun testPhaseMismatchLocal() {
        val l = let_in("b", code_of(ff()), ff())
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                l
            )
        )

        assert(diagnostics.contains(Diagnostic.PhaseMismatch(l.id)))
    }

    @Test
    fun testMatchBoolean() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "not",
                false,
                boolean(),
                ff().match(
                    p_ff() to tt(),
                    p_tt() to ff()
                )
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testMatchVariable() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                false,
                boolean(),
                ff().match(
                    p_variable("b") to variable("b", 0)
                )
            )
        )

        assert(diagnostics.isEmpty())
    }
}
