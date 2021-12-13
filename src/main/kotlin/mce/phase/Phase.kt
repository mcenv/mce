package mce.phase

interface Phase<in I, out O> {
    fun run(input: I): O
}
