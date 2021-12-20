package mce.phase

import mce.Diagnostic
import mce.graph.Dsl.boolean
import mce.graph.Dsl.definition
import mce.graph.Dsl.ff
import mce.graph.Dsl.function
import mce.graph.Dsl.function_of
import mce.graph.Dsl.invoke
import mce.graph.Dsl.let_in
import mce.graph.Dsl.name
import mce.graph.Dsl.type
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Core as C

class ElaborateTest {
    @Test
    fun testFalse() {
        val boolean = boolean()
        val ff = ff()
        val (elaborated, diagnostics, types) = Elaborate(
            emptyMap(),
            definition("a", boolean, ff)
        )

        assert(diagnostics.isEmpty())
        assertEquals(C.Value.Type, types[boolean.id])
        assertEquals(C.Value.Boolean, types[ff.id])
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
            definition(
                "a",
                boolean(),
                function_of(name("x"), "x")(ff())
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testDependentFunction() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                function(name("x"), "x" to type(), "y" to name("x")),
                function_of(name("y"), "x", "y")
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testLet() {
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "a",
                boolean(),
                let_in("x", ff(), name("x"))
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
                boolean(),
                ff()
            )
        )
        val (_, diagnostics, _) = Elaborate(
            mapOf(a.name to a),
            definition(
                "b",
                boolean(),
                name("a")
            )
        )

        assert(diagnostics.isEmpty())
    }

    @Test
    fun testInvalidDefinition() {
        val a = name("a")
        val (_, diagnostics, _) = Elaborate(
            emptyMap(),
            definition(
                "b",
                boolean(),
                a
            )
        )

        assert(diagnostics.contains(Diagnostic.NameNotFound("a", a.id)))
    }
}
