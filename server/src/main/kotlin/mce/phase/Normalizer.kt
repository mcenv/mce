package mce.phase

import mce.BUILTINS
import mce.graph.Id
import mce.graph.Core as C

class Normalizer(
    private val values: MutableList<Lazy<C.Value>>,
    private val items: Map<String, C.Item>,
    private val solutions: MutableList<C.Value?>
) {
    val size: Int get() = values.size

    fun substitute(level: Int, value: Lazy<C.Value>) {
        values[level] = value
    }

    fun bind(value: Lazy<C.Value>) {
        values += value
    }

    fun pop() {
        values.removeLast()
    }

    inline fun <R> scope(block: Normalizer.() -> R): R {
        val size = this.size
        return block().also {
            repeat(this.size - size) { pop() }
        }
    }

    /**
     * Returns the solution at the [index].
     */
    fun getSolution(index: Int): C.Value? = solutions[index]

    /**
     * Solves for the meta-variable at the [index] as the [solution].
     */
    fun solve(index: Int, solution: C.Value) {
        solutions[index] = solution
    }

    /**
     * Creates a fresh meta-variable.
     */
    fun fresh(id: Id): C.Value = C.Value.Meta(solutions.size, id).also { solutions += null }

    /**
     * Unfolds meta-variables of the [value] recursively.
     */
    tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (val forced = getSolution(value.index)) {
            null -> value
            else -> force(forced)
        }
        else -> value
    }

    /**
     * Evaluates the [term] to a value.
     */
    fun eval(term: C.Term): C.Value = when (term) {
        is C.Term.Hole -> C.Value.Hole
        is C.Term.Meta -> getSolution(term.index) ?: C.Value.Meta(term.index, term.id)
        is C.Term.Variable -> values[term.level].value
        is C.Term.Def -> {
            val item = items[term.name]!! as C.Item.Def
            if (item.modifiers.contains(C.Modifier.BUILTIN)) C.Value.Def(term.name) else eval(item.body)
        }
        is C.Term.Let -> scope {
            bind(lazy { eval(term.init) })
            eval(term.body)
        }
        is C.Term.Match -> C.Value.Match(eval(term.scrutinee), term.clauses.map { it.first to lazy { eval(it.second) /* TODO: collect variables */ } })
        is C.Term.BoolOf -> C.Value.BoolOf(term.value)
        is C.Term.ByteOf -> C.Value.ByteOf(term.value)
        is C.Term.ShortOf -> C.Value.ShortOf(term.value)
        is C.Term.IntOf -> C.Value.IntOf(term.value)
        is C.Term.LongOf -> C.Value.LongOf(term.value)
        is C.Term.FloatOf -> C.Value.FloatOf(term.value)
        is C.Term.DoubleOf -> C.Value.DoubleOf(term.value)
        is C.Term.StringOf -> C.Value.StringOf(term.value)
        is C.Term.ByteArrayOf -> C.Value.ByteArrayOf(term.elements.map { lazy { eval(it) } })
        is C.Term.IntArrayOf -> C.Value.IntArrayOf(term.elements.map { lazy { eval(it) } })
        is C.Term.LongArrayOf -> C.Value.LongArrayOf(term.elements.map { lazy { eval(it) } })
        is C.Term.ListOf -> C.Value.ListOf(term.elements.map { lazy { eval(it) } })
        is C.Term.CompoundOf -> C.Value.CompoundOf(term.elements.map { lazy { eval(it) } })
        is C.Term.RefOf -> C.Value.RefOf(lazy { eval(term.element) })
        is C.Term.Refl -> C.Value.Refl
        is C.Term.FunOf -> C.Value.FunOf(term.parameters, term.body)
        is C.Term.Apply -> {
            val arguments = term.arguments.map { lazy { eval(it) } }
            when (val function = eval(term.function)) {
                is C.Value.Def -> {
                    arguments.forEach { bind(it) }
                    BUILTINS[function.name]!!(arguments)
                }
                is C.Value.FunOf -> scope {
                    arguments.forEach { bind(it) }
                    eval(function.body)
                }
                else -> C.Value.Apply(function, arguments)
            }
        }
        is C.Term.ThunkOf -> C.Value.ThunkOf(lazy { eval(term.body) })
        is C.Term.Force -> when (val thunk = eval(term.element)) {
            is C.Value.ThunkOf -> thunk.body.value
            else -> C.Value.Force(lazyOf(thunk))
        }
        is C.Term.CodeOf -> C.Value.CodeOf(lazy { eval(term.element) })
        is C.Term.Splice -> when (val element = eval(term.element)) {
            is C.Value.CodeOf -> element.element.value
            else -> C.Value.Splice(lazyOf(element))
        }
        is C.Term.Union -> C.Value.Union(term.variants.map { lazy { eval(it) } })
        is C.Term.Intersection -> C.Value.Intersection(term.variants.map { lazy { eval(it) } })
        is C.Term.Bool -> C.Value.Bool
        is C.Term.Byte -> C.Value.Byte
        is C.Term.Short -> C.Value.Short
        is C.Term.Int -> C.Value.Int
        is C.Term.Long -> C.Value.Long
        is C.Term.Float -> C.Value.Float
        is C.Term.Double -> C.Value.Double
        is C.Term.String -> C.Value.String
        is C.Term.ByteArray -> C.Value.ByteArray
        is C.Term.IntArray -> C.Value.IntArray
        is C.Term.LongArray -> C.Value.LongArray
        is C.Term.List -> C.Value.List(lazy { eval(term.element) })
        is C.Term.Compound -> C.Value.Compound(term.elements)
        is C.Term.Ref -> C.Value.Ref(lazy { eval(term.element) })
        is C.Term.Eq -> C.Value.Eq(lazy { eval(term.left) }, lazy { eval(term.right) })
        is C.Term.Fun -> C.Value.Fun(term.parameters, term.resultant)
        is C.Term.Thunk -> C.Value.Thunk(lazy { eval(term.element) }, term.effects)
        is C.Term.Code -> C.Value.Code(lazy { eval(term.element) })
        is C.Term.Type -> C.Value.Type
    }

    /**
     * Quotes the [value] to a term.
     */
    @Suppress("NAME_SHADOWING")
    fun quote(value: C.Value): C.Term = when (val value = force(value)) {
        is C.Value.Hole -> C.Term.Hole
        is C.Value.Meta -> getSolution(value.index)?.let { quote(it) } ?: C.Term.Meta(value.index, value.id)
        is C.Value.Variable -> C.Term.Variable(value.name, value.level)
        is C.Value.Def -> C.Term.Def(value.name)
        is C.Value.Match -> C.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) })
        is C.Value.BoolOf -> C.Term.BoolOf(value.value)
        is C.Value.ByteOf -> C.Term.ByteOf(value.value)
        is C.Value.ShortOf -> C.Term.ShortOf(value.value)
        is C.Value.IntOf -> C.Term.IntOf(value.value)
        is C.Value.LongOf -> C.Term.LongOf(value.value)
        is C.Value.FloatOf -> C.Term.FloatOf(value.value)
        is C.Value.DoubleOf -> C.Term.DoubleOf(value.value)
        is C.Value.StringOf -> C.Term.StringOf(value.value)
        is C.Value.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quote(it.value) })
        is C.Value.IntArrayOf -> C.Term.IntArrayOf(value.elements.map { quote(it.value) })
        is C.Value.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quote(it.value) })
        is C.Value.ListOf -> C.Term.ListOf(value.elements.map { quote(it.value) })
        is C.Value.CompoundOf -> C.Term.CompoundOf(value.elements.map { quote(it.value) })
        is C.Value.RefOf -> C.Term.RefOf(quote(value.element.value))
        is C.Value.Refl -> C.Term.Refl
        is C.Value.FunOf -> C.Term.FunOf(value.parameters, scope {
            value.parameters.forEach { bind(lazyOf(C.Value.Variable(it, size))) }
            quote(eval(value.body))
        })
        is C.Value.Apply -> C.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) })
        is C.Value.ThunkOf -> C.Term.ThunkOf(quote(value.body.value))
        is C.Value.Force -> C.Term.Force(quote(value.element.value))
        is C.Value.CodeOf -> C.Term.CodeOf(quote(value.element.value))
        is C.Value.Splice -> C.Term.Splice(quote(value.element.value))
        is C.Value.Union -> C.Term.Union(value.variants.map { quote(it.value) })
        is C.Value.Intersection -> C.Term.Intersection(value.variants.map { quote(it.value) })
        is C.Value.Bool -> C.Term.Bool
        is C.Value.Byte -> C.Term.Byte
        is C.Value.Short -> C.Term.Short
        is C.Value.Int -> C.Term.Int
        is C.Value.Long -> C.Term.Long
        is C.Value.Float -> C.Term.Float
        is C.Value.Double -> C.Term.Double
        is C.Value.String -> C.Term.String
        is C.Value.ByteArray -> C.Term.ByteArray
        is C.Value.IntArray -> C.Term.IntArray
        is C.Value.LongArray -> C.Term.LongArray
        is C.Value.List -> C.Term.List(quote(value.element.value))
        is C.Value.Compound -> C.Term.Compound(value.elements)
        is C.Value.Ref -> C.Term.Ref(quote(value.element.value))
        is C.Value.Eq -> C.Term.Eq(quote(value.left.value), quote(value.right.value))
        is C.Value.Fun -> C.Term.Fun(value.parameters, scope {
            value.parameters.forEach { (name, _) -> bind(lazyOf(C.Value.Variable(name, size))) }
            quote(eval(value.resultant))
        })
        is C.Value.Thunk -> C.Term.Thunk(quote(value.element.value), value.effects)
        is C.Value.Code -> C.Term.Code(quote(value.element.value))
        is C.Value.Type -> C.Term.Type
    }

    /**
     * Normalizes the [term] to a term.
     */
    fun norm(term: C.Term): C.Term = quote(eval(term))
}
