package mce.phase

import mce.graph.Core as C

class MetaState(
    private val values: MutableList<C.Value?> = mutableListOf()
) {
    /**
     * Returns the meta-variable at the [index].
     */
    operator fun get(index: Int): C.Value? = values[index]

    /**
     * Solves for the meta-variable at the [index] as the [value].
     */
    fun solve(index: Int, value: C.Value) {
        values[index] = value
    }

    /**
     * Creates a fresh meta-variable.
     */
    fun fresh(): C.Value = C.Value.Meta(values.size).also { values += null }

    /**
     * Unfolds meta-variables of the [value] recursively.
     */
    tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (this[value.index]) {
            null -> value
            else -> force(value)
        }
        else -> value
    }
}
