package mce.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.ast.Id
import mce.ast.freshId
import mce.builtin.builtins
import mce.util.State
import mce.util.toLinkedHashMap
import mce.ast.Core as C

class Normalizer(
    private val values: PersistentList<Lazy<C.VTerm>>,
    val items: Map<String, C.Item>,
    val solutions: MutableList<C.VTerm?>
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

    fun getItem(name: String): C.Item? = items[name]
}

/**
 * Creates a fresh meta-variable.
 */
fun fresh(id: Id): State<Normalizer, C.VTerm> = {
    !gets {
        C.VTerm.Meta(solutions.size, id).also { solutions += null }
    }
}

/**
 * Reveals the head constructor of the [term].
 */
tailrec fun Normalizer.force(term: C.VTerm): C.VTerm = when (term) {
    is C.VTerm.Meta -> when (val forced = getSolution(term.index)) {
        null -> forceSubst(term)
        else -> force(forced)
    }
    else -> forceSubst(term)
}

/**
 * Performs the pending substitution for the [term].
 */
private tailrec fun Normalizer.forceSubst(term: C.VTerm): C.VTerm = when (term) {
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
 * Evaluates the [term] to a semantic term under the normalizer.
 */
fun evalTerm(term: C.Term): State<Normalizer, C.VTerm> = {
    when (term) {
        is C.Term.Hole -> C.VTerm.Hole(term.id)
        is C.Term.Meta -> !gets { getSolution(term.index) } ?: C.VTerm.Meta(term.index, term.id)
        is C.Term.Var -> !gets { lookup(term.level) }
        is C.Term.Def -> {
            val item = !gets { getItem(term.name)!! } as C.Item.Def;
            !term.arguments.forEachM { argument -> modify { bind(lazy { !evalTerm(argument) }) } }
            if (item.modifiers.contains(C.Modifier.BUILTIN)) {
                builtins[term.name]!!(!get())
            } else {
                !evalTerm(item.body)
            }
        }
        is C.Term.Let -> {
            !modify { bind(lazy { !evalTerm(term.init) }) }
            !evalTerm(term.body)
        }
        is C.Term.Match -> {
            val scrutinee = !evalTerm(term.scrutinee)
            val clause = term.clauses.firstOrNull { (pattern, _) ->
                !restore {
                    !match(pattern, scrutinee)
                }
            }
            when (clause) {
                null -> {
                    val clauses = !term.clauses.mapM { (clause, body) ->
                        restore {
                            clause to lazy {
                                !match(clause, scrutinee)
                                !evalTerm(body)
                            }
                        }
                    }
                    C.VTerm.Match(scrutinee, clauses, term.id)
                }
                else -> {
                    !match(clause.first, scrutinee)
                    !evalTerm(clause.second)
                }
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
        is C.Term.ByteArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            C.VTerm.ByteArrayOf(elements, term.id)
        }
        is C.Term.IntArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            C.VTerm.IntArrayOf(elements, term.id)
        }
        is C.Term.LongArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            C.VTerm.LongArrayOf(elements, term.id)
        }
        is C.Term.ListOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            C.VTerm.ListOf(elements, term.id)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { (name, element) -> name to lazy { !evalTerm(element) } }.toLinkedHashMap()
            C.VTerm.CompoundOf(elements, term.id)
        }
        is C.Term.BoxOf -> {
            val content = lazy { !evalTerm(term.content) }
            val tag = lazy { !evalTerm(term.tag) }
            C.VTerm.BoxOf(content, tag, term.id)
        }
        is C.Term.RefOf -> {
            val element = lazy { !evalTerm(term.element) }
            C.VTerm.RefOf(element, term.id)
        }
        is C.Term.Refl -> C.VTerm.Refl(term.id)
        is C.Term.FunOf -> C.VTerm.FunOf(term.parameters, term.body, term.id)
        is C.Term.Apply -> {
            val arguments = term.arguments.map { lazy { !evalTerm(it) } }
            when (val function = !evalTerm(term.function)) {
                is C.VTerm.FunOf -> {
                    !arguments.forEachM { argument -> gets { bind(argument) } }
                    !evalTerm(function.body)
                }
                else -> C.VTerm.Apply(function, arguments, term.id)
            }
        }
        is C.Term.CodeOf -> {
            val element = lazy { !evalTerm(term.element) }
            C.VTerm.CodeOf(element, term.id)
        }
        is C.Term.Splice -> when (val element = !evalTerm(term.element)) {
            is C.VTerm.CodeOf -> element.element.value
            else -> C.VTerm.Splice(lazyOf(element), term.id)
        }
        is C.Term.Or -> {
            val variants = term.variants.map { lazy { !evalTerm(it) } }
            C.VTerm.Or(variants, term.id)
        }
        is C.Term.And -> {
            val variants = term.variants.map { lazy { !evalTerm(it) } }
            C.VTerm.And(variants, term.id)
        }
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
        is C.Term.List -> {
            val element = lazy { !evalTerm(term.element) }
            val size = lazy { !evalTerm(term.size) }
            C.VTerm.List(element, size, term.id)
        }
        is C.Term.Compound -> C.VTerm.Compound(term.elements, term.id)
        is C.Term.Box -> {
            val content = lazy { !evalTerm(term.content) }
            C.VTerm.Box(content, term.id)
        }
        is C.Term.Ref -> {
            val element = lazy { !evalTerm(term.element) }
            C.VTerm.Ref(element, term.id)
        }
        is C.Term.Eq -> {
            val left = lazy { !evalTerm(term.left) }
            val right = lazy { !evalTerm(term.right) }
            C.VTerm.Eq(left, right, term.id)
        }
        is C.Term.Fun -> C.VTerm.Fun(term.parameters, term.resultant, term.effects, term.id)
        is C.Term.Code -> {
            val element = lazy { !evalTerm(term.element) }
            C.VTerm.Code(element, term.id)
        }
        is C.Term.Type -> C.VTerm.Type(term.id)
    }
}

private fun match(pattern: C.Pattern, term: C.VTerm): State<Normalizer, Boolean> = {
    when {
        pattern is C.Pattern.Var -> {
            !modify { bind(lazyOf(term)) }
            true
        }
        pattern is C.Pattern.UnitOf && term is C.VTerm.UnitOf -> true
        pattern is C.Pattern.BoolOf && term is C.VTerm.BoolOf -> term.value == pattern.value
        pattern is C.Pattern.ByteOf && term is C.VTerm.ByteOf -> term.value == pattern.value
        pattern is C.Pattern.ShortOf && term is C.VTerm.ShortOf -> term.value == pattern.value
        pattern is C.Pattern.IntOf && term is C.VTerm.IntOf -> term.value == pattern.value
        pattern is C.Pattern.LongOf && term is C.VTerm.LongOf -> term.value == pattern.value
        pattern is C.Pattern.FloatOf && term is C.VTerm.FloatOf -> term.value == pattern.value
        pattern is C.Pattern.DoubleOf && term is C.VTerm.DoubleOf -> term.value == pattern.value
        pattern is C.Pattern.StringOf && term is C.VTerm.StringOf -> term.value == pattern.value
        pattern is C.Pattern.ByteArrayOf && term is C.VTerm.ByteArrayOf ->
            if (pattern.elements.size == term.elements.size) {
                !(pattern.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pattern is C.Pattern.IntArrayOf && term is C.VTerm.IntArrayOf ->
            if (pattern.elements.size == term.elements.size) {
                !(pattern.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pattern is C.Pattern.LongArrayOf && term is C.VTerm.LongArrayOf ->
            if (pattern.elements.size == term.elements.size) {
                !(pattern.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pattern is C.Pattern.ListOf && term is C.VTerm.ListOf ->
            if (pattern.elements.size == term.elements.size) {
                !(pattern.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pattern is C.Pattern.CompoundOf && term is C.VTerm.CompoundOf ->
            if (pattern.elements.size == term.elements.size) {
                !(pattern.elements.entries zip term.elements.entries).allM { (pattern, value) ->
                    {
                        !match(pattern.value, value.value.value) && pattern.key.text == value.key.text
                    }
                }
            } else false
        pattern is C.Pattern.BoxOf && term is C.VTerm.BoxOf -> {
            val matched = !match(pattern.content, term.content.value)
            if (matched) {
                !match(pattern.tag, term.tag.value)
            } else false
        }
        pattern is C.Pattern.RefOf && term is C.VTerm.RefOf -> !match(pattern.element, term.element.value)
        pattern is C.Pattern.Refl && term is C.VTerm.Refl -> true
        pattern is C.Pattern.Bool && term is C.VTerm.Bool -> true
        pattern is C.Pattern.Byte && term is C.VTerm.Byte -> true
        pattern is C.Pattern.Short && term is C.VTerm.Short -> true
        pattern is C.Pattern.Int && term is C.VTerm.Int -> true
        pattern is C.Pattern.Long && term is C.VTerm.Long -> true
        pattern is C.Pattern.Float && term is C.VTerm.Float -> true
        pattern is C.Pattern.Double && term is C.VTerm.Double -> true
        pattern is C.Pattern.String && term is C.VTerm.String -> true
        pattern is C.Pattern.ByteArray && term is C.VTerm.ByteArray -> true
        pattern is C.Pattern.IntArray && term is C.VTerm.IntArray -> true
        pattern is C.Pattern.LongArray && term is C.VTerm.LongArray -> true
        else -> false
    }
}

/**
 * Quotes the [term] to a syntactic term under the normalizer.
 */
@Suppress("NAME_SHADOWING")
fun quoteTerm(term: C.VTerm): State<Normalizer, C.Term> = {
    when (val term = !gets { force(term) }) {
        is C.VTerm.Hole -> C.Term.Hole(term.id)
        is C.VTerm.Meta -> C.Term.Meta(term.index, term.id)
        is C.VTerm.Var -> C.Term.Var(term.name, term.level, term.id)
        is C.VTerm.Def -> {
            val arguments = !term.arguments.mapM { quoteTerm(it.value) }
            C.Term.Def(term.name, arguments, term.id)
        }
        is C.VTerm.Match -> {
            val scrutinee = !quoteTerm(term.scrutinee)
            val clauses = !term.clauses.mapM { clause ->
                {
                    clause.first to !quoteTerm(clause.second.value)
                }
            }
            C.Term.Match(scrutinee, clauses, term.id)
        }
        is C.VTerm.UnitOf -> C.Term.UnitOf(term.id)
        is C.VTerm.BoolOf -> C.Term.BoolOf(term.value, term.id)
        is C.VTerm.ByteOf -> C.Term.ByteOf(term.value, term.id)
        is C.VTerm.ShortOf -> C.Term.ShortOf(term.value, term.id)
        is C.VTerm.IntOf -> C.Term.IntOf(term.value, term.id)
        is C.VTerm.LongOf -> C.Term.LongOf(term.value, term.id)
        is C.VTerm.FloatOf -> C.Term.FloatOf(term.value, term.id)
        is C.VTerm.DoubleOf -> C.Term.DoubleOf(term.value, term.id)
        is C.VTerm.StringOf -> C.Term.StringOf(term.value, term.id)
        is C.VTerm.ByteArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            C.Term.ByteArrayOf(elements, term.id)
        }
        is C.VTerm.IntArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            C.Term.IntArrayOf(elements, term.id)
        }
        is C.VTerm.LongArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            C.Term.LongArrayOf(elements, term.id)
        }
        is C.VTerm.ListOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            C.Term.ListOf(elements, term.id)
        }
        is C.VTerm.CompoundOf -> {
            val elements = (!term.elements.entries.mapM { (name, element) -> { name to !quoteTerm(element.value) } }).toLinkedHashMap()
            C.Term.CompoundOf(elements, term.id)
        }
        is C.VTerm.BoxOf -> {
            val content = !quoteTerm(term.content.value)
            val tag = !quoteTerm(term.tag.value)
            C.Term.BoxOf(content, tag, term.id)
        }
        is C.VTerm.RefOf -> {
            val element = !quoteTerm(term.element.value)
            C.Term.RefOf(element, term.id)
        }
        is C.VTerm.Refl -> C.Term.Refl(term.id)
        is C.VTerm.FunOf -> {
            !term.parameters.forEachM { parameter ->
                modify { bind(lazyOf(C.VTerm.Var(parameter.text, size, freshId()))) }
            }
            val body = !normTerm(term.body)
            C.Term.FunOf(term.parameters, body, term.id)
        }
        is C.VTerm.Apply -> {
            val function = !quoteTerm(term.function)
            val arguments = !term.arguments.mapM { quoteTerm(it.value) }
            C.Term.Apply(function, arguments, term.id)
        }
        is C.VTerm.CodeOf -> {
            val element = !quoteTerm(term.element.value)
            C.Term.CodeOf(element, term.id)
        }
        is C.VTerm.Splice -> {
            val element = !quoteTerm(term.element.value)
            C.Term.Splice(element, term.id)
        }
        is C.VTerm.Or -> {
            val variants = !term.variants.mapM { quoteTerm(it.value) }
            C.Term.Or(variants, term.id)
        }
        is C.VTerm.And -> {
            val variants = !term.variants.mapM { quoteTerm(it.value) }
            C.Term.And(variants, term.id)
        }
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
        is C.VTerm.List -> {
            val element = !quoteTerm(term.element.value)
            val size = !quoteTerm(term.size.value)
            C.Term.List(element, size, term.id)
        }
        is C.VTerm.Compound -> C.Term.Compound(term.elements, term.id)
        is C.VTerm.Box -> {
            val content = !quoteTerm(term.content.value)
            C.Term.Box(content, term.id)
        }
        is C.VTerm.Ref -> {
            val element = !quoteTerm(term.element.value)
            C.Term.Ref(element, term.id)
        }
        is C.VTerm.Eq -> {
            val left = !quoteTerm(term.left.value)
            val right = !quoteTerm(term.right.value)
            C.Term.Eq(left, right, term.id)
        }
        is C.VTerm.Fun -> {
            !term.parameters.forEachM { parameter ->
                modify { bind(lazyOf(C.VTerm.Var(parameter.name, size, freshId()))) }
            }
            val resultant = !normTerm(term.resultant)
            C.Term.Fun(term.parameters, resultant, term.effects, term.id)
        }
        is C.VTerm.Code -> {
            val element = !quoteTerm(term.element.value)
            C.Term.Code(element, term.id)
        }
        is C.VTerm.Type -> C.Term.Type(term.id)
    }
}

/**
 * Normalizes the [term] to a term.
 */
fun normTerm(term: C.Term): State<Normalizer, C.Term> = { !quoteTerm(!evalTerm(term)) }

/**
 * Evaluates the [module] to a semantic module.
 */
fun evalModule(module: C.Module): State<Normalizer, C.VModule> = {
    when (module) {
        is C.Module.Var -> {
            val item = !gets { getItem(module.name) } as C.Item.Mod;
            !evalModule(item.body)
        }
        is C.Module.Str -> C.VModule.Str(module.items, module.id)
        is C.Module.Sig -> {
            val signatures = !(module.signatures.mapM { evalSignature(it) })
            C.VModule.Sig(signatures, module.id)
        }
        is C.Module.Type -> C.VModule.Type(module.id)
    }
}

/**
 * Evaluates the [signature] to a semantic signature.
 */
fun evalSignature(signature: C.Signature): State<Normalizer, C.VSignature> = {
    when (signature) {
        is C.Signature.Def -> C.VSignature.Def(signature.name, signature.parameters, signature.resultant, signature.id)
        is C.Signature.Mod -> {
            val type = !evalModule(signature.type)
            C.VSignature.Mod(signature.name, type, signature.id)
        }
        is C.Signature.Test -> C.VSignature.Test(signature.name, signature.id)
    }
}