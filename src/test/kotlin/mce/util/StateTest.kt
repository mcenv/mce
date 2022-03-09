package mce.util

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import mce.util.State.Companion.all
import mce.util.State.Companion.fold
import mce.util.State.Companion.invoke
import mce.util.State.Companion.rem
import kotlin.test.Test
import kotlin.test.assertEquals

class StateTest {
    @Test
    fun simple() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            State.gets { this[level] }

        assertEquals(
            persistentListOf("a", "b", "c") to "a",
            eval(0)(persistentListOf("a", "b", "c")),
        )
    }

    @Test
    fun fold() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            State.modify<PersistentList<String>> { add(size.toString()) } % {
                State.gets { this[level] }
            }

        assertEquals(
            persistentListOf("0", "1", "2") to listOf("0", "0", "2"),
            listOf(eval(0), eval(0), eval(2)).fold()(persistentListOf()),
        )
    }

    @Test
    fun all() {
        fun eval(level: Int): State<PersistentList<String>, String> =
            State.modify<PersistentList<String>> { add(size.toString()) } % {
                State.gets { this[level] }
            }

        assertEquals(
            persistentListOf("0") to false,
            listOf(eval(0), eval(1), eval(2)).all { State.gets { it == "0" } }(persistentListOf())
        )
    }
}
