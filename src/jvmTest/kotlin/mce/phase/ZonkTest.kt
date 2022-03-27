package mce.phase

import mce.fetch
import mce.phase.frontend.Diagnostic
import mce.phase.frontend.elab.VTerm
import mce.phase.frontend.zonk.Zonk
import mce.server.Key
import kotlin.test.Test
import kotlin.test.assertIs

class ZonkTest {
    private fun zonk(name: String): Zonk.Result = fetch(Key.ZonkResult(name))

    @Test
    fun meta() {
        val result = zonk("meta")
        assertIs<VTerm.Bool>(result.normalizer.getSolution(0))
    }

    @Test
    fun unsolved_meta() {
        val result = zonk("unsolved_meta")
        assert(result.diagnostics.contains(Diagnostic.UnsolvedMeta(Id(0, 0))))
    }
}
