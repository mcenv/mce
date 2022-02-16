package mce

import mce.graph.Core as C

@Suppress("NAME_SHADOWING")
private val COMM: Comparator<Lazy<C.Value>> = Comparator { value1, value2 ->
    val value1 = value1.value
    val value2 = value2.value
    when {
        value1 is C.Value.Variable && value2 is C.Value.Variable -> value1.level.compareTo(value2.level)
        value1 is C.Value.Variable -> 1
        value2 is C.Value.Variable -> -1
        else -> 0
    }
}

val BUILTINS: Map<String, (List<Lazy<C.Value>>) -> C.Value> = mapOf(
    "int/add" to { arguments ->
        val a = arguments[0].value
        val b = arguments[1].value
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value + b.value)
            // 0 + b = b
            a is C.Value.IntOf && a.value == 0 -> b
            // a + 0 = a
            b is C.Value.IntOf && b.value == 0 -> a
            else -> C.Value.Apply(C.Value.Def("int/add"), arguments.sortedWith(COMM))
        }
    },
    "int/sub" to { arguments ->
        val a = arguments[0].value
        val b = arguments[1].value
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(a.value - b.value)
            // a - 0 = a
            b is C.Value.IntOf && b.value == 0 -> a
            else -> C.Value.Apply(C.Value.Def("int/sub"), arguments)
        }
    },
    "int/mul" to { arguments ->
        val a = arguments[0].value
        val b = arguments[1].value
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
            else -> C.Value.Apply(C.Value.Def("int/mul"), arguments.sortedWith(COMM))
        }
    },
    "int/div" to { arguments ->
        val a = arguments[0].value
        val b = arguments[1].value
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(Math.floorDiv(a.value, b.value))
            // a / 1 = a
            b is C.Value.IntOf && b.value == 1 -> a
            else -> C.Value.Apply(C.Value.Def("int/div"), arguments)
        }
    },
    "int/mod" to { arguments ->
        val a = arguments[0].value
        val b = arguments[1].value
        when {
            a is C.Value.IntOf && b is C.Value.IntOf -> C.Value.IntOf(Math.floorMod(a.value, b.value))
            // a % 1 = 0
            b is C.Value.IntOf && b.value == 1 -> a
            else -> C.Value.Apply(C.Value.Def("int/mod"), arguments)
        }
    }
)
