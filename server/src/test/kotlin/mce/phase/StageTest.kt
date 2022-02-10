package mce.phase

import mce.read
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import mce.graph.Staged as S

class StageTest {
    private fun stage(name: String): S.Item {
        val surface = Parse(name, read("/$name.mce"))
        val elaborated = Elaborate(emptyMap(), surface)
        return Stage(elaborated.metaState, emptyMap(), elaborated.item)
    }

    @Test
    fun reduce() {
        val definition = stage("code_elim") as S.Item.Definition
        assertEquals(S.Term.BooleanOf(false), definition.body)
    }
}
