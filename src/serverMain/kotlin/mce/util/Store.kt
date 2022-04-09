package mce.util

class Store<T>(var value: T) {
    inline fun restore(block: Store<T>.() -> Unit) {
        val value = this.value
        block()
        this.value = value
    }
}
