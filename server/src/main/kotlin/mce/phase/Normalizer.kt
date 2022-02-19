package mce.phase

import mce.BUILTINS
import mce.graph.Id
import mce.graph.freshId
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
        is C.Term.Hole -> C.Value.Hole(term.id)
        is C.Term.Meta -> getSolution(term.index) ?: C.Value.Meta(term.index, term.id)
        is C.Term.Var -> values[term.level].value
        is C.Term.Def -> {
            val item = items[term.name]!! as C.Item.Def
            if (item.modifiers.contains(C.Modifier.BUILTIN)) C.Value.Def(term.name, term.id) else eval(item.body)
        }
        is C.Term.Let -> scope {
            bind(lazy { eval(term.init) })
            eval(term.body)
        }
        is C.Term.Match -> C.Value.Match(eval(term.scrutinee), term.clauses.map { it.first to lazy { eval(it.second) /* TODO: collect variables */ } }, term.id)
        is C.Term.BoolOf -> C.Value.BoolOf(term.value, term.id)
        is C.Term.ByteOf -> C.Value.ByteOf(term.value, term.id)
        is C.Term.ShortOf -> C.Value.ShortOf(term.value, term.id)
        is C.Term.IntOf -> C.Value.IntOf(term.value, term.id)
        is C.Term.LongOf -> C.Value.LongOf(term.value, term.id)
        is C.Term.FloatOf -> C.Value.FloatOf(term.value, term.id)
        is C.Term.DoubleOf -> C.Value.DoubleOf(term.value, term.id)
        is C.Term.StringOf -> C.Value.StringOf(term.value, term.id)
        is C.Term.ByteArrayOf -> C.Value.ByteArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.IntArrayOf -> C.Value.IntArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.LongArrayOf -> C.Value.LongArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.ListOf -> C.Value.ListOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.CompoundOf -> C.Value.CompoundOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.BoxOf -> C.Value.BoxOf(lazy { eval(term.content) }, term.id)
        is C.Term.RefOf -> C.Value.RefOf(lazy { eval(term.element) }, term.id)
        is C.Term.Refl -> C.Value.Refl(term.id)
        is C.Term.FunOf -> C.Value.FunOf(term.parameters, term.body, term.id)
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
                else -> C.Value.Apply(function, arguments, term.id)
            }
        }
        is C.Term.CodeOf -> C.Value.CodeOf(lazy { eval(term.element) }, term.id)
        is C.Term.Splice -> when (val element = eval(term.element)) {
            is C.Value.CodeOf -> element.element.value
            else -> C.Value.Splice(lazyOf(element), term.id)
        }
        is C.Term.Union -> C.Value.Union(term.variants.map { lazy { eval(it) } }, term.id)
        is C.Term.Intersection -> C.Value.Intersection(term.variants.map { lazy { eval(it) } }, term.id)
        is C.Term.Bool -> C.Value.Bool(term.id)
        is C.Term.Byte -> C.Value.Byte(term.id)
        is C.Term.Short -> C.Value.Short(term.id)
        is C.Term.Int -> C.Value.Int(term.id)
        is C.Term.Long -> C.Value.Long(term.id)
        is C.Term.Float -> C.Value.Float(term.id)
        is C.Term.Double -> C.Value.Double(term.id)
        is C.Term.String -> C.Value.String(term.id)
        is C.Term.ByteArray -> C.Value.ByteArray(term.id)
        is C.Term.IntArray -> C.Value.IntArray(term.id)
        is C.Term.LongArray -> C.Value.LongArray(term.id)
        is C.Term.List -> C.Value.List(lazy { eval(term.element) }, term.id)
        is C.Term.Compound -> C.Value.Compound(term.elements, term.id)
        is C.Term.Box -> C.Value.Box(lazy { eval(term.content) }, term.id)
        is C.Term.Ref -> C.Value.Ref(lazy { eval(term.element) }, term.id)
        is C.Term.Eq -> C.Value.Eq(lazy { eval(term.left) }, lazy { eval(term.right) }, term.id)
        is C.Term.Fun -> C.Value.Fun(term.parameters, term.resultant, term.effects, term.id)
        is C.Term.Code -> C.Value.Code(lazy { eval(term.element) }, term.id)
        is C.Term.Type -> C.Value.Type(term.id)
    }

    /**
     * Quotes the [value] to a term.
     */
    @Suppress("NAME_SHADOWING")
    fun quote(value: C.Value): C.Term = when (val value = force(value)) {
        is C.Value.Hole -> C.Term.Hole(value.id)
        is C.Value.Meta -> getSolution(value.index)?.let { quote(it) } ?: C.Term.Meta(value.index, value.id)
        is C.Value.Var -> C.Term.Var(value.name, value.level, value.id)
        is C.Value.Def -> C.Term.Def(value.name, value.id)
        is C.Value.Match -> C.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) }, value.id)
        is C.Value.BoolOf -> C.Term.BoolOf(value.value, value.id)
        is C.Value.ByteOf -> C.Term.ByteOf(value.value, value.id)
        is C.Value.ShortOf -> C.Term.ShortOf(value.value, value.id)
        is C.Value.IntOf -> C.Term.IntOf(value.value, value.id)
        is C.Value.LongOf -> C.Term.LongOf(value.value, value.id)
        is C.Value.FloatOf -> C.Term.FloatOf(value.value, value.id)
        is C.Value.DoubleOf -> C.Term.DoubleOf(value.value, value.id)
        is C.Value.StringOf -> C.Term.StringOf(value.value, value.id)
        is C.Value.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.Value.IntArrayOf -> C.Term.IntArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.Value.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.Value.ListOf -> C.Term.ListOf(value.elements.map { quote(it.value) }, value.id)
        is C.Value.CompoundOf -> C.Term.CompoundOf(value.elements.map { quote(it.value) }, value.id)
        is C.Value.BoxOf -> C.Term.BoxOf(quote(value.content.value), value.id)
        is C.Value.RefOf -> C.Term.RefOf(quote(value.element.value), value.id)
        is C.Value.Refl -> C.Term.Refl(value.id)
        is C.Value.FunOf -> C.Term.FunOf(value.parameters, scope {
            value.parameters.forEach { bind(lazyOf(C.Value.Var(it, size, freshId()))) }
            quote(eval(value.body))
        }, value.id)
        is C.Value.Apply -> C.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) }, value.id)
        is C.Value.CodeOf -> C.Term.CodeOf(quote(value.element.value), value.id)
        is C.Value.Splice -> C.Term.Splice(quote(value.element.value), value.id)
        is C.Value.Union -> C.Term.Union(value.variants.map { quote(it.value) }, value.id)
        is C.Value.Intersection -> C.Term.Intersection(value.variants.map { quote(it.value) }, value.id)
        is C.Value.Bool -> C.Term.Bool(value.id)
        is C.Value.Byte -> C.Term.Byte(value.id)
        is C.Value.Short -> C.Term.Short(value.id)
        is C.Value.Int -> C.Term.Int(value.id)
        is C.Value.Long -> C.Term.Long(value.id)
        is C.Value.Float -> C.Term.Float(value.id)
        is C.Value.Double -> C.Term.Double(value.id)
        is C.Value.String -> C.Term.String(value.id)
        is C.Value.ByteArray -> C.Term.ByteArray(value.id)
        is C.Value.IntArray -> C.Term.IntArray(value.id)
        is C.Value.LongArray -> C.Term.LongArray(value.id)
        is C.Value.List -> C.Term.List(quote(value.element.value), value.id)
        is C.Value.Compound -> C.Term.Compound(value.elements, value.id)
        is C.Value.Box -> C.Term.Box(quote(value.content.value), value.id)
        is C.Value.Ref -> C.Term.Ref(quote(value.element.value), value.id)
        is C.Value.Eq -> C.Term.Eq(quote(value.left.value), quote(value.right.value), value.id)
        is C.Value.Fun -> C.Term.Fun(value.parameters, scope {
            value.parameters.forEach { (name, _) -> bind(lazyOf(C.Value.Var(name, size, freshId()))) }
            quote(eval(value.resultant))
        }, value.effects, value.id)
        is C.Value.Code -> C.Term.Code(quote(value.element.value), value.id)
        is C.Value.Type -> C.Term.Type(value.id)
    }

    /**
     * Normalizes the [term] to a term.
     */
    fun norm(term: C.Term): C.Term = quote(eval(term))
}
