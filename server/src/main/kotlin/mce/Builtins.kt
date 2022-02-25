package mce

import mce.phase.Normalizer
import mce.graph.Core as C

@Suppress("NAME_SHADOWING")
private val COMM: Comparator<C.Value> = Comparator { value1, value2 ->
    when {
        value1 is C.Value.Var && value2 is C.Value.Var -> value1.level.compareTo(value2.level)
        value1 is C.Value.Var -> 1
        value2 is C.Value.Var -> -1
        else -> 0
    }
}

val BUILTINS: Map<String, Normalizer.() -> C.Value> = mapOf(
    "int/add" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value + b.value)
            // 0 + b = b
            a is C.Value.IntOf && a.value == 0 -> b
            // a + 0 = a
            b is C.Value.IntOf && b.value == 0 -> a
            else -> C.Value.Def("int/add", mutableListOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/sub" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value - b.value)
            // a - 0 = a
            b is C.Value.IntOf && b.value == 0 -> a
            // a - a = 0
            a is C.Value.Var && b is C.Value.Var && a.level == b.level -> C.Value.IntOf(0)
            else -> C.Value.Def("int/sub", mutableListOf(a, b).map(::lazyOf))
        }
    },
    "int/mul" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value * b.value)
            // 0 * b = 0
            a is C.Value.IntOf && a.value == 0 -> C.Value.IntOf(0)
            // a * 0 = 0
            b is C.Value.IntOf && b.value == 0 -> C.Value.IntOf(0)
            // 1 * b = b
            a is C.Value.IntOf && a.value == 1 -> b
            // a * 1 = a
            b is C.Value.IntOf && b.value == 1 -> a
            else -> C.Value.Def("int/mul", mutableListOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/div" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(Math.floorDiv(a.value, b.value))
            // a / 1 = a
            b is C.Value.IntOf && b.value == 1 -> a
            // a / a = 1
            a is C.Value.Var && b is C.Value.Var && a.level == b.level -> C.Value.IntOf(1)
            else -> C.Value.Def("int/div", mutableListOf(a, b).map(::lazyOf))
        }
    },
    "int/mod" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(Math.floorMod(a.value, b.value))
            // a % 1 = 0
            b is C.Value.IntOf && b.value == 1 -> a
            // a % a = 0
            a is C.Value.Var && b is C.Value.Var && a.level == b.level -> C.Value.IntOf(0)
            else -> C.Value.Def("int/mod", mutableListOf(a, b).map(::lazyOf))
        }
    },
    "list/length" to {
        when (val `as` = lookup(size - 1)) {
            is C.Value.ListOf -> C.Value.IntOf(`as`.elements.size)
            else -> C.Value.Def("list/length", listOf())
        }
    }
)
