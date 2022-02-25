package mce.phase

import mce.Diagnostic
import mce.fetch
import mce.server.Key
import java.util.*
import kotlin.test.Test
import kotlin.test.assertIs
import mce.graph.Core as C

class ZonkTest {
    private fun zonk(name: String): Zonk.Result = fetch(Key.ZonkResult(name))

    @Test
    fun meta() {
        val result = zonk("meta")
        assertIs<C.Value.Bool>(result.normalizer.getSolution(0))
    }

    @Test
    fun unsolvedMeta() {
        val result = zonk("unsolved_meta")
        assert(result.diagnostics.contains(Diagnostic.UnsolvedMeta(UUID(0, 0))))
    }
}