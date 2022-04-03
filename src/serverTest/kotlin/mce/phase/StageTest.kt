package mce.phase

import mce.Id
import mce.fetch
import mce.phase.backend.stage.Stage
import mce.phase.frontend.elab.Item
import mce.phase.frontend.elab.Term
import mce.server.build.Key
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StageTest {
    private fun stage(name: String): Stage.Result = fetch(Key.StageResult(name))

    @Test
    fun reduce() {
        val result = stage("code_elim")
        val def = result.item as Item.Def
        assertEquals(Term.BoolOf(false, Id(0, 0)), def.body)
    }
}
