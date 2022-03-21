package mce.phase

import mce.ast.Id
import mce.ast.core.VTerm
import mce.fetch
import mce.phase.frontend.Diagnostic
import mce.phase.frontend.Zonk
import mce.server.Key
import java.util.*
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
    fun unsolvedMeta() {
        val result = zonk("unsolved_meta")
        assert(result.diagnostics.contains(Diagnostic.UnsolvedMeta(Id(UUID(0, 0)))))
    }
}
