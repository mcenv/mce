package mce.phase

import mce.read
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import mce.graph.Core as C

class StageTest {
    private fun stage(name: String): C.Item {
        val surface = Parse(name, read("/$name.mce"))
        val elaborated = Elaborate(emptyMap(), surface)
        return Stage(elaborated.metaState, emptyMap(), elaborated.item)
    }

    @Test
    fun reduce() {
        val definition = stage("code_elim") as C.Item.Definition
        assertEquals(C.Term.BooleanOf(false), definition.body)
    }
}
