package mce

import mce.graph.Core as C

val BUILTINS: Map<String, (List<Lazy<C.Value>>) -> C.Value> = mapOf(
    "int/add" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(a.value + b.value)
    },
    "int/sub" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(a.value - b.value)
    },
    "int/mul" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(a.value * b.value)
    },
    "int/div" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(Math.floorDiv(a.value, b.value))
    },
    "int/mod" to { arguments ->
        val a = arguments[0].value as C.Value.IntOf
        val b = arguments[1].value as C.Value.IntOf
        C.Value.IntOf(Math.floorMod(a.value, b.value))
    }
)
