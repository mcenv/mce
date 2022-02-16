package mce

import mce.graph.Core as C

val BUILTINS: Map<String, (List<Lazy<C.Value>>) -> C.Value> = mapOf(
    "int/add" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(a.value + b.value)
    }
)
