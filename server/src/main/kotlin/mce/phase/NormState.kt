package mce.phase

import mce.graph.Core as C

class NormState(
    private val values: MutableList<Lazy<C.Value>>,
    private val items: Map<String, C.Item>,
    private val solutions: MutableList<C.Value?>
) {
    val size: Int get() = values.size

    fun getItem(name: String): C.Item = items[name]!!

    fun lookup(level: Int): C.Value = values[level].value

    fun substitute(level: Int, value: Lazy<C.Value>) {
        values[level] = value
    }

    fun bind(value: Lazy<C.Value>) {
        values += value
    }

    fun pop() {
        values.removeLast()
    }

    inline fun <R> scope(block: NormState.() -> R): R {
        val size = this.size
        return block().also {
            repeat(this.size - size) { pop() }
        }
    }

    /**
     * Returns the solution at the [index].
     */
    fun getSolution(index: Int): C.Value? = solutions[index]

    /**
     * Solves for the meta-variable at the [index] as the [solution].
     */
    fun solve(index: Int, solution: C.Value) {
        solutions[index] = solution
    }

    /**
     * Creates a fresh meta-variable.
     */
    fun fresh(): C.Value = C.Value.Meta(solutions.size).also { solutions += null }

    /**
     * Unfolds meta-variables of the [value] recursively.
     */
    tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (val forced = getSolution(value.index)) {
            null -> value
            else -> force(forced)
        }
        else -> value
    }
}
