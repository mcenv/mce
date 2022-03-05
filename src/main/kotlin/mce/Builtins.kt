package mce

import mce.phase.Normalizer
import mce.graph.Core as C

@Suppress("NAME_SHADOWING")
private val COMM: Comparator<C.VTerm> = Comparator { value1, value2 ->
    when {
        value1 is C.VTerm.Var && value2 is C.VTerm.Var -> value1.level.compareTo(value2.level)
        value1 is C.VTerm.Var -> 1
        value2 is C.VTerm.Var -> -1
        else -> 0
    }
}

val BUILTINS: Map<String, Normalizer.() -> C.VTerm> = mapOf(
    "int/eq" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.BoolOf(a.value == b.value)
            // a == a = true
            a is C.VTerm.Var && b is C.VTerm.Var && a.level == b.level -> C.VTerm.BoolOf(true)
            else -> C.VTerm.Def("int/eq", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/ne" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.BoolOf(a.value != b.value)
            else -> C.VTerm.Def("int/ne", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/add" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.IntOf(a.value + b.value)
            // 0 + b = b
            a is C.VTerm.IntOf && a.value == 0 -> b
            // a + 0 = a
            b is C.VTerm.IntOf && b.value == 0 -> a
            else -> C.VTerm.Def("int/add", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/sub" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.IntOf(a.value - b.value)
            // a - 0 = a
            b is C.VTerm.IntOf && b.value == 0 -> a
            // a - a = 0
            a is C.VTerm.Var && b is C.VTerm.Var && a.level == b.level -> C.VTerm.IntOf(0)
            else -> C.VTerm.Def("int/sub", listOf(a, b).map(::lazyOf))
        }
    },
    "int/mul" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.IntOf(a.value * b.value)
            // 0 * b = 0
            a is C.VTerm.IntOf && a.value == 0 -> C.VTerm.IntOf(0)
            // a * 0 = 0
            b is C.VTerm.IntOf && b.value == 0 -> C.VTerm.IntOf(0)
            // 1 * b = b
            a is C.VTerm.IntOf && a.value == 1 -> b
            // a * 1 = a
            b is C.VTerm.IntOf && b.value == 1 -> a
            else -> C.VTerm.Def("int/mul", listOf(a, b).sortedWith(COMM).map(::lazyOf))
        }
    },
    "int/div" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.IntOf(Math.floorDiv(a.value, b.value))
            // a / 1 = a
            b is C.VTerm.IntOf && b.value == 1 -> a
            // a / a = 1
            a is C.VTerm.Var && b is C.VTerm.Var && a.level == b.level -> C.VTerm.IntOf(1)
            else -> C.VTerm.Def("int/div", listOf(a, b).map(::lazyOf))
        }
    },
    "int/mod" to {
        val a = lookup(size - 2)
        val b = lookup(size - 1)
        when {
            a is C.VTerm.IntOf && b is C.VTerm.IntOf -> C.VTerm.IntOf(Math.floorMod(a.value, b.value))
            // a % 1 = 0
            b is C.VTerm.IntOf && b.value == 1 -> a
            // a % a = 0
            a is C.VTerm.Var && b is C.VTerm.Var && a.level == b.level -> C.VTerm.IntOf(0)
            else -> C.VTerm.Def("int/mod", listOf(a, b).map(::lazyOf))
        }
    },
    "byte_array/size" to {
        when (val bs = lookup(size - 1)) {
            is C.VTerm.ListOf -> C.VTerm.IntOf(bs.elements.size)
            else -> C.VTerm.Def("byte_array/size", listOf(bs).map(::lazyOf))
        }
    },
    "int_array/size" to {
        when (val `is` = lookup(size - 1)) {
            is C.VTerm.ListOf -> C.VTerm.IntOf(`is`.elements.size)
            else -> C.VTerm.Def("int_array/size", listOf(`is`).map(::lazyOf))
        }
    },
    "long_array/size" to {
        when (val ls = lookup(size - 1)) {
            is C.VTerm.ListOf -> C.VTerm.IntOf(ls.elements.size)
            else -> C.VTerm.Def("long_array/size", listOf(ls).map(::lazyOf))
        }
    },
    "list/size" to {
        when (val `as` = lookup(size - 1)) {
            is C.VTerm.ListOf -> C.VTerm.IntOf(`as`.elements.size)
            else -> {
                val α = lookup(size - 3)
                val n = lookup(size - 2)
                C.VTerm.Def("list/size", listOf(α, n, `as`).map(::lazyOf))
            }
        }
    }
)
