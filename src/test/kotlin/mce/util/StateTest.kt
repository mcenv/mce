package mce.util

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class StateTest {
    @Test
    fun simple() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            gets { this[level] }

        assertEquals(
            persistentListOf("a", "b", "c") to "a",
            eval(0) with persistentListOf("a", "b", "c"),
        )
    }
}
