package mce.phase.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.BUILTINS
import mce.graph.Id
import mce.graph.freshId
import mce.util.firstMapOrNull
import mce.util.foldAll
import mce.util.mapSecond
import mce.util.toLinkedHashMap
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
     * Evaluates the [term] to a semantic term.
     */
    fun evalTerm(term: C.Term): C.VTerm = when (term) {
        is C.Term.Hole -> C.VTerm.Hole(term.id)
        is C.Term.Meta -> getSolution(term.index) ?: C.VTerm.Meta(term.index, term.id)
        is C.Term.Var -> lookup(term.level)
        is C.Term.Def -> {
            val item = items[term.name]!! as C.Item.Def
            if (item.modifiers.contains(C.Modifier.BUILTIN)) {
                val normalizer = term.arguments.fold(this) { normalizer, argument -> normalizer.bind(lazy { normalizer.evalTerm(argument) }) }
                BUILTINS[term.name]!!(normalizer)
            } else evalTerm(item.body)
        }
        is C.Term.Let -> bind(lazy { evalTerm(term.init) }).evalTerm(term.body)
        is C.Term.Match -> {
            val scrutinee = evalTerm(term.scrutinee)
            val clause = term.clauses.firstMapOrNull { (clause, body) ->
                val (normalizer, matched) = match(clause, scrutinee)
                (normalizer to body) to matched
            }
            when (clause) {
                null -> C.VTerm.Match(scrutinee, term.clauses.map { (clause, body) -> clause to lazy { match(clause, scrutinee).first.evalTerm(body) } }, term.id)
                else -> clause.first.evalTerm(clause.second)
            }
        }
        is C.Term.UnitOf -> C.VTerm.UnitOf(term.id)
        is C.Term.BoolOf -> C.VTerm.BoolOf(term.value, term.id)
        is C.Term.ByteOf -> C.VTerm.ByteOf(term.value, term.id)
        is C.Term.ShortOf -> C.VTerm.ShortOf(term.value, term.id)
        is C.Term.IntOf -> C.VTerm.IntOf(term.value, term.id)
        is C.Term.LongOf -> C.VTerm.LongOf(term.value, term.id)
        is C.Term.FloatOf -> C.VTerm.FloatOf(term.value, term.id)
        is C.Term.DoubleOf -> C.VTerm.DoubleOf(term.value, term.id)
        is C.Term.StringOf -> C.VTerm.StringOf(term.value, term.id)
        is C.Term.ByteArrayOf -> C.VTerm.ByteArrayOf(term.elements.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.IntArrayOf -> C.VTerm.IntArrayOf(term.elements.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.LongArrayOf -> C.VTerm.LongArrayOf(term.elements.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.ListOf -> C.VTerm.ListOf(term.elements.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.CompoundOf -> C.VTerm.CompoundOf(term.elements.map { (name, element) -> name to lazy { evalTerm(element) } }.toLinkedHashMap(), term.id)
        is C.Term.BoxOf -> C.VTerm.BoxOf(lazy { evalTerm(term.content) }, lazy { evalTerm(term.tag) }, term.id)
        is C.Term.RefOf -> C.VTerm.RefOf(lazy { evalTerm(term.element) }, term.id)
        is C.Term.Refl -> C.VTerm.Refl(term.id)
        is C.Term.FunOf -> C.VTerm.FunOf(term.parameters, term.body, term.id)
        is C.Term.Apply -> {
            val arguments = term.arguments.map { lazy { evalTerm(it) } }
            when (val function = evalTerm(term.function)) {
                is C.VTerm.FunOf -> arguments.fold(this) { normalizer, argument -> normalizer.bind(argument) }.evalTerm(function.body)
                else -> C.VTerm.Apply(function, arguments, term.id)
            }
        }
        is C.Term.CodeOf -> C.VTerm.CodeOf(lazy { evalTerm(term.element) }, term.id)
        is C.Term.Splice -> when (val element = evalTerm(term.element)) {
            is C.VTerm.CodeOf -> element.element.value
            else -> C.VTerm.Splice(lazyOf(element), term.id)
        }
        is C.Term.Or -> C.VTerm.Or(term.variants.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.And -> C.VTerm.And(term.variants.map { lazy { evalTerm(it) } }, term.id)
        is C.Term.Unit -> C.VTerm.Unit(term.id)
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
        is C.Term.List -> C.VTerm.List(lazy { evalTerm(term.element) }, lazy { evalTerm(term.size) }, term.id)
        is C.Term.Compound -> C.VTerm.Compound(term.elements, term.id)
        is C.Term.Box -> C.VTerm.Box(lazy { evalTerm(term.content) }, term.id)
        is C.Term.Ref -> C.VTerm.Ref(lazy { evalTerm(term.element) }, term.id)
        is C.Term.Eq -> C.VTerm.Eq(lazy { evalTerm(term.left) }, lazy { evalTerm(term.right) }, term.id)
        is C.Term.Fun -> C.VTerm.Fun(term.parameters, term.resultant, term.effects, term.id)
        is C.Term.Code -> C.VTerm.Code(lazy { evalTerm(term.element) }, term.id)
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
            (pattern.elements.entries zip term.elements.entries).foldAll(this) { normalizer, (pattern, value) -> normalizer.match(pattern.value, value.value.value).mapSecond { it && pattern.key.text == value.key.text } }
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
     * Quotes the [term] to a syntactic term.
     */
    @Suppress("NAME_SHADOWING")
    fun quoteTerm(term: C.VTerm): C.Term = when (val term = force(term)) {
        is C.VTerm.Hole -> C.Term.Hole(term.id)
        is C.VTerm.Meta -> C.Term.Meta(term.index, term.id)
        is C.VTerm.Var -> C.Term.Var(term.name, term.level, term.id)
        is C.VTerm.Def -> C.Term.Def(term.name, term.arguments.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.Match -> C.Term.Match(quoteTerm(term.scrutinee), term.clauses.map { it.first to quoteTerm(it.second.value) }, term.id)
        is C.VTerm.UnitOf -> C.Term.UnitOf(term.id)
        is C.VTerm.BoolOf -> C.Term.BoolOf(term.value, term.id)
        is C.VTerm.ByteOf -> C.Term.ByteOf(term.value, term.id)
        is C.VTerm.ShortOf -> C.Term.ShortOf(term.value, term.id)
        is C.VTerm.IntOf -> C.Term.IntOf(term.value, term.id)
        is C.VTerm.LongOf -> C.Term.LongOf(term.value, term.id)
        is C.VTerm.FloatOf -> C.Term.FloatOf(term.value, term.id)
        is C.VTerm.DoubleOf -> C.Term.DoubleOf(term.value, term.id)
        is C.VTerm.StringOf -> C.Term.StringOf(term.value, term.id)
        is C.VTerm.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.IntArrayOf -> C.Term.IntArrayOf(term.elements.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.ListOf -> C.Term.ListOf(term.elements.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.CompoundOf -> C.Term.CompoundOf(term.elements.map { (name, element) -> name to quoteTerm(element.value) }.toLinkedHashMap(), term.id)
        is C.VTerm.BoxOf -> C.Term.BoxOf(quoteTerm(term.content.value), quoteTerm(term.tag.value), term.id)
        is C.VTerm.RefOf -> C.Term.RefOf(quoteTerm(term.element.value), term.id)
        is C.VTerm.Refl -> C.Term.Refl(term.id)
        is C.VTerm.FunOf -> C.Term.FunOf(term.parameters, term.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter.text, normalizer.size, freshId()))) }.quoteTerm(evalTerm(term.body)), term.id)
        is C.VTerm.Apply -> C.Term.Apply(quoteTerm(term.function), term.arguments.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.CodeOf -> C.Term.CodeOf(quoteTerm(term.element.value), term.id)
        is C.VTerm.Splice -> C.Term.Splice(quoteTerm(term.element.value), term.id)
        is C.VTerm.Or -> C.Term.Or(term.variants.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.And -> C.Term.And(term.variants.map { quoteTerm(it.value) }, term.id)
        is C.VTerm.Unit -> C.Term.Unit(term.id)
        is C.VTerm.Bool -> C.Term.Bool(term.id)
        is C.VTerm.Byte -> C.Term.Byte(term.id)
        is C.VTerm.Short -> C.Term.Short(term.id)
        is C.VTerm.Int -> C.Term.Int(term.id)
        is C.VTerm.Long -> C.Term.Long(term.id)
        is C.VTerm.Float -> C.Term.Float(term.id)
        is C.VTerm.Double -> C.Term.Double(term.id)
        is C.VTerm.String -> C.Term.String(term.id)
        is C.VTerm.ByteArray -> C.Term.ByteArray(term.id)
        is C.VTerm.IntArray -> C.Term.IntArray(term.id)
        is C.VTerm.LongArray -> C.Term.LongArray(term.id)
        is C.VTerm.List -> C.Term.List(quoteTerm(term.element.value), quoteTerm(term.size.value), term.id)
        is C.VTerm.Compound -> C.Term.Compound(term.elements, term.id)
        is C.VTerm.Box -> C.Term.Box(quoteTerm(term.content.value), term.id)
        is C.VTerm.Ref -> C.Term.Ref(quoteTerm(term.element.value), term.id)
        is C.VTerm.Eq -> C.Term.Eq(quoteTerm(term.left.value), quoteTerm(term.right.value), term.id)
        is C.VTerm.Fun -> C.Term.Fun(
            term.parameters,
            term.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter.name, normalizer.size, freshId()))) }.quoteTerm(evalTerm(term.resultant)),
            term.effects,
            term.id
        )
        is C.VTerm.Code -> C.Term.Code(quoteTerm(term.element.value), term.id)
        is C.VTerm.Type -> C.Term.Type(term.id)
    }

    /**
     * Normalizes the [term] to a term.
     */
    fun normTerm(term: C.Term): C.Term = quoteTerm(evalTerm(term))

    /**
     * Evaluates the [module] to a semantic module.
     */
    fun evalModule(module: C.Module): C.VModule = when (module) {
        is C.Module.Var -> {
            val item = items[module.name] as C.Item.Mod
            evalModule(item.body)
        }
        is C.Module.Str -> C.VModule.Str(module.items, module.id)
        is C.Module.Sig -> {
            val signatures = module.signatures.map { evalSignature(it) }
            C.VModule.Sig(signatures, module.id)
        }
        is C.Module.Type -> C.VModule.Type(module.id)
    }

    /**
     * Evaluates the [signature] to a semantic signature.
     */
    fun evalSignature(signature: C.Signature): C.VSignature = when (signature) {
        is C.Signature.Def -> C.VSignature.Def(signature.name, signature.parameters, signature.resultant, signature.id)
        is C.Signature.Mod -> {
            val type = evalModule(signature.type)
            C.VSignature.Mod(signature.name, type, signature.id)
        }
    }
}
