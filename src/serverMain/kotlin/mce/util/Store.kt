package mce.util

class Store<S>(var value: S) {
    inline fun <R> restore(block: Store<S>.() -> R): R {
        val value = this.value
        val result = block()
        this.value = value
        return result
    }
}
