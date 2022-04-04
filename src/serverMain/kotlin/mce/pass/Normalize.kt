package mce.pass

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.Id
import mce.pass.builtin.builtins
import mce.pass.frontend.elab.*
import mce.util.State
import mce.util.toLinkedHashMap

@Suppress("NOTHING_TO_INLINE")
class Normalizer(
    val values: PersistentList<Lazy<VTerm>>,
    val items: Map<String, Item>,
    val solutions: MutableList<VTerm?>
) {
    inline val size: Int get() = values.size

    inline fun lookup(level: Int): VTerm = values.getOrNull(level)?.value ?: VTerm.Var("", level)

    fun subst(level: Int, term: Lazy<VTerm>): Normalizer = Normalizer(values.set(level, term), items, solutions)

    fun bind(term: Lazy<VTerm>): Normalizer = Normalizer(values + term, items, solutions)

    fun empty(): Normalizer = Normalizer(persistentListOf(), items, solutions)

    /**
     * Returns the solution at the [index].
     */
    fun getSolution(index: Int): VTerm? = solutions[index]

    /**
     * Solves for the meta-variable at the [index] as the [solution].
     */
    fun solve(index: Int, solution: VTerm) {
        solutions[index] = solution
    }

    fun getItem(name: String): Item? = items[name]
}

/**
 * Creates a fresh meta-variable.
 */
fun fresh(id: Id): State<Normalizer, VTerm> = {
    !gets {
        VTerm.Meta(solutions.size, id).also { solutions += null }
    }
}

/**
 * Reveals the head constructor of the [term].
 */
tailrec fun Normalizer.force(term: VTerm): VTerm = when (term) {
    is VTerm.Meta -> when (val forced = getSolution(term.index)) {
        null -> forceSubst(term)
        else -> force(forced)
    }
    else -> forceSubst(term)
}

/**
 * Performs the pending substitution for the [term].
 */
private tailrec fun Normalizer.forceSubst(term: VTerm): VTerm = when (term) {
    is VTerm.Var -> {
        val forced = lookup(term.level)
        when {
            forced is VTerm.Var && term.level != forced.level -> forceSubst(forced)
            else -> forced
        }
    }
    else -> term
}

/**
 * Evaluates the [term] to a semantic term under the normalizer.
 */
fun evalTerm(term: Term): State<Normalizer, VTerm> = {
    when (term) {
        is Term.Hole -> VTerm.Hole(term.id)
        is Term.Meta -> !gets { getSolution(term.index) } ?: VTerm.Meta(term.index, term.id)
        is Term.Var -> !gets { lookup(term.level) }
        is Term.Def -> {
            val item = !gets { getItem(term.name)!! } as Item.Def;
            if (item.modifiers.contains(Modifier.ABSTRACT)) {
                val arguments = !term.arguments.mapM { { lazy { !evalTerm(it) } } }
                VTerm.Def(term.name, arguments, term.id)
            } else {
                !term.arguments.forEachM { argument -> modify { bind(lazy { !evalTerm(argument) }) } }
                if (item.modifiers.contains(Modifier.BUILTIN)) {
                    !gets { with(builtins[term.name]!!) { eval() } }
                } else {
                    !evalTerm(item.body)
                }
            }
        }
        is Term.Let -> {
            !modify { bind(lazy { !evalTerm(term.init) }) }
            !evalTerm(term.body)
        }
        is Term.Match -> {
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
                    VTerm.Match(scrutinee, clauses, term.id)
                }
                else -> {
                    !match(clause.first, scrutinee)
                    !evalTerm(clause.second)
                }
            }
        }
        is Term.UnitOf -> VTerm.UnitOf(term.id)
        is Term.BoolOf -> VTerm.BoolOf(term.value, term.id)
        is Term.ByteOf -> VTerm.ByteOf(term.value, term.id)
        is Term.ShortOf -> VTerm.ShortOf(term.value, term.id)
        is Term.IntOf -> VTerm.IntOf(term.value, term.id)
        is Term.LongOf -> VTerm.LongOf(term.value, term.id)
        is Term.FloatOf -> VTerm.FloatOf(term.value, term.id)
        is Term.DoubleOf -> VTerm.DoubleOf(term.value, term.id)
        is Term.StringOf -> VTerm.StringOf(term.value, term.id)
        is Term.ByteArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            VTerm.ByteArrayOf(elements, term.id)
        }
        is Term.IntArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            VTerm.IntArrayOf(elements, term.id)
        }
        is Term.LongArrayOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            VTerm.LongArrayOf(elements, term.id)
        }
        is Term.ListOf -> {
            val elements = !term.elements.mapM { { lazy { !evalTerm(it) } } }
            VTerm.ListOf(elements, term.id)
        }
        is Term.CompoundOf -> {
            val elements = term.elements.map { (name, element) -> name to lazy { !evalTerm(element) } }.toLinkedHashMap()
            VTerm.CompoundOf(elements, term.id)
        }
        is Term.RefOf -> {
            val element = lazy { !evalTerm(term.element) }
            VTerm.RefOf(element, term.id)
        }
        is Term.Refl -> VTerm.Refl(term.id)
        is Term.FunOf -> VTerm.FunOf(term.params, term.body, term.id)
        is Term.Apply -> {
            val arguments = term.arguments.map { lazy { !evalTerm(it) } }
            when (val function = !evalTerm(term.function)) {
                is VTerm.FunOf -> {
                    !arguments.forEachM { argument -> gets { bind(argument) } }
                    !evalTerm(function.body)
                }
                else -> VTerm.Apply(function, arguments, term.id)
            }
        }
        is Term.CodeOf -> {
            val element = lazy { !evalTerm(term.element) }
            VTerm.CodeOf(element, term.id)
        }
        is Term.Splice -> when (val element = !evalTerm(term.element)) {
            is VTerm.CodeOf -> element.element.value
            else -> VTerm.Splice(lazyOf(element), term.id)
        }
        is Term.Or -> {
            val variants = term.variants.map { lazy { !evalTerm(it) } }
            VTerm.Or(variants, term.id)
        }
        is Term.And -> {
            val variants = term.variants.map { lazy { !evalTerm(it) } }
            VTerm.And(variants, term.id)
        }
        is Term.Unit -> VTerm.Unit(term.id)
        is Term.Bool -> VTerm.Bool(term.id)
        is Term.Byte -> VTerm.Byte(term.id)
        is Term.Short -> VTerm.Short(term.id)
        is Term.Int -> VTerm.Int(term.id)
        is Term.Long -> VTerm.Long(term.id)
        is Term.Float -> VTerm.Float(term.id)
        is Term.Double -> VTerm.Double(term.id)
        is Term.String -> VTerm.String(term.id)
        is Term.ByteArray -> VTerm.ByteArray(term.id)
        is Term.IntArray -> VTerm.IntArray(term.id)
        is Term.LongArray -> VTerm.LongArray(term.id)
        is Term.List -> {
            val element = lazy { !evalTerm(term.element) }
            val size = lazy { !evalTerm(term.size) }
            VTerm.List(element, size, term.id)
        }
        is Term.Compound -> VTerm.Compound(term.elements, term.id)
        is Term.Ref -> {
            val element = lazy { !evalTerm(term.element) }
            VTerm.Ref(element, term.id)
        }
        is Term.Eq -> {
            val left = lazy { !evalTerm(term.left) }
            val right = lazy { !evalTerm(term.right) }
            VTerm.Eq(left, right, term.id)
        }
        is Term.Fun -> VTerm.Fun(term.params, term.resultant, term.effs, term.id)
        is Term.Code -> {
            val element = lazy { !evalTerm(term.element) }
            VTerm.Code(element, term.id)
        }
        is Term.Type -> VTerm.Type(term.id)
    }
}

/**
 * Checks if the [pat] matches the [term], binding the matched terms to the normalizer.
 */
private fun match(pat: Pat, term: VTerm): State<Normalizer, Boolean> = {
    when {
        pat is Pat.Var -> {
            !modify { bind(lazyOf(term)) }
            true
        }
        pat is Pat.UnitOf && term is VTerm.UnitOf -> true
        pat is Pat.BoolOf && term is VTerm.BoolOf -> term.value == pat.value
        pat is Pat.ByteOf && term is VTerm.ByteOf -> term.value == pat.value
        pat is Pat.ShortOf && term is VTerm.ShortOf -> term.value == pat.value
        pat is Pat.IntOf && term is VTerm.IntOf -> term.value == pat.value
        pat is Pat.LongOf && term is VTerm.LongOf -> term.value == pat.value
        pat is Pat.FloatOf && term is VTerm.FloatOf -> term.value == pat.value
        pat is Pat.DoubleOf && term is VTerm.DoubleOf -> term.value == pat.value
        pat is Pat.StringOf && term is VTerm.StringOf -> term.value == pat.value
        pat is Pat.ByteArrayOf && term is VTerm.ByteArrayOf ->
            if (pat.elements.size == term.elements.size) {
                !(pat.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pat is Pat.IntArrayOf && term is VTerm.IntArrayOf ->
            if (pat.elements.size == term.elements.size) {
                !(pat.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pat is Pat.LongArrayOf && term is VTerm.LongArrayOf ->
            if (pat.elements.size == term.elements.size) {
                !(pat.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pat is Pat.ListOf && term is VTerm.ListOf ->
            if (pat.elements.size == term.elements.size) {
                !(pat.elements zip term.elements).allM { (pattern, value) -> match(pattern, value.value) }
            } else false
        pat is Pat.CompoundOf && term is VTerm.CompoundOf ->
            if (pat.elements.size == term.elements.size) {
                !(pat.elements.entries zip term.elements.entries).allM { (pattern, value) ->
                    {
                        !match(pattern.value, value.value.value) && pattern.key.text == value.key.text
                    }
                }
            } else false
        pat is Pat.RefOf && term is VTerm.RefOf -> !match(pat.element, term.element.value)
        pat is Pat.Refl && term is VTerm.Refl -> true
        // TODO: Or
        // TODO: And
        pat is Pat.Unit && term is VTerm.Unit -> true
        pat is Pat.Bool && term is VTerm.Bool -> true
        pat is Pat.Byte && term is VTerm.Byte -> true
        pat is Pat.Short && term is VTerm.Short -> true
        pat is Pat.Int && term is VTerm.Int -> true
        pat is Pat.Long && term is VTerm.Long -> true
        pat is Pat.Float && term is VTerm.Float -> true
        pat is Pat.Double && term is VTerm.Double -> true
        pat is Pat.String && term is VTerm.String -> true
        pat is Pat.ByteArray && term is VTerm.ByteArray -> true
        pat is Pat.IntArray && term is VTerm.IntArray -> true
        pat is Pat.LongArray && term is VTerm.LongArray -> true
        pat is Pat.List && term is VTerm.List -> !match(pat.element, term.element.value) && !match(pat.size, term.size.value)
        pat is Pat.Compound && term is VTerm.Compound -> TODO()
        pat is Pat.Ref && term is VTerm.Ref -> !match(pat.element, term.element.value)
        pat is Pat.Eq && term is VTerm.Eq -> !match(pat.left, term.left.value) && !match(pat.right, term.right.value)
        pat is Pat.Code && term is VTerm.Code -> !match(pat.element, term.element.value)
        pat is Pat.Type && term is VTerm.Type -> true
        else -> false
    }
}

/**
 * Quotes the [term] to a syntactic term under the normalizer.
 */
@Suppress("NAME_SHADOWING")
fun quoteTerm(term: VTerm): State<Normalizer, Term> = {
    when (val term = !gets { force(term) }) {
        is VTerm.Hole -> Term.Hole(term.id)
        is VTerm.Meta -> Term.Meta(term.index, term.id)
        is VTerm.Var -> Term.Var(term.name, term.level, term.id)
        is VTerm.Def -> {
            val arguments = !term.arguments.mapM { quoteTerm(it.value) }
            Term.Def(term.name, arguments, term.id)
        }
        is VTerm.Match -> {
            val scrutinee = !quoteTerm(term.scrutinee)
            val clauses = !term.clauses.mapM { clause ->
                {
                    clause.first to !quoteTerm(clause.second.value)
                }
            }
            Term.Match(scrutinee, clauses, term.id)
        }
        is VTerm.UnitOf -> Term.UnitOf(term.id)
        is VTerm.BoolOf -> Term.BoolOf(term.value, term.id)
        is VTerm.ByteOf -> Term.ByteOf(term.value, term.id)
        is VTerm.ShortOf -> Term.ShortOf(term.value, term.id)
        is VTerm.IntOf -> Term.IntOf(term.value, term.id)
        is VTerm.LongOf -> Term.LongOf(term.value, term.id)
        is VTerm.FloatOf -> Term.FloatOf(term.value, term.id)
        is VTerm.DoubleOf -> Term.DoubleOf(term.value, term.id)
        is VTerm.StringOf -> Term.StringOf(term.value, term.id)
        is VTerm.ByteArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            Term.ByteArrayOf(elements, term.id)
        }
        is VTerm.IntArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            Term.IntArrayOf(elements, term.id)
        }
        is VTerm.LongArrayOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            Term.LongArrayOf(elements, term.id)
        }
        is VTerm.ListOf -> {
            val elements = !term.elements.mapM { quoteTerm(it.value) }
            Term.ListOf(elements, term.id)
        }
        is VTerm.CompoundOf -> {
            val elements = (!term.elements.entries.mapM { (name, element) -> { name to !quoteTerm(element.value) } }).toLinkedHashMap()
            Term.CompoundOf(elements, term.id)
        }
        is VTerm.RefOf -> {
            val element = !quoteTerm(term.element.value)
            Term.RefOf(element, term.id)
        }
        is VTerm.Refl -> Term.Refl(term.id)
        is VTerm.FunOf -> {
            !term.params.forEachM { parameter ->
                modify { bind(lazyOf(VTerm.Var(parameter.text, size, freshId()))) }
            }
            val body = !normTerm(term.body)
            Term.FunOf(term.params, body, term.id)
        }
        is VTerm.Apply -> {
            val function = !quoteTerm(term.function)
            val arguments = !term.arguments.mapM { quoteTerm(it.value) }
            Term.Apply(function, arguments, term.id)
        }
        is VTerm.CodeOf -> {
            val element = !quoteTerm(term.element.value)
            Term.CodeOf(element, term.id)
        }
        is VTerm.Splice -> {
            val element = !quoteTerm(term.element.value)
            Term.Splice(element, term.id)
        }
        is VTerm.Or -> {
            val variants = !term.variants.mapM { quoteTerm(it.value) }
            Term.Or(variants, term.id)
        }
        is VTerm.And -> {
            val variants = !term.variants.mapM { quoteTerm(it.value) }
            Term.And(variants, term.id)
        }
        is VTerm.Unit -> Term.Unit(term.id)
        is VTerm.Bool -> Term.Bool(term.id)
        is VTerm.Byte -> Term.Byte(term.id)
        is VTerm.Short -> Term.Short(term.id)
        is VTerm.Int -> Term.Int(term.id)
        is VTerm.Long -> Term.Long(term.id)
        is VTerm.Float -> Term.Float(term.id)
        is VTerm.Double -> Term.Double(term.id)
        is VTerm.String -> Term.String(term.id)
        is VTerm.ByteArray -> Term.ByteArray(term.id)
        is VTerm.IntArray -> Term.IntArray(term.id)
        is VTerm.LongArray -> Term.LongArray(term.id)
        is VTerm.List -> {
            val element = !quoteTerm(term.element.value)
            val size = !quoteTerm(term.size.value)
            Term.List(element, size, term.id)
        }
        is VTerm.Compound -> Term.Compound(term.elements, term.id)
        is VTerm.Ref -> {
            val element = !quoteTerm(term.element.value)
            Term.Ref(element, term.id)
        }
        is VTerm.Eq -> {
            val left = !quoteTerm(term.left.value)
            val right = !quoteTerm(term.right.value)
            Term.Eq(left, right, term.id)
        }
        is VTerm.Fun -> {
            !term.params.forEachM { parameter ->
                modify { bind(lazyOf(VTerm.Var(parameter.name, size, freshId()))) }
            }
            val resultant = !normTerm(term.resultant)
            Term.Fun(term.params, resultant, term.effs, term.id)
        }
        is VTerm.Code -> {
            val element = !quoteTerm(term.element.value)
            Term.Code(element, term.id)
        }
        is VTerm.Type -> Term.Type(term.id)
    }
}

/**
 * Normalizes the [term] to a term.
 */
fun normTerm(term: Term): State<Normalizer, Term> = { !quoteTerm(!evalTerm(term)) }

/**
 * Evaluates the [module] to a semantic module.
 */
fun evalModule(module: Module): State<Normalizer, VModule> = {
    when (module) {
        is Module.Var -> {
            val item = !gets { getItem(module.name) } as Item.Mod;
            !evalModule(item.body)
        }
        is Module.Str -> VModule.Str(module.items, module.id)
        is Module.Sig -> {
            val signatures = !(module.signatures.mapM { evalSignature(it) })
            VModule.Sig(signatures, module.id)
        }
        is Module.Type -> VModule.Type(module.id)
    }
}

/**
 * Evaluates the [signature] to a semantic signature.
 */
fun evalSignature(signature: Signature): State<Normalizer, VSignature> = {
    when (signature) {
        is Signature.Def -> VSignature.Def(signature.name, signature.params, signature.resultant, signature.id)
        is Signature.Mod -> {
            val type = !evalModule(signature.type)
            VSignature.Mod(signature.name, type, signature.id)
        }
        is Signature.Test -> VSignature.Test(signature.name, signature.id)
    }
}
