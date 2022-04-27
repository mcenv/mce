package mce.util

class Store<S>(var value: S) {
    inline fun <T> map(transform: (S) -> T): Store<T> = Store(transform(value))

    inline fun modify(block: (S) -> S) {
        value = block(value)
    }

    inline fun <R> restore(block: Store<S>.() -> R): R {
        val value = this.value
        val result = block()
        this.value = value
        return result
    }
}
