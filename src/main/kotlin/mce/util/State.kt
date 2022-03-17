@file:Suppress("NOTHING_TO_INLINE")

package mce.util

class StateScope<S>(var value: S)

typealias State<S, A> = StateScope<S>.() -> A

inline fun <S, A> state(noinline block: State<S, A>): State<S, A> = block

inline fun <S, A> State<S, A>.run(s: S): A = StateScope(s).this()

inline fun <S, A> pure(a: A): State<S, A> = { a }

inline fun <S> get(): State<S, S> = { value }

inline fun <S> put(s: S): State<S, Unit> = { value = s }

inline fun <S, A> gets(crossinline f: S.() -> A): State<S, A> = { f(`!`(get())) }

inline fun <S> modify(crossinline f: S.() -> S): State<S, Unit> = { `!`(put(f(`!`(get())))) }

inline fun <S, A> restore(crossinline state: State<S, A>): State<S, A> = {
    val s = `!`(get())
    val a = `!`(state)
    `!`(put(s))
    a
}

// Avoid using extension functions with receiver [State<S, A>] for better type inference.
inline fun <S, A> StateScope<S>.`!`(state: State<S, A>): A = this.state()

inline fun <S, A> Iterable<A>.forEachM(crossinline transform: (A) -> State<S, Unit>): State<S, Unit> = {
    this@forEachM.forEach { `!`(transform(it)) }
}

inline fun <S, A, B> Iterable<A>.mapM(crossinline action: (A) -> State<S, B>): State<S, List<B>> = {
    this@mapM.map { `!`(action(it)) }
}

inline fun <S, A> Iterable<A>.allM(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = {
    this@allM.all { `!`(predicate(it)) }
}

inline fun <S, A> Iterable<A>.anyM(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = {
    this@anyM.any { `!`(predicate(it)) }
}
