package mce.phase

import mce.ast.core.Item
import mce.ast.core.Term
import mce.fetch
import mce.phase.backend.Stage
import mce.server.Key
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class StageTest {
    private fun stage(name: String): Stage.Result = fetch(Key.StageResult(name))

    @Test
    fun reduce() {
        val result = stage("code_elim")
        val def = result.item as Item.Def
        assertEquals(Term.BoolOf(false, UUID(0, 0)), def.body)
    }
}
