package mce.phase

import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Core as C
import mce.graph.Surface as S

class ElaborateTest {
    @Test
    fun testFalse() {
        val (elaborated, diagnostics) = Elaborate().run(
            S.Item.Definition(
                "a",
                emptyList(),
                S.Term.Boolean(),
                S.Term.BooleanOf(false)
            )
        )
        assert(diagnostics.isEmpty())
        assertEquals(
            C.Item.Definition(
                "a",
                emptyList(),
                C.Term.BooleanOf(false)
            ), elaborated
        )
    }
}
