package mce.phase

import mce.fetch
import mce.server.Key
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import mce.graph.Core as C

class StageTest {
    private fun stage(name: String): C.Item = fetch(Key.StagedItem(name))

    @Test
    fun reduce() {
        val def = stage("code_elim") as C.Item.Def
        assertEquals(C.Term.BoolOf(false), def.body)
    }
}
