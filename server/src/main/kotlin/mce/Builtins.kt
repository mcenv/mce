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
    "int/eq" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.BoolOf(a.value == b.value)
            // a == a = true
            a is C.Value.Var && b is C.Value.Var && a.level == b.level -> C.Value.BoolOf(true)
            else -> C.Value.Def("int/eq", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/ne" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.BoolOf(a.value != b.value)
            else -> C.Value.Def("int/ne", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/add" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value + b.value)
            // 0 + b = b
            a is C.Value.IntOf && a.value == 0 -> b
            // a + 0 = a
            b is C.Value.IntOf && b.value == 0 -> a
            else -> C.Value.Def("int/add", listOf(a, b).sortedWith(COMM).map(::lazyOf))
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
            else -> C.Value.Def("int/sub", listOf(a, b).map(::lazyOf))
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
            else -> C.Value.Def("int/mul", listOf(a, b).sortedWith(COMM).map(::lazyOf))
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
            else -> C.Value.Def("int/div", listOf(a, b).map(::lazyOf))
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
            else -> C.Value.Def("int/mod", listOf(a, b).map(::lazyOf))
        }
    },
    "byte_array/length" to {
        when (val bs = lookup(size - 1)) {
            is C.Value.ListOf -> C.Value.IntOf(bs.elements.size)
            else -> C.Value.Def("byte_array/length", listOf(bs).map(::lazyOf))
        }
    },
    "int_array/length" to {
        when (val `is` = lookup(size - 1)) {
            is C.Value.ListOf -> C.Value.IntOf(`is`.elements.size)
            else -> C.Value.Def("int_array/length", listOf(`is`).map(::lazyOf))
        }
    },
    "long_array/length" to {
        when (val ls = lookup(size - 1)) {
            is C.Value.ListOf -> C.Value.IntOf(ls.elements.size)
            else -> C.Value.Def("long_array/length", listOf(ls).map(::lazyOf))
        }
    },
    "list/length" to {
        when (val `as` = lookup(size - 1)) {
            is C.Value.ListOf -> C.Value.IntOf(`as`.elements.size)
            else -> {
                val α = lookup(size - 3)
                val n = lookup(size - 2)
                C.Value.Def("list/length", listOf(α, n, `as`).map(::lazyOf))
            }
        }
    }
)
