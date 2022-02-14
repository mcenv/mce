package mce.phase

import mce.fetch
import mce.server.Key
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import mce.graph.Core as C

class ZonkTest {
    private fun zonk(name: String): Zonk.Output = fetch(Key.ZonkedOutput(name))

    @Test
    fun meta() {
        val output = zonk("meta")
        assertEquals(C.Value.Bool, output.normalizer.getSolution(0))
    }

    @Test
    fun unsolvedMeta() {
        assertThrows<Error> { zonk("unsolved_meta") }
    }
}
