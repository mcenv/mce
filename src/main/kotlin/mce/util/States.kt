@file:Suppress("NOTHING_TO_INLINE")

package mce.util

typealias State<S, A> = StateScope<S>.() -> A

inline fun <S, A> State<S, A>.run(s: S): A = StateScope(s).this()

class StateScope<S>(var value: S) {
    inline operator fun <A> State<S, A>.not(): A = this()

    inline fun <A> pure(a: A): State<S, A> = { a }

    inline fun get(): State<S, S> = { value }

    inline fun put(s: S): State<S, Unit> = { value = s }

    inline fun <A> gets(crossinline block: S.() -> A): State<S, A> = { block(!get()) }

    inline fun modify(crossinline transform: S.() -> S): State<S, Unit> = { !put(transform(!get())) }

    inline fun <T, A> lift(crossinline transform: S.() -> T, crossinline state: State<T, A>): State<S, A> = { state.run((!get()).transform()) }

    inline fun <A> restore(crossinline state: State<S, A>): State<S, A> = {
        val s = !get()
        val a = !state
        !put(s)
        a
    }

    inline fun <A, B> Iterable<A>.mapM(crossinline action: (A) -> State<S, B>): State<S, List<B>> = {
        this@mapM.map { !action(it) }
    }

    inline fun <A> Iterable<A>.forEachM(crossinline transform: (A) -> State<S, Unit>): State<S, Unit> = {
        this@forEachM.forEach { !transform(it) }
    }

    inline fun <A> Iterable<A>.allM(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = {
        this@allM.all { !predicate(it) }
    }

    inline fun <A> Iterable<A>.anyM(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = {
        this@anyM.any { !predicate(it) }
    }
}
