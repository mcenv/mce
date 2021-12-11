package mce.phase

import mce.Diagnostic

interface Phase<in I, out O> {
    fun run(input: I): Pair<O, List<Diagnostic>>
}
