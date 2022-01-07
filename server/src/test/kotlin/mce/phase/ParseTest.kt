package mce.phase

import mce.graph.Surface.Item
import mce.graph.Surface.Term
import mce.read
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseTest {
    @Test
    fun testFalse() {
        assertEquals(
            Item.Definition(
                UUID(0, 1),
                "false",
                emptyList(),
                Term.Boolean(UUID(0, 3)),
                Term.BooleanOf(false, UUID(0, 4))
            ),
            Parse(read("/false.mce"))
        )
    }
}
