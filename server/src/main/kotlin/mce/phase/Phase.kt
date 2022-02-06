package mce.phase

interface Phase<I, O> {
    operator fun invoke(input: I): O
}
