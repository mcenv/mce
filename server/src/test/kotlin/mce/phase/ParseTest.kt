package mce.phase

import mce.graph.Surface.Term
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseTest {
    @Test
    fun hole() {
        assertEquals(
            Term.Hole(UUID(0, 0)),
            Parse("_ hole 00000000-0000-0000-0000-000000000000 00000000-0000-0000-0000-000000000001").parseTerm()
        )
    }
}
