package mce.phase

import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    @Test
    fun testFalse() {
        val boolean = S.Term.Boolean()
        val `false` = S.Term.BooleanOf(false)
        val (elaborated, diagnostics, types) = Elaborate().run(
            S.Item.Definition("a", emptyList(), boolean, `false`)
        )

        assert(diagnostics.isEmpty())
        assertEquals(C.Value.Type, types[boolean.id])
        assertEquals(C.Value.Boolean, types[`false`.id])
        assertEquals(
            C.Item.Definition(
                "a",
                emptyList(),
                C.Term.BooleanOf(false)
            ), elaborated
        )
    }
}
