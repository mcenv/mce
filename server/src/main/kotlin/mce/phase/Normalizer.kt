package mce.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.BUILTINS
import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C

class Normalizer(
    private val values: PersistentList<Lazy<C.Value>>,
    private val items: Map<String, C.Item>,
    private val solutions: MutableList<C.Value?>
) {
    val size: Int get() = values.size

    fun lookup(level: Int): C.Value = values[level].value

    fun subst(level: Int, value: Lazy<C.Value>): Normalizer = Normalizer(values.set(level, value), items, solutions)

    fun bind(value: Lazy<C.Value>): Normalizer = Normalizer(values + value, items, solutions)

    fun empty(): Normalizer = Normalizer(persistentListOf(), items, solutions)

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
     * Reveals the head constructor of the [value].
     */
    tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (val forced = getSolution(value.index)) {
            null -> forceSubst(value)
            else -> force(forced)
        }
        else -> forceSubst(value)
    }

    /**
     * Performs the pending substitution for the [value].
     */
    private tailrec fun forceSubst(value: C.Value): C.Value = when (value) {
        is C.Value.Var -> {
            val forced = lookup(value.level)
            when {
                forced is C.Value.Var && value.level != forced.level -> forceSubst(forced)
                else -> forced
            }
        }
        else -> value
    }

    /**
     * Evaluates the [term] to a value.
     */
    fun eval(term: C.Term): C.Value = when (term) {
        is C.Term.Hole -> C.Value.Hole(term.id)
        is C.Term.Meta -> getSolution(term.index) ?: C.Value.Meta(term.index, term.id)
        is C.Term.Var -> lookup(term.level)
        is C.Term.Def -> {
            val item = items[term.name]!! as C.Item.Def
            if (item.modifiers.contains(C.Modifier.BUILTIN)) {
                val normalizer = term.arguments.fold(this) { normalizer, argument -> normalizer.bind(lazy { normalizer.eval(argument) }) }
                BUILTINS[term.name]!!(normalizer)
            } else eval(item.body)
        }
        is C.Term.Let -> bind(lazy { eval(term.init) }).eval(term.body)
        is C.Term.Match -> {
            val scrutinee = eval(term.scrutinee)
            val clause = term.clauses.firstMapOrNull { (clause, body) ->
                val (normalizer, matched) = match(clause, scrutinee)
                (normalizer to body) to matched
            }
            when (clause) {
                null -> C.Value.Match(scrutinee, term.clauses.map { (clause, body) -> clause to lazy { match(clause, scrutinee).first.eval(body) } }, term.id)
                else -> clause.first.eval(clause.second)
            }
        }
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
        is C.Term.BoxOf -> C.Value.BoxOf(lazy { eval(term.content) }, lazy { eval(term.tag) }, term.id)
        is C.Term.RefOf -> C.Value.RefOf(lazy { eval(term.element) }, term.id)
        is C.Term.Refl -> C.Value.Refl(term.id)
        is C.Term.FunOf -> C.Value.FunOf(term.parameters, term.body, term.id)
        is C.Term.Apply -> {
            val arguments = term.arguments.map { lazy { eval(it) } }
            when (val function = eval(term.function)) {
                is C.Value.FunOf -> arguments.fold(this) { normalizer, argument -> normalizer.bind(argument) }.eval(function.body)
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

    private fun match(pattern: C.Pattern, value: C.Value): Pair<Normalizer, Boolean> = when {
        pattern is C.Pattern.Var -> bind(lazyOf(value)) to true
        pattern is C.Pattern.BoolOf && value is C.Value.BoolOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.ByteOf && value is C.Value.ByteOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.ShortOf && value is C.Value.ShortOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.IntOf && value is C.Value.IntOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.LongOf && value is C.Value.LongOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.FloatOf && value is C.Value.FloatOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.DoubleOf && value is C.Value.DoubleOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.StringOf && value is C.Value.StringOf -> this to (value.value == pattern.value)
        pattern is C.Pattern.ByteArrayOf && value is C.Value.ByteArrayOf -> if (pattern.elements.size == value.elements.size) {
            (pattern.elements zip value.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.IntArrayOf && value is C.Value.IntArrayOf -> if (pattern.elements.size == value.elements.size) {
            (pattern.elements zip value.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.LongArrayOf && value is C.Value.LongArrayOf -> if (pattern.elements.size == value.elements.size) {
            (pattern.elements zip value.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.ListOf && value is C.Value.ListOf -> if (pattern.elements.size == value.elements.size) {
            (pattern.elements zip value.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.CompoundOf && value is C.Value.CompoundOf -> if (pattern.elements.size == value.elements.size) {
            (pattern.elements zip value.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.BoxOf && value is C.Value.BoxOf -> {
            val (normalizer, matched) = match(pattern.content, value.content.value)
            if (matched) normalizer.match(pattern.tag, value.tag.value) else normalizer to false
        }
        pattern is C.Pattern.RefOf && value is C.Value.RefOf -> match(pattern.element, value.element.value)
        pattern is C.Pattern.Refl && value is C.Value.Refl -> this to true
        pattern is C.Pattern.Bool && value is C.Value.Bool -> this to true
        pattern is C.Pattern.Byte && value is C.Value.Byte -> this to true
        pattern is C.Pattern.Short && value is C.Value.Short -> this to true
        pattern is C.Pattern.Int && value is C.Value.Int -> this to true
        pattern is C.Pattern.Long && value is C.Value.Long -> this to true
        pattern is C.Pattern.Float && value is C.Value.Float -> this to true
        pattern is C.Pattern.Double && value is C.Value.Double -> this to true
        pattern is C.Pattern.String && value is C.Value.String -> this to true
        pattern is C.Pattern.ByteArray && value is C.Value.ByteArray -> this to true
        pattern is C.Pattern.IntArray && value is C.Value.IntArray -> this to true
        pattern is C.Pattern.LongArray && value is C.Value.LongArray -> this to true
        else -> this to false
    }

    /**
     * Quotes the [value] to a term.
     */
    @Suppress("NAME_SHADOWING")
    fun quote(value: C.Value): C.Term = when (val value = force(value)) {
        is C.Value.Hole -> C.Term.Hole(value.id)
        is C.Value.Meta -> C.Term.Meta(value.index, value.id)
        is C.Value.Var -> C.Term.Var(value.name, value.level, value.id)
        is C.Value.Def -> C.Term.Def(value.name, value.arguments.map { quote(it.value) }, value.id)
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
        is C.Value.BoxOf -> C.Term.BoxOf(quote(value.content.value), quote(value.tag.value), value.id)
        is C.Value.RefOf -> C.Term.RefOf(quote(value.element.value), value.id)
        is C.Value.Refl -> C.Term.Refl(value.id)
        is C.Value.FunOf -> C.Term.FunOf(value.parameters, value.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.Value.Var(parameter, normalizer.size, freshId()))) }.quote(eval(value.body)), value.id)
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
        is C.Value.Fun -> C.Term.Fun(
            value.parameters,
            value.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.Value.Var(parameter.name, normalizer.size, freshId()))) }.quote(eval(value.resultant)),
            value.effects,
            value.id
        )
        is C.Value.Code -> C.Term.Code(quote(value.element.value), value.id)
        is C.Value.Type -> C.Term.Type(value.id)
    }

    /**
     * Normalizes the [term] to a term.
     */
    fun norm(term: C.Term): C.Term = quote(eval(term))
}
