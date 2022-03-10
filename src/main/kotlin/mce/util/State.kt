@file:Suppress("NOTHING_TO_INLINE")

package mce.util

fun interface State<S, out A> {
    fun S.run(): Pair<S, A>
}

inline infix fun <S, A> State<S, A>.with(s: S): Pair<S, A> = s.run()

inline fun <S, A> pure(a: A): State<S, A> = State { this to a }

inline fun <S> get(): State<S, S> = State { this to this }

inline fun <S> put(s: S): State<S, Unit> = State { s to Unit }

inline fun <S, A> gets(crossinline transform: S.() -> A): State<S, A> = get<S>() % { pure(it.transform()) }

inline fun <S> modify(crossinline transform: S.() -> S): State<S, Unit> = get<S>() % { put(it.transform()) }

inline fun <S, A, B> State<S, A>.map(crossinline transform: (A) -> B): State<S, B> = State { run().let { (s, a) -> s to transform(a) } }

inline operator fun <S, A, B> State<S, A>.div(crossinline transform: (A) -> B): State<S, B> = map(transform)

inline fun <S, A, B> State<S, A>.flatMap(crossinline transform: (A) -> State<S, B>): State<S, B> = State { run().let { (s, a) -> transform(a) with s } }

inline operator fun <S, A, B> State<S, A>.rem(crossinline transform: (A) -> State<S, B>): State<S, B> = flatMap(transform)

inline fun <S> Iterable<State<S, Unit>>.forEach(): State<S, Unit> = State {
    var accumulator = this
    for (state in this@forEach) {
        val (s, _) = state with accumulator
        accumulator = s
    }
    accumulator to Unit
}

inline fun <S, A> Iterable<State<S, A>>.fold(): State<S, List<A>> = State {
    var accumulator = this
    val elements = mutableListOf<A>()
    for (state in this@fold) {
        val (s, element) = state with accumulator
        elements += element
        accumulator = s
    }
    accumulator to elements
}

inline fun <S, A> Iterable<State<S, A>>.all(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = State {
    var accumulator = this
    for (state in this@all) {
        val (s, success) = (state % predicate) with accumulator
        if (!success) {
            return@State accumulator to false
        }
        accumulator = s
    }
    accumulator to true
}
