package mce.pass

import mce.Id
import mce.fetch
import mce.pass.frontend.Diagnostic
import mce.pass.frontend.elab.VTerm
import mce.pass.frontend.zonk.Zonk
import mce.server.build.Key
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
