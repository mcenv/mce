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

    @Test
    fun fold() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            modify<PersistentList<String>> { add(size.toString()) } % {
                gets { this[level] }
            }

        assertEquals(
            persistentListOf("0", "1", "2") to listOf("0", "0", "2"),
            listOf(eval(0), eval(0), eval(2)).fold() with persistentListOf(),
        )
    }

    @Test
    fun all() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            modify<PersistentList<String>> { add(size.toString()) } % {
                gets { this[level] }
            }

        assertEquals(
            persistentListOf("0") to false,
            listOf(eval(0), eval(1), eval(2)).all { gets { it == "0" } } with persistentListOf()
        )
    }
}
