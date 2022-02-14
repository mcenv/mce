package mce.phase

import mce.Diagnostic
import mce.fetch
import mce.server.Key
import java.util.*
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
        val output = zonk("unsolved_meta")
        assert(output.diagnostics.contains(Diagnostic.UnsolvedMeta(UUID(0, 0))))
    }
}
