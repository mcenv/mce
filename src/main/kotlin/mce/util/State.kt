package mce.util

fun interface State<S, out A> {
    fun S.run(): Pair<S, A>

    companion object {
        operator fun <S, A> State<S, A>.invoke(s: S): Pair<S, A> = s.run()

        fun <S, A> pure(a: A): State<S, A> = State { this to a }

        fun <S> get(): State<S, S> = State { this to this }

        fun <S> put(s: S): State<S, Unit> = State { s to Unit }

        inline fun <S, A> gets(crossinline transform: S.() -> A): State<S, A> = get<S>() % { pure(it.transform()) }

        inline fun <S> modify(crossinline transform: S.() -> S): State<S, Unit> = get<S>() % { put(it.transform()) }

        inline fun <S, A, B> State<S, A>.map(crossinline transform: (A) -> B): State<S, B> = State { run().let { (s, a) -> s to transform(a) } }

        inline operator fun <S, A, B> State<S, A>.div(crossinline transform: (A) -> B): State<S, B> = map(transform)

        inline fun <S, A, B> State<S, A>.flatMap(crossinline transform: (A) -> State<S, B>): State<S, B> = State { run().let { (s, a) -> transform(a)(s) } }

        inline operator fun <S, A, B> State<S, A>.rem(crossinline transform: (A) -> State<S, B>): State<S, B> = flatMap(transform)

        fun <S, A> Iterable<State<S, A>>.fold(): State<S, List<A>> = State {
            var accumulator = this
            val elements = mutableListOf<A>()
            for (state in this@fold) {
                val (s, element) = state(accumulator)
                elements += element
                accumulator = s
            }
            accumulator to elements
        }

        inline fun <S, A> Iterable<State<S, A>>.all(crossinline predicate: (A) -> State<S, Boolean>): State<S, Boolean> = State {
            var accumulator = this
            for (state in this@all) {
                val (s, success) = (state % predicate)(accumulator)
                if (!success) {
                    return@State accumulator to false
                }
                accumulator = s
            }
            accumulator to true
        }
    }
}
