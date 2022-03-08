package mce.phase

import mce.fetch
import mce.phase.backend.Stage
import mce.server.Key
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import mce.graph.Core as C

class StageTest {
    private fun stage(name: String): Stage.Result = fetch(Key.StageResult(name))

    @Test
    fun reduce() {
        val result = stage("code_elim")
        val def = result.item as C.Item.Def
        assertEquals(C.Term.BoolOf(false, UUID(0, 0)), def.body)
    }
}
