package mce.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.BUILTINS
import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C

class Normalizer(
    private val values: PersistentList<Lazy<C.VTerm>>,
    private val items: Map<String, C.Item>,
    private val solutions: MutableList<C.VTerm?>
) {
    val size: Int get() = values.size

    fun lookup(level: Int): C.VTerm = values.getOrNull(level)?.value ?: C.VTerm.Var("", level)

    fun subst(level: Int, term: Lazy<C.VTerm>): Normalizer = Normalizer(values.set(level, term), items, solutions)

    fun bind(term: Lazy<C.VTerm>): Normalizer = Normalizer(values + term, items, solutions)

    fun empty(): Normalizer = Normalizer(persistentListOf(), items, solutions)

    /**
     * Returns the solution at the [index].
     */
    fun getSolution(index: Int): C.VTerm? = solutions[index]

    /**
     * Solves for the meta-variable at the [index] as the [solution].
     */
    fun solve(index: Int, solution: C.VTerm) {
        solutions[index] = solution
    }

    /**
     * Creates a fresh meta-variable.
     */
    fun fresh(id: Id): C.VTerm = C.VTerm.Meta(solutions.size, id).also { solutions += null }

    /**
     * Reveals the head constructor of the [term].
     */
    tailrec fun force(term: C.VTerm): C.VTerm = when (term) {
        is C.VTerm.Meta -> when (val forced = getSolution(term.index)) {
            null -> forceSubst(term)
            else -> force(forced)
        }
        else -> forceSubst(term)
    }

    /**
     * Performs the pending substitution for the [term].
     */
    private tailrec fun forceSubst(term: C.VTerm): C.VTerm = when (term) {
        is C.VTerm.Var -> {
            val forced = lookup(term.level)
            when {
                forced is C.VTerm.Var && term.level != forced.level -> forceSubst(forced)
                else -> forced
            }
        }
        else -> term
    }

    /**
     * Evaluates the [term] to a value.
     */
    fun eval(term: C.Term): C.VTerm = when (term) {
        is C.Term.Hole -> C.VTerm.Hole(term.id)
        is C.Term.Meta -> getSolution(term.index) ?: C.VTerm.Meta(term.index, term.id)
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
                null -> C.VTerm.Match(scrutinee, term.clauses.map { (clause, body) -> clause to lazy { match(clause, scrutinee).first.eval(body) } }, term.id)
                else -> clause.first.eval(clause.second)
            }
        }
        is C.Term.BoolOf -> C.VTerm.BoolOf(term.value, term.id)
        is C.Term.ByteOf -> C.VTerm.ByteOf(term.value, term.id)
        is C.Term.ShortOf -> C.VTerm.ShortOf(term.value, term.id)
        is C.Term.IntOf -> C.VTerm.IntOf(term.value, term.id)
        is C.Term.LongOf -> C.VTerm.LongOf(term.value, term.id)
        is C.Term.FloatOf -> C.VTerm.FloatOf(term.value, term.id)
        is C.Term.DoubleOf -> C.VTerm.DoubleOf(term.value, term.id)
        is C.Term.StringOf -> C.VTerm.StringOf(term.value, term.id)
        is C.Term.ByteArrayOf -> C.VTerm.ByteArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.IntArrayOf -> C.VTerm.IntArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.LongArrayOf -> C.VTerm.LongArrayOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.ListOf -> C.VTerm.ListOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.CompoundOf -> C.VTerm.CompoundOf(term.elements.map { lazy { eval(it) } }, term.id)
        is C.Term.BoxOf -> C.VTerm.BoxOf(lazy { eval(term.content) }, lazy { eval(term.tag) }, term.id)
        is C.Term.RefOf -> C.VTerm.RefOf(lazy { eval(term.element) }, term.id)
        is C.Term.Refl -> C.VTerm.Refl(term.id)
        is C.Term.FunOf -> C.VTerm.FunOf(term.parameters, term.body, term.id)
        is C.Term.Apply -> {
            val arguments = term.arguments.map { lazy { eval(it) } }
            when (val function = eval(term.function)) {
                is C.VTerm.FunOf -> arguments.fold(this) { normalizer, argument -> normalizer.bind(argument) }.eval(function.body)
                else -> C.VTerm.Apply(function, arguments, term.id)
            }
        }
        is C.Term.CodeOf -> C.VTerm.CodeOf(lazy { eval(term.element) }, term.id)
        is C.Term.Splice -> when (val element = eval(term.element)) {
            is C.VTerm.CodeOf -> element.element.value
            else -> C.VTerm.Splice(lazyOf(element), term.id)
        }
        is C.Term.Union -> C.VTerm.Union(term.variants.map { lazy { eval(it) } }, term.id)
        is C.Term.Intersection -> C.VTerm.Intersection(term.variants.map { lazy { eval(it) } }, term.id)
        is C.Term.Bool -> C.VTerm.Bool(term.id)
        is C.Term.Byte -> C.VTerm.Byte(term.id)
        is C.Term.Short -> C.VTerm.Short(term.id)
        is C.Term.Int -> C.VTerm.Int(term.id)
        is C.Term.Long -> C.VTerm.Long(term.id)
        is C.Term.Float -> C.VTerm.Float(term.id)
        is C.Term.Double -> C.VTerm.Double(term.id)
        is C.Term.String -> C.VTerm.String(term.id)
        is C.Term.ByteArray -> C.VTerm.ByteArray(term.id)
        is C.Term.IntArray -> C.VTerm.IntArray(term.id)
        is C.Term.LongArray -> C.VTerm.LongArray(term.id)
        is C.Term.List -> C.VTerm.List(lazy { eval(term.element) }, lazy { eval(term.size) }, term.id)
        is C.Term.Compound -> C.VTerm.Compound(term.elements, term.id)
        is C.Term.Box -> C.VTerm.Box(lazy { eval(term.content) }, term.id)
        is C.Term.Ref -> C.VTerm.Ref(lazy { eval(term.element) }, term.id)
        is C.Term.Eq -> C.VTerm.Eq(lazy { eval(term.left) }, lazy { eval(term.right) }, term.id)
        is C.Term.Fun -> C.VTerm.Fun(term.parameters, term.resultant, term.effects, term.id)
        is C.Term.Code -> C.VTerm.Code(lazy { eval(term.element) }, term.id)
        is C.Term.Type -> C.VTerm.Type(term.id)
    }

    private fun match(pattern: C.Pattern, term: C.VTerm): Pair<Normalizer, Boolean> = when {
        pattern is C.Pattern.Var -> bind(lazyOf(term)) to true
        pattern is C.Pattern.BoolOf && term is C.VTerm.BoolOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.ByteOf && term is C.VTerm.ByteOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.ShortOf && term is C.VTerm.ShortOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.IntOf && term is C.VTerm.IntOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.LongOf && term is C.VTerm.LongOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.FloatOf && term is C.VTerm.FloatOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.DoubleOf && term is C.VTerm.DoubleOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.StringOf && term is C.VTerm.StringOf -> this to (term.value == pattern.value)
        pattern is C.Pattern.ByteArrayOf && term is C.VTerm.ByteArrayOf -> if (pattern.elements.size == term.elements.size) {
            (pattern.elements zip term.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.IntArrayOf && term is C.VTerm.IntArrayOf -> if (pattern.elements.size == term.elements.size) {
            (pattern.elements zip term.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.LongArrayOf && term is C.VTerm.LongArrayOf -> if (pattern.elements.size == term.elements.size) {
            (pattern.elements zip term.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.ListOf && term is C.VTerm.ListOf -> if (pattern.elements.size == term.elements.size) {
            (pattern.elements zip term.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.CompoundOf && term is C.VTerm.CompoundOf -> if (pattern.elements.size == term.elements.size) {
            (pattern.elements zip term.elements).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern, value.value) }
        } else this to false
        pattern is C.Pattern.BoxOf && term is C.VTerm.BoxOf -> {
            val (normalizer, matched) = match(pattern.content, term.content.value)
            if (matched) normalizer.match(pattern.tag, term.tag.value) else normalizer to false
        }
        pattern is C.Pattern.RefOf && term is C.VTerm.RefOf -> match(pattern.element, term.element.value)
        pattern is C.Pattern.Refl && term is C.VTerm.Refl -> this to true
        pattern is C.Pattern.Bool && term is C.VTerm.Bool -> this to true
        pattern is C.Pattern.Byte && term is C.VTerm.Byte -> this to true
        pattern is C.Pattern.Short && term is C.VTerm.Short -> this to true
        pattern is C.Pattern.Int && term is C.VTerm.Int -> this to true
        pattern is C.Pattern.Long && term is C.VTerm.Long -> this to true
        pattern is C.Pattern.Float && term is C.VTerm.Float -> this to true
        pattern is C.Pattern.Double && term is C.VTerm.Double -> this to true
        pattern is C.Pattern.String && term is C.VTerm.String -> this to true
        pattern is C.Pattern.ByteArray && term is C.VTerm.ByteArray -> this to true
        pattern is C.Pattern.IntArray && term is C.VTerm.IntArray -> this to true
        pattern is C.Pattern.LongArray && term is C.VTerm.LongArray -> this to true
        else -> this to false
    }

    /**
     * Quotes the [term] to a term.
     */
    @Suppress("NAME_SHADOWING")
    fun quote(term: C.VTerm): C.Term = when (val value = force(term)) {
        is C.VTerm.Hole -> C.Term.Hole(value.id)
        is C.VTerm.Meta -> C.Term.Meta(value.index, value.id)
        is C.VTerm.Var -> C.Term.Var(value.name, value.level, value.id)
        is C.VTerm.Def -> C.Term.Def(value.name, value.arguments.map { quote(it.value) }, value.id)
        is C.VTerm.Match -> C.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) }, value.id)
        is C.VTerm.BoolOf -> C.Term.BoolOf(value.value, value.id)
        is C.VTerm.ByteOf -> C.Term.ByteOf(value.value, value.id)
        is C.VTerm.ShortOf -> C.Term.ShortOf(value.value, value.id)
        is C.VTerm.IntOf -> C.Term.IntOf(value.value, value.id)
        is C.VTerm.LongOf -> C.Term.LongOf(value.value, value.id)
        is C.VTerm.FloatOf -> C.Term.FloatOf(value.value, value.id)
        is C.VTerm.DoubleOf -> C.Term.DoubleOf(value.value, value.id)
        is C.VTerm.StringOf -> C.Term.StringOf(value.value, value.id)
        is C.VTerm.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.VTerm.IntArrayOf -> C.Term.IntArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.VTerm.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quote(it.value) }, value.id)
        is C.VTerm.ListOf -> C.Term.ListOf(value.elements.map { quote(it.value) }, value.id)
        is C.VTerm.CompoundOf -> C.Term.CompoundOf(value.elements.map { quote(it.value) }, value.id)
        is C.VTerm.BoxOf -> C.Term.BoxOf(quote(value.content.value), quote(value.tag.value), value.id)
        is C.VTerm.RefOf -> C.Term.RefOf(quote(value.element.value), value.id)
        is C.VTerm.Refl -> C.Term.Refl(value.id)
        is C.VTerm.FunOf -> C.Term.FunOf(value.parameters, value.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter, normalizer.size, freshId()))) }.quote(eval(value.body)), value.id)
        is C.VTerm.Apply -> C.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) }, value.id)
        is C.VTerm.CodeOf -> C.Term.CodeOf(quote(value.element.value), value.id)
        is C.VTerm.Splice -> C.Term.Splice(quote(value.element.value), value.id)
        is C.VTerm.Union -> C.Term.Union(value.variants.map { quote(it.value) }, value.id)
        is C.VTerm.Intersection -> C.Term.Intersection(value.variants.map { quote(it.value) }, value.id)
        is C.VTerm.Bool -> C.Term.Bool(value.id)
        is C.VTerm.Byte -> C.Term.Byte(value.id)
        is C.VTerm.Short -> C.Term.Short(value.id)
        is C.VTerm.Int -> C.Term.Int(value.id)
        is C.VTerm.Long -> C.Term.Long(value.id)
        is C.VTerm.Float -> C.Term.Float(value.id)
        is C.VTerm.Double -> C.Term.Double(value.id)
        is C.VTerm.String -> C.Term.String(value.id)
        is C.VTerm.ByteArray -> C.Term.ByteArray(value.id)
        is C.VTerm.IntArray -> C.Term.IntArray(value.id)
        is C.VTerm.LongArray -> C.Term.LongArray(value.id)
        is C.VTerm.List -> C.Term.List(quote(value.element.value), quote(value.size.value), value.id)
        is C.VTerm.Compound -> C.Term.Compound(value.elements, value.id)
        is C.VTerm.Box -> C.Term.Box(quote(value.content.value), value.id)
        is C.VTerm.Ref -> C.Term.Ref(quote(value.element.value), value.id)
        is C.VTerm.Eq -> C.Term.Eq(quote(value.left.value), quote(value.right.value), value.id)
        is C.VTerm.Fun -> C.Term.Fun(
            value.parameters,
            value.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter.name, normalizer.size, freshId()))) }.quote(eval(value.resultant)),
            value.effects,
            value.id
        )
        is C.VTerm.Code -> C.Term.Code(quote(value.element.value), value.id)
        is C.VTerm.Type -> C.Term.Type(value.id)
    }

    /**
     * Normalizes the [term] to a term.
     */
    fun norm(term: C.Term): C.Term = quote(eval(term))
}
