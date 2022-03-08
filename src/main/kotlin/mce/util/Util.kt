package mce.util

@Suppress("NAME_SHADOWING")
inline fun <T, U, R> Iterable<T>.foldMap(initial: R, operation: (R, T) -> Pair<R, U>): Pair<R, List<U>> {
    val elements = mutableListOf<U>()
    return fold(initial) { accumulator, element ->
        val (accumulator, element) = operation(accumulator, element)
        elements += element
        accumulator
    } to elements
}

inline fun <T, R> Iterable<T>.foldAll(initial: R, operation: (R, T) -> Pair<R, Boolean>): Pair<R, Boolean> {
    if (this is Collection && isEmpty()) return initial to true
    var accumulator = initial
    for (element in this) {
        val (acc, success) = operation(accumulator, element)
        if (!success) return acc to false
        accumulator = acc
    }
    return accumulator to true
}

inline fun <T, R> Iterable<T>.firstMapOrNull(predicate: (T) -> Pair<R, Boolean>): R? {
    firstOrNull {
        val (element, success) = predicate(it)
        if (success) return element
        false
    }
    return null
}

fun <K, V> Iterable<Pair<K, V>>.toLinkedHashMap(): LinkedHashMap<K, V> = LinkedHashMap<K, V>().also { it.putAll(this) }

inline fun <A, B, R> Pair<A, B>.mapFirst(transform: (A) -> R): Pair<R, B> = transform(first) to second

inline fun <A, B, R> Pair<A, B>.mapSecond(transform: (B) -> R): Pair<A, R> = first to transform(second)
