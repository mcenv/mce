@file:Suppress("NOTHING_TO_INLINE")

package mce.util

fun interface State<S, out A> {
    fun S.run(): Pair<S, A>
}

inline infix fun <S, A> State<S, A>.with(s: S): Pair<S, A> = s.run()

inline infix fun <S> State<S, Unit>.with(s: S): S = s.run().first

inline fun <S, A> pure(a: A): State<S, A> = State { this to a }

inline fun <S> get(): State<S, S> = State { this to this }

inline fun <S> put(s: S): State<S, Unit> = State { s to Unit }

inline fun <S, A> gets(crossinline transform: S.() -> A): State<S, A> = get<S>() % { pure(it.transform()) }

inline fun <S> modify(crossinline transform: S.() -> S): State<S, Unit> = get<S>() % { put(it.transform()) }

inline fun <S, A, B> State<S, A>.map(crossinline transform: S.(A) -> B): State<S, B> = State { run().let { (s, a) -> s to s.transform(a) } }

inline operator fun <S, A, B> State<S, A>.div(crossinline transform: S.(A) -> B): State<S, B> = map(transform)

inline fun <S, A, B> State<S, A>.flatMap(crossinline transform: S.(A) -> State<S, B>): State<S, B> = State { run().let { (s, a) -> s.transform(a) with s } }

inline operator fun <S, A, B> State<S, A>.rem(crossinline transform: S.(A) -> State<S, B>): State<S, B> = flatMap(transform)

inline fun <S, T, A> State<S, A>.lift(s: S, crossinline transform: (S) -> T): State<T, A> = State { (this@lift with s).let { (s, a) -> transform(s) to a } }

inline fun <S, A> scope(crossinline block: S.() -> State<S, A>): State<S, A> = State { with(block()) { run().let { (_, a) -> this@State to a } } }

inline fun <S, A> Iterable<A>.forEachM(crossinline transform: S.(A) -> State<S, Unit>): State<S, Unit> = State {
    var accumulator = this
    for (a in this@forEachM) {
        val s = accumulator.transform(a) with accumulator
        accumulator = s
    }
    accumulator to Unit
}

inline fun <S, A, B> Iterable<A>.mapM(crossinline action: S.(A) -> State<S, B>): State<S, List<B>> = State {
    var accumulator = this
    val bs = mutableListOf<B>()
    for (a in this@mapM) {
        val (s, b) = accumulator.action(a) with accumulator
        bs += b
        accumulator = s
    }
    accumulator to bs
}

inline fun <S, A> Iterable<A>.allM(crossinline predicate: S.(A) -> State<S, Boolean>): State<S, Boolean> = State {
    var accumulator = this
    for (a in this@allM) {
        val (s, success) = accumulator.predicate(a) with accumulator
        if (!success) {
            return@State accumulator to false
        }
        accumulator = s
    }
    accumulator to true
}

inline fun <S, A> Iterable<A>.anyM(crossinline predicate: S.(A) -> State<S, Boolean>): State<S, Boolean> = State {
    var accumulator = this
    for (a in this@anyM) {
        val (s, success) = accumulator.predicate(a) with accumulator
        if (success) {
            return@State accumulator to true
        }
        accumulator = s
    }
    accumulator to false
}

inline infix fun <S> State<S, Boolean>.andM(other: State<S, Boolean>): State<S, Boolean> = State {
    val (s, success) = this@andM with this
    if (success) {
        other with s
    } else {
        s to false
    }
}
