package mce.pass.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.Id
import mce.ast.Name
import mce.pass.*
import mce.util.Store
import mce.util.toLinkedHashMap
import mce.ast.core.Eff as CEff
import mce.ast.core.Item as CItem
import mce.ast.core.Modifier as CModifier
import mce.ast.core.Module as CModule
import mce.ast.core.Param as CParam
import mce.ast.core.Pat as CPat
import mce.ast.core.Signature as CSignature
import mce.ast.core.Term as CTerm
import mce.ast.core.VModule as CVModule
import mce.ast.core.VSignature as CVSignature
import mce.ast.core.VTerm as CVTerm
import mce.ast.surface.Eff as SEff
import mce.ast.surface.Item as SItem
import mce.ast.surface.Modifier as SModifier
import mce.ast.surface.Module as SModule
import mce.ast.surface.Param as SParam
import mce.ast.surface.Pat as SPat
import mce.ast.surface.Signature as SSignature
import mce.ast.surface.Term as STerm

// TODO: check/synth effects and stages
@Suppress("NAME_SHADOWING")
class Elab private constructor(
    private val items: Map<String, CItem>,
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val completions: MutableMap<Id, List<Pair<String, CVTerm>>> = mutableMapOf()
    private val types: MutableMap<Id, CVTerm> = mutableMapOf()
    private val solutions: MutableList<CVTerm?> = mutableListOf()

    /**
     * Infers the signature of the [item].
     */
    private fun Store<Context>.inferItem(item: SItem): Pair<CItem, CVSignature> {
        val modifiers = item.modifiers.mapTo(mutableSetOf()) { elabModifier(it) }
        return when (item) {
            is SItem.Def -> {
                value = value.copy(static = item.modifiers.contains(SModifier.STATIC), termRelevant = false, typeRelevant = true)
                val params = bindParams(item.params)
                val resultant = checkTerm(item.resultant, TYPE)
                val vResultant = Store(value.normalizer).evalTerm(resultant)
                val effs = item.effs.map { elabEff(it) }.toSet()
                val body = if (item.modifiers.contains(SModifier.BUILTIN)) {
                    types[item.body.id] = vResultant
                    CTerm.Builtin(item.body.id)
                } else {
                    value = value.copy(termRelevant = true, typeRelevant = false)
                    checkTerm(item.body, vResultant)
                }
                value.checkPhase(item.body.id, vResultant)
                CItem.Def(modifiers, item.name, params, resultant, effs, body, item.id) to CVSignature.Def(item.name, params, resultant, null)
            }
            is SItem.Mod -> {
                val type = checkModule(item.type, CVModule.Type(null))
                val vType = value.normalizer.evalModule(type)
                val body = checkModule(item.body, vType)
                CItem.Mod(modifiers, item.name, vType, body, item.id) to CVSignature.Mod(item.name, vType, null)
            }
            is SItem.Test -> {
                val body = checkTerm(item.body, BOOL)
                CItem.Test(modifiers, item.name, body, item.id) to CVSignature.Test(item.name, null)
            }
            is SItem.Pack -> {
                val body = checkTerm(item.body, ANY) // TODO: type
                CItem.Pack(body, item.id) to CVSignature.Pack(null)
            }
            is SItem.Advancement -> {
                value = value.copy(static = true)
                val body = checkTerm(item.body, ANY) // TODO: type
                CItem.Advancement(modifiers, item.name, body, item.id) to CVSignature.Advancement(item.name, null)
            }
        }
    }

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun Store<Context>.checkItem(item: SItem, signature: CVSignature): CItem {
        val (inferred, inferredSignature) = inferItem(item)
        if (!(Store(Normalizer(persistentListOf(), items, solutions)).unifySignatures(inferredSignature, signature))) {
            diagnose(Diagnostic.SigMismatch(item.id)) // TODO: serialize signatures
        }
        return inferred
    }

    /**
     * Elaborates the [modifier].
     */
    private fun elabModifier(modifier: SModifier): CModifier = when (modifier) {
        SModifier.ABSTRACT -> CModifier.ABSTRACT
        SModifier.BUILTIN -> CModifier.BUILTIN
        SModifier.STATIC -> CModifier.STATIC
    }

    /**
     * Binds the [params] to the context.
     */
    private fun Store<Context>.bindParams(params: List<SParam>): List<CParam> =
        params.map { param ->
            val type = checkTerm(param.type, TYPE)
            val vType = Store(value.normalizer).evalTerm(type)
            val lower = param.lower?.let { checkTerm(it, vType) }
            val vLower = lower?.let { Store(value.normalizer).evalTerm(it) }
            val upper = param.upper?.let { checkTerm(it, vType) }
            val vUpper = upper?.let { Store(value.normalizer).evalTerm(it) }
            value = value.bind(param.id, Entry(param.termRelevant, param.name, vLower, vUpper, param.typeRelevant, vType, value.stage))
            CParam(param.termRelevant, param.name, lower, upper, param.typeRelevant, type, param.id)
        }

    /**
     * Infers the type of the [module] under the context.
     */
    private fun Store<Context>.inferModule(module: SModule): Pair<CModule, CVModule> =
        when (module) {
            is SModule.Var -> when (val item = items[module.name]) {
                is CItem.Mod -> CModule.Var(module.name, module.id) to item.type
                else -> {
                    diagnose(Diagnostic.ModNotFound(module.name, module.id))
                    CModule.Var(module.name, module.id) to TODO()
                }
            }
            is SModule.Str -> {
                val (items, signatures) = module.items.map { inferItem(it) }.unzip()
                CModule.Str(items, module.id) to CVModule.Sig(signatures, null)
            }
            is SModule.Sig -> {
                val signatures = module.signatures.map { elabSignature(it) }
                CModule.Sig(signatures, module.id) to CVModule.Type(null)
            }
            is SModule.Type -> CModule.Type(module.id) to CVModule.Type(null)
        }

    /**
     * Checks the [module] against the [type] under the context.
     */
    private fun Store<Context>.checkModule(module: SModule, type: CVModule): CModule =
        when {
            module is SModule.Str && type is CVModule.Sig -> {
                val items = (module.items zip type.signatures).map { (item, signature) -> checkItem(item, signature) }
                CModule.Str(items, module.id)
            }
            else -> {
                val (inferred, inferredType) = inferModule(module)
                if (!(Store(value.normalizer).unifyModules(inferredType, type))) {
                    diagnose(Diagnostic.ModMismatch(module.id))
                }
                inferred
            }
        }

    /**
     * Checks if the [module1] and the [module2] can be unified under the normalizer.
     */
    private fun Store<Normalizer>.unifyModules(module1: CVModule, module2: CVModule): Boolean =
        when (module1) {
            is CVModule.Var ->
                module2 is CVModule.Var &&
                        module1.name == module2.name
            is CVModule.Sig ->
                module2 is CVModule.Sig &&
                        (module1.signatures zip module2.signatures).all { (signature1, signature2) -> unifySignatures(signature1, signature2) }
            is CVModule.Str -> false // TODO
            is CVModule.Type -> module2 is CVModule.Type
        }

    /**
     * Checks if the [signature1] and the [signature2] can be unified under the normalizer.
     */
    private fun Store<Normalizer>.unifySignatures(signature1: CVSignature, signature2: CVSignature): Boolean =
        when (signature1) {
            is CVSignature.Def ->
                signature2 is CVSignature.Def &&
                        signature1.name == signature2.name &&
                        signature1.params.size == signature2.params.size &&
                        (signature1.params zip signature2.params).all { (param1, param2) ->
                            unifyTerms(evalTerm(param2.type), evalTerm(param1.type)).also {
                                value = value.bind(lazyOf(CVTerm.Var("", value.size)))
                            }
                        } &&
                        unifyTerms(evalTerm(signature1.resultant), evalTerm(signature2.resultant))
            is CVSignature.Mod ->
                signature2 is CVSignature.Mod &&
                        signature1.name == signature2.name &&
                        unifyModules(signature1.type, signature2.type)
            is CVSignature.Test ->
                signature2 is CVSignature.Test &&
                        signature1.name == signature2.name
            is CVSignature.Pack ->
                signature2 is CVSignature.Pack
            is CVSignature.Advancement ->
                signature2 is CVSignature.Advancement &&
                        signature1.name == signature2.name
        }

    /**
     * Elaborates the [signature] under the context.
     */
    private fun Store<Context>.elabSignature(signature: SSignature): CSignature =
        when (signature) {
            is SSignature.Def ->
                restore {
                    value = value.copy(termRelevant = false, typeRelevant = true)
                    val params = bindParams(signature.params)
                    val resultant = checkTerm(signature.resultant, TYPE)
                    CSignature.Def(signature.name, params, resultant, signature.id)
                }
            is SSignature.Mod -> {
                val type = checkModule(signature.type, CVModule.Type(null))
                CSignature.Mod(signature.name, type, signature.id)
            }
            is SSignature.Test -> CSignature.Test(signature.name, signature.id)
        }

    /**
     * Infers the type of the [term] under the context.
     */
    private fun Store<Context>.inferTerm(term: STerm): Typing =
        when (term) {
            is STerm.Hole -> {
                completions[term.id] = value.entries.map { it.name to it.type }
                Typing(CTerm.Hole(term.id), diagnose(Diagnostic.TermExpected(printTerm(Store(value.normalizer).quoteTerm(END)), term.id)))
            }
            is STerm.Meta -> {
                val type = value.normalizer.fresh(term.id)
                val term = checkTerm(term, type)
                Typing(term, type)
            }
            is STerm.Command -> {
                restore {
                    value = value.copy(static = true)
                    val body = checkTerm(term.body, STRING)
                    Typing(CTerm.Command(body, term.id), END)
                }
            }
            is STerm.Anno ->
                restore {
                    value = value.copy(termRelevant = false, typeRelevant = true)
                    val type = Store(value.normalizer).evalTerm(checkTerm(term.type, TYPE))
                    val term = checkTerm(term.element, type)
                    Typing(term, type)
                }
            is STerm.Var -> {
                val level = value.lookup(term.name)
                val type = when (level) {
                    -1 -> diagnose(Diagnostic.VarNotFound(term.name, term.id))
                    else -> {
                        val entry = value.entries[level]
                        var type = entry.type
                        if (value.stage != entry.stage) type = diagnose(Diagnostic.StageMismatch(value.stage, entry.stage, term.id))
                        if (value.termRelevant && !entry.termRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                        if (value.typeRelevant && !entry.typeRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                        Store(value.normalizer).checkRepr(term.id, type)
                        type
                    }
                }
                Typing(CTerm.Var(term.name, level /* TODO: avoid [IndexOutOfBoundsException] */, term.id), type)
            }
            is STerm.UnitOf -> Typing(CTerm.UnitOf(term.id), UNIT)
            is STerm.BoolOf -> Typing(CTerm.BoolOf(term.value, term.id), BOOL)
            is STerm.ByteOf -> Typing(CTerm.ByteOf(term.value, term.id), BYTE)
            is STerm.ShortOf -> Typing(CTerm.ShortOf(term.value, term.id), SHORT)
            is STerm.IntOf -> Typing(CTerm.IntOf(term.value, term.id), INT)
            is STerm.LongOf -> Typing(CTerm.LongOf(term.value, term.id), LONG)
            is STerm.FloatOf -> Typing(CTerm.FloatOf(term.value, term.id), FLOAT)
            is STerm.DoubleOf -> Typing(CTerm.DoubleOf(term.value, term.id), DOUBLE)
            is STerm.StringOf -> Typing(CTerm.StringOf(term.value, term.id), STRING)
            is STerm.ByteArrayOf -> {
                val elements = term.elements.map { checkTerm(it, BYTE) }
                Typing(CTerm.ByteArrayOf(elements, term.id), BYTE_ARRAY)
            }
            is STerm.IntArrayOf -> {
                val elements = term.elements.map { checkTerm(it, INT) }
                Typing(CTerm.IntArrayOf(elements, term.id), INT_ARRAY)
            }
            is STerm.LongArrayOf -> {
                val elements = term.elements.map { checkTerm(it, LONG) }
                Typing(CTerm.LongArrayOf(elements, term.id), LONG_ARRAY)
            }
            is STerm.ListOf -> if (term.elements.isEmpty()) {
                Typing(CTerm.ListOf(emptyList(), term.id), CVTerm.List(lazyOf(END), lazyOf(CVTerm.IntOf(0))))
            } else { // TODO: use union of element types
                val head = inferTerm(term.elements.first())
                val tail = term.elements.drop(1).map { checkTerm(it, head.type) }
                val elements = listOf(head.term) + tail
                val size = CVTerm.IntOf(elements.size)
                Typing(CTerm.ListOf(elements, term.id), CVTerm.List(lazyOf(head.type), lazyOf(size)))
            }
            is STerm.CompoundOf -> {
                val (elementTerms, elementTypes) = term.elements.map { entry ->
                    val element = inferTerm(entry.element)
                    CTerm.CompoundOf.Entry(entry.name, element.term) to CVTerm.Compound.Entry(true, entry.name, lazyOf(element.type), null)
                }.unzip()
                Typing(CTerm.CompoundOf(elementTerms, term.id), CVTerm.Compound(elementTypes.map { it.name.text to it }.toLinkedHashMap()))
            }
            is STerm.TupleOf -> {
                val elements = term.elements.map { element -> inferTerm(element) }
                Typing(
                    CTerm.TupleOf(elements.map { it.term }, term.id),
                    CVTerm.Tuple(elements.map { CTerm.Tuple.Entry(true, Name("", freshId()), Store(value.normalizer).quoteTerm(it.type), null) })
                )
            }
            is STerm.RefOf -> {
                val element = inferTerm(term.element)
                Typing(CTerm.RefOf(element.term, term.id), CVTerm.Ref(lazyOf(element.type)))
            }
            is STerm.Refl -> {
                val left = lazy { value.normalizer.fresh(term.id) }
                Typing(CTerm.Refl(term.id), CVTerm.Eq(left, left))
            }
            is STerm.CodeOf ->
                restore {
                    value = value.copy(stage = value.stage + 1)
                    val element = inferTerm(term.element)
                    Typing(CTerm.CodeOf(element.term, term.id), CVTerm.Code(lazyOf(element.type)))
                }
            is STerm.Splice ->
                restore {
                    value = value.copy(stage = value.stage - 1)
                    val element = inferTerm(term.element)
                    val type = when (val type = value.normalizer.force(element.type)) {
                        is CVTerm.Code -> type.element.value
                        else -> type.also {
                            Store(value.normalizer).unifyTerms(it, CVTerm.Code(lazyOf(value.normalizer.fresh(freshId()))))
                        }
                    }
                    Typing(CTerm.Splice(element.term, term.id), type)
                }
            is STerm.Or -> {
                val variants = term.variants.map { checkTerm(it, TYPE) }
                Typing(CTerm.Or(variants, term.id), TYPE)
            }
            is STerm.And -> {
                val variants = term.variants.map { checkTerm(it, TYPE) }
                Typing(CTerm.And(variants, term.id), TYPE)
            }
            is STerm.Unit -> Typing(CTerm.Unit(term.id), TYPE)
            is STerm.Bool -> Typing(CTerm.Bool(term.id), TYPE)
            is STerm.Byte -> Typing(CTerm.Byte(term.id), TYPE)
            is STerm.Short -> Typing(CTerm.Short(term.id), TYPE)
            is STerm.Int -> Typing(CTerm.Int(term.id), TYPE)
            is STerm.Long -> Typing(CTerm.Long(term.id), TYPE)
            is STerm.Float -> Typing(CTerm.Float(term.id), TYPE)
            is STerm.Double -> Typing(CTerm.Double(term.id), TYPE)
            is STerm.String -> Typing(CTerm.String(term.id), TYPE)
            is STerm.ByteArray -> Typing(CTerm.ByteArray(term.id), TYPE)
            is STerm.IntArray -> Typing(CTerm.IntArray(term.id), TYPE)
            is STerm.LongArray -> Typing(CTerm.LongArray(term.id), TYPE)
            is STerm.List -> {
                val element = checkTerm(term.element, TYPE)
                restore {
                    value = value.copy(termRelevant = false, typeRelevant = true)
                    val size = checkTerm(term.size, INT)
                    Typing(CTerm.List(element, size, term.id), TYPE)
                }
            }
            is STerm.Compound -> {
                val elements = term.elements.map { entry ->
                    val element = checkTerm(entry.type, TYPE)
                    CTerm.Compound.Entry(entry.relevant, entry.name, element, entry.id)
                }
                Typing(CTerm.Compound(elements, term.id), TYPE)
            }
            is STerm.Tuple ->
                restore {
                    val elements = term.elements.map { entry ->
                        val element = checkTerm(entry.type, TYPE)
                        value = value.bind(entry.id, Entry(false, entry.name.text, END, ANY, true, Store(value.normalizer).evalTerm(element), value.stage))
                        CTerm.Tuple.Entry(entry.relevant, entry.name, element, entry.id)
                    }
                    Typing(CTerm.Tuple(elements, term.id), TYPE)
                }
            is STerm.Ref -> {
                val element = checkTerm(term.element, TYPE)
                Typing(CTerm.Ref(element, term.id), TYPE)
            }
            is STerm.Eq -> {
                val left = inferTerm(term.left)
                val right = checkTerm(term.right, left.type)
                Typing(CTerm.Eq(left.term, right, term.id), TYPE)
            }
            is STerm.Code -> {
                val element = checkTerm(term.element, TYPE)
                Typing(CTerm.Code(element, term.id), TYPE)
            }
            is STerm.Type -> Typing(CTerm.Type(term.id), TYPE)
            else -> {
                val computation = inferComputation(term)
                if (computation.effs.isNotEmpty()) {
                    diagnose(Diagnostic.EffMismatch(emptyList(), computation.effs.map { printEff(it) }, term.id))
                }
                computation
            }
        }.also {
            types[term.id] = it.type
        }

    /**
     * Infers the type of the [computation] under the context.
     */
    private fun Store<Context>.inferComputation(computation: STerm): Typing =
        when (computation) {
            is STerm.Block ->
                restore {
                    val elements = computation.elements.map { inferComputation(it) }
                    val type = elements.lastOrNull()?.type ?: UNIT
                    Typing(CTerm.Block(elements.map { it.term }, computation.id), type)
                }
            is STerm.Def -> when (val item = items[computation.name]) {
                is CItem.Def -> {
                    if (item.params.size != computation.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(item.params.size, computation.arguments.size, computation.id))
                    }
                    Store(value.normalizer).run {
                        val arguments = (computation.arguments zip item.params).map { (argument, param) ->
                            val id = argument.id
                            val argument = checkTerm(argument, evalTerm(param.type))
                            val vArgument = evalTerm(argument)
                            param.lower?.let { evalTerm(it) }?.also { lower ->
                                if (!subtypeTerms(lower, vArgument)) {
                                    diagnose(Diagnostic.TermMismatch(printTerm(quoteTerm(vArgument)), printTerm(quoteTerm(lower)), id))
                                }
                            }
                            param.upper?.let { evalTerm(it) }?.also { upper ->
                                if (!subtypeTerms(vArgument, upper)) {
                                    diagnose(Diagnostic.TermMismatch(printTerm(quoteTerm(upper)), printTerm(quoteTerm(vArgument)), id))
                                }
                            }
                            value = value.bind(lazyOf(vArgument))
                            argument
                        }
                        val resultant = evalTerm(item.resultant)
                        Typing(CTerm.Def(computation.name, arguments, computation.id), resultant, item.effs)
                    }
                }
                else -> Typing(CTerm.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id)))
            }
            is STerm.Let -> {
                val init = inferComputation(computation.init)
                value = value.bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, true, init.type, value.stage))
                Typing(CTerm.Let(computation.name, init.term, computation.id), UNIT, init.effs)
            }
            is STerm.Match -> {
                val type = value.normalizer.fresh(computation.id) // TODO: use union of element types
                val scrutinee = inferTerm(computation.scrutinee)
                val clauses = computation.clauses.map { (pat, body) ->
                    restore {
                        val pat = checkPat(pat, scrutinee.type)
                        val body = checkTerm(body, type) /* TODO: effect */
                        pat to body
                    }
                }
                checkExhaustiveness(clauses.map { it.first }, scrutinee.type, computation.id)
                Typing(CTerm.Match(scrutinee.term, clauses, computation.id), type)
            }
            is STerm.FunOf -> {
                val types = computation.params.map { value.normalizer.fresh(computation.id) }
                restore {
                    (computation.params zip types).forEach { (param, type) ->
                        this@Elab.types[param.id] = type
                        value = value.bind(param.id, Entry(true /* TODO */, param.text, END, ANY, true, type, value.stage))
                    }
                    val body = inferComputation(computation.body)
                    Store(value.normalizer).run {
                        val params = (computation.params zip types).map { (param, type) ->
                            CParam(true /* TODO */, param.text, quoteTerm(END), quoteTerm(ANY), true, quoteTerm(type), param.id)
                        }
                        Typing(CTerm.FunOf(computation.params, body.term, computation.id), CVTerm.Fun(params, quoteTerm(body.type), body.effs))
                    }
                }
            }
            is STerm.Apply -> {
                val function = inferComputation(computation.function)
                val type = Store(value.normalizer).run {
                    when (val type = value.force(function.type)) {
                        is CVTerm.Fun -> type
                        else -> {
                            val params = computation.arguments.map {
                                CParam(true, "", null, null, true, quoteTerm(value.fresh(freshId())), freshId())
                            }
                            val vResultant = value.fresh(freshId())
                            val resultant = quoteTerm(vResultant)
                            val effs = emptySet<CEff>() // TODO
                            unifyTerms(type, CVTerm.Fun(params, resultant, effs, null))
                            CVTerm.Fun(params, resultant, effs, type.id)
                        }
                    }
                }
                if (type.params.size != computation.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, computation.arguments.size, computation.id))
                }
                Store(value.normalizer).run {
                    val arguments = (computation.arguments zip type.params).map { (argument, param) ->
                        val tArgument = checkTerm(argument, evalTerm(param.type))
                        val vArgument = evalTerm(tArgument)
                        param.lower?.let { evalTerm(it) }?.also { lower ->
                            if (!subtypeTerms(lower, vArgument)) {
                                diagnose(Diagnostic.TermMismatch(printTerm(quoteTerm(vArgument)), printTerm(quoteTerm(lower)), argument.id))
                            }
                        }
                        param.upper?.let { evalTerm(it) }?.also { upper ->
                            if (!subtypeTerms(vArgument, upper)) {
                                diagnose(Diagnostic.TermMismatch(printTerm(quoteTerm(upper)), printTerm(quoteTerm(vArgument)), argument.id))
                            }
                        }
                        value = value.bind(lazyOf(vArgument))
                        tArgument
                    }
                    val resultant = evalTerm(type.resultant)
                    Typing(CTerm.Apply(function.term, arguments, computation.id), resultant, type.effs)
                }
            }
            is STerm.Fun ->
                restore {
                    val params = bindParams(computation.params)
                    val resultant = checkTerm(computation.resultant, TYPE) /* TODO: check effects */
                    val effs = computation.effs.map { elabEff(it) }.toSet()
                    Typing(CTerm.Fun(params, resultant, effs, computation.id), TYPE)
                }
            else -> inferTerm(computation)
        }

    /**
     * Checks the [term] against the [type] under the context.
     */
    private fun Store<Context>.checkTerm(term: STerm, type: CVTerm): CTerm {
        val type = value.normalizer.force(type).also {
            types[term.id] = it
        }
        return when {
            term is STerm.Hole -> {
                completions[term.id] = value.entries.map { it.name to it.type }
                diagnose(Diagnostic.TermExpected(printTerm(Store(value.normalizer).quoteTerm(type)), term.id))
                CTerm.Hole(term.id)
            }
            term is STerm.Meta ->
                Store(value.normalizer).quoteTerm(value.normalizer.fresh(term.id))
            term is STerm.ListOf && type is CVTerm.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value) }
                when (val size = value.normalizer.force(type.size.value)) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                    else -> {}
                }
                CTerm.ListOf(elements, term.id)
            }
            term is STerm.CompoundOf && type is CVTerm.Compound -> {
                if (type.elements.size != term.elements.size) {
                    diagnose(Diagnostic.SizeMismatch(type.elements.size, term.elements.size, term.id))
                }
                val elements = (term.elements zip type.elements.values).map { (element, entry) ->
                    val tElement = checkTerm(element.element, entry.type.value)
                    CTerm.CompoundOf.Entry(element.name, tElement)
                }
                CTerm.CompoundOf(elements, term.id)
            }
            term is STerm.TupleOf && type is CVTerm.Tuple ->
                restore {
                    val elements = (term.elements zip type.elements).map { (element, entry) ->
                        val vType = Store(value.normalizer).evalTerm(entry.type)
                        val tElement = checkTerm(element, vType)
                        val vElement = Store(value.normalizer).evalTerm(tElement)
                        value = value.bindUnchecked(Entry(entry.relevant, "", END, ANY, true, vType, value.stage), vElement)
                        tElement
                    }
                    CTerm.TupleOf(elements, term.id)
                }
            term is STerm.RefOf && type is CVTerm.Ref -> {
                val element = checkTerm(term.element, type.element.value)
                CTerm.RefOf(element, term.id)
            }
            term is STerm.CodeOf && type is CVTerm.Code ->
                restore {
                    value = value.copy(stage = value.stage + 1)
                    val element = checkTerm(term.element, type.element.value)
                    CTerm.CodeOf(element, term.id)
                }
            else -> {
                val computation = checkComputation(term, type, emptySet())
                computation.term
            }
        }
    }

    /**
     * Checks the [computation] against the [type] and the [effs] under the context.
     */
    private fun Store<Context>.checkComputation(computation: STerm, type: CVTerm, effs: Set<CEff>): Effecting {
        val type = value.normalizer.force(type).also {
            types[computation.id] = it
        }
        return when {
            computation is STerm.Block ->
                restore {
                    val elements = computation.elements.map { inferComputation(it) }
                    val effs = elements.flatMap { it.effs }.toSet()
                    Effecting(CTerm.Block(elements.map { it.term }, computation.id), effs)
                }
            computation is STerm.Let -> {
                val init = inferComputation(computation.init)
                value = value.bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, true, init.type, value.stage))
                Effecting(CTerm.Let(computation.name, init.term, computation.id), init.effs)
            }
            computation is STerm.Match -> {
                val scrutinee = inferTerm(computation.scrutinee)
                val clauses = computation.clauses.map { (pat, body) ->
                    restore {
                        val pat = checkPat(pat, scrutinee.type)
                        val body = checkComputation(body, type, effs)
                        pat to body
                    }
                }
                checkExhaustiveness(clauses.map { it.first }, scrutinee.type, computation.id)
                val effs = clauses.flatMap { (_, body) -> body.effs }.toSet()
                Effecting(CTerm.Match(scrutinee.term, clauses.map { (pat, body) -> pat to body.term }, computation.id), effs)
            }
            computation is STerm.FunOf && type is CVTerm.Fun -> {
                if (type.params.size != computation.params.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, computation.params.size, computation.id))
                }
                restore {
                    value = value.empty()
                    (computation.params zip type.params).forEach { (name, param) ->
                        val lower = param.lower?.let { Store(value.normalizer).evalTerm(it) }
                        val upper = param.upper?.let { Store(value.normalizer).evalTerm(it) }
                        val type = Store(value.normalizer).evalTerm(param.type)
                        this@Elab.types[param.id] = type
                        value = value.bind(name.id, Entry(param.termRelevant, name.text, lower, upper, param.typeRelevant, type, value.stage))
                    }
                    val resultant = checkComputation(computation.body, Store(value.normalizer).evalTerm(type.resultant), effs)
                    Effecting(CTerm.FunOf(computation.params, resultant.term, computation.id), emptySet())
                }
            }
            else -> {
                val id = computation.id
                val computation = inferComputation(computation)
                val isSubtype = subtypeTerms(computation.type, type)
                if (!isSubtype) {
                    types[id] = END
                    val expected = printTerm(Store(value.normalizer).quoteTerm(type))
                    val actual = printTerm(Store(value.normalizer).quoteTerm(computation.type))
                    diagnose(Diagnostic.TermMismatch(expected, actual, id))
                }
                if (!effs.containsAll(computation.effs)) {
                    diagnose(Diagnostic.EffMismatch(effs.map { printEff(it) }, computation.effs.map { printEff(it) }, id))
                }
                Effecting(computation.term, computation.effs)
            }
        }
    }

    /**
     * Checks if the [term1] and the [term2] can be unified under the normalizer.
     */
    private fun Store<Normalizer>.unifyTerms(term1: CVTerm, term2: CVTerm): Boolean {
        val term1 = value.force(term1)
        val term2 = value.force(term2)
        return when {
            term1.id != null && term2.id != null && term1.id == term2.id -> true
            term1 is CVTerm.Meta && term2 is CVTerm.Meta && term1.index == term2.index -> true
            term1 is CVTerm.Meta ->
                when (val solved1 = value.getSolution(term1.index)) {
                    null -> {
                        value.solve(term1.index, term2)
                        true
                    }
                    else -> unifyTerms(solved1, term2)
                }
            term2 is CVTerm.Meta -> unifyTerms(term2, term1)
            term1 is CVTerm.Command && term2 is CVTerm.Command -> unifyTerms(term1.body.value, term2.body.value)
            term1 is CVTerm.Var && term2 is CVTerm.Var -> term1.level == term2.level
            term1 is CVTerm.Def && term2 is CVTerm.Def && term1.name == term2.name -> true
            term1 is CVTerm.Match && term2 is CVTerm.Match -> false // TODO
            term1 is CVTerm.UnitOf && term2 is CVTerm.UnitOf -> true
            term1 is CVTerm.BoolOf && term2 is CVTerm.BoolOf -> term1.value == term2.value
            term1 is CVTerm.ByteOf && term2 is CVTerm.ByteOf -> term1.value == term2.value
            term1 is CVTerm.ShortOf && term2 is CVTerm.ShortOf -> term1.value == term2.value
            term1 is CVTerm.IntOf && term2 is CVTerm.IntOf -> term1.value == term2.value
            term1 is CVTerm.LongOf && term2 is CVTerm.LongOf -> term1.value == term2.value
            term1 is CVTerm.FloatOf && term2 is CVTerm.FloatOf -> term1.value == term2.value
            term1 is CVTerm.DoubleOf && term2 is CVTerm.DoubleOf -> term1.value == term2.value
            term1 is CVTerm.StringOf && term2 is CVTerm.StringOf -> term1.value == term2.value
            term1 is CVTerm.ByteArrayOf && term2 is CVTerm.ByteArrayOf ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.IntArrayOf && term2 is CVTerm.IntArrayOf ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.LongArrayOf && term2 is CVTerm.LongArrayOf ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.ListOf && term2 is CVTerm.ListOf ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.CompoundOf && term2 is CVTerm.CompoundOf ->
                term1.elements.keys == term2.elements.keys &&
                        term1.elements.entries.all { (key1, element1) ->
                            val element2 = term2.elements[key1]!!
                            unifyTerms(element1.element.value, element2.element.value)
                        }
            term1 is CVTerm.TupleOf && term2 is CVTerm.TupleOf ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { (element1, element2) ->
                            unifyTerms(element1.value, element2.value)
                        }
            term1 is CVTerm.RefOf && term2 is CVTerm.RefOf -> unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Refl && term2 is CVTerm.Refl -> true
            term1 is CVTerm.FunOf && term2 is CVTerm.FunOf ->
                term1.params.size == term2.params.size && run {
                    term1.params.forEach { param ->
                        value = value.bind(lazyOf(CVTerm.Var(param.text, value.size)))
                    }
                    unifyTerms(evalTerm(term1.body), evalTerm(term2.body))
                }
            term1 is CVTerm.Apply && term2 is CVTerm.Apply ->
                unifyTerms(term1.function, term2.function) &&
                        (term1.arguments zip term2.arguments).all { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.CodeOf && term2 is CVTerm.CodeOf -> unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Splice && term2 is CVTerm.Splice -> unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Or && term1.variants.isEmpty() && term2 is CVTerm.Or && term2.variants.isEmpty() -> true
            term1 is CVTerm.And && term1.variants.isEmpty() && term2 is CVTerm.And && term2.variants.isEmpty() -> true
            term1 is CVTerm.Unit && term2 is CVTerm.Unit -> true
            term1 is CVTerm.Bool && term2 is CVTerm.Bool -> true
            term1 is CVTerm.Byte && term2 is CVTerm.Byte -> true
            term1 is CVTerm.Short && term2 is CVTerm.Short -> true
            term1 is CVTerm.Int && term2 is CVTerm.Int -> true
            term1 is CVTerm.Long && term2 is CVTerm.Long -> true
            term1 is CVTerm.Float && term2 is CVTerm.Float -> true
            term1 is CVTerm.Double && term2 is CVTerm.Double -> true
            term1 is CVTerm.String && term2 is CVTerm.String -> true
            term1 is CVTerm.ByteArray && term2 is CVTerm.ByteArray -> true
            term1 is CVTerm.IntArray && term2 is CVTerm.IntArray -> true
            term1 is CVTerm.LongArray && term2 is CVTerm.LongArray -> true
            term1 is CVTerm.List && term2 is CVTerm.List ->
                unifyTerms(term1.element.value, term2.element.value) &&
                        unifyTerms(term1.size.value, term2.size.value)
            term1 is CVTerm.Compound && term2 is CVTerm.Compound ->
                term1.elements.keys == term2.elements.keys &&
                        term1.elements.entries.all { (key1, element1) ->
                            val element2 = term2.elements[key1]!!
                            value = value.bind(lazyOf(CVTerm.Var(key1, value.size)))
                            element1.relevant == element2.relevant &&
                                    unifyTerms(element1.type.value, element2.type.value)
                        }
            term1 is CVTerm.Tuple && term2 is CVTerm.Tuple ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { (entry1, entry2) ->
                            value = value.bind(lazyOf(CVTerm.Var(entry1.name.text, value.size)))
                            entry1.relevant == entry2.relevant &&
                                    unifyTerms(evalTerm(entry1.type), evalTerm(entry2.type))
                        }
            term1 is CVTerm.Ref && term2 is CVTerm.Ref -> unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Eq && term2 is CVTerm.Eq ->
                unifyTerms(term1.left.value, term2.left.value) &&
                        unifyTerms(term1.right.value, term2.right.value)
            term1 is CVTerm.Fun && term2 is CVTerm.Fun ->
                term1.params.size == term2.params.size &&
                        term1.effs == term2.effs &&
                        (term1.params zip term2.params).all { (param1, param2) ->
                            value = value.bind(lazyOf(CVTerm.Var("", value.size)))
                            unifyTerms(evalTerm(param2.type), evalTerm(param1.type))
                        } &&
                        unifyTerms(evalTerm(term1.resultant), evalTerm(term2.resultant))
            term1 is CVTerm.Code && term2 is CVTerm.Code -> unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Type && term2 is CVTerm.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [term1] is a subtype of the [term2] under the context.
     */
    private fun Store<Context>.subtypeTerms(term1: CVTerm, term2: CVTerm): Boolean {
        val term1 = value.normalizer.force(term1)
        val term2 = value.normalizer.force(term2)
        return when {
            term1 is CVTerm.Var && term2 is CVTerm.Var && term1.level == term2.level -> true
            term1 is CVTerm.Var && value.entries[term1.level].upper != null -> {
                val upper = value.entries[term1.level].upper!!
                subtypeTerms(upper, term2)
            }
            term2 is CVTerm.Var && value.entries[term2.level].lower != null -> {
                val lower = value.entries[term2.level].lower!!
                subtypeTerms(term1, lower)
            }
            term1 is CVTerm.Apply && term2 is CVTerm.Apply ->
                subtypeTerms(term1.function, term2.function) &&
                        (term1.arguments zip term2.arguments).all { (argument1, argument2) ->
                            Store(value.normalizer).unifyTerms(argument1.value, argument2.value) // pointwise subtyping
                        }
            term1 is CVTerm.Or -> term1.variants.all { subtypeTerms(it.value, term2) }
            term2 is CVTerm.Or -> term2.variants.any { subtypeTerms(term1, it.value) }
            term2 is CVTerm.And -> term2.variants.all { subtypeTerms(term1, it.value) }
            term1 is CVTerm.And -> term1.variants.any { subtypeTerms(it.value, term2) }
            term1 is CVTerm.List && term2 is CVTerm.List ->
                subtypeTerms(term1.element.value, term2.element.value) &&
                        Store(value.normalizer).unifyTerms(term1.size.value, term2.size.value)
            term1 is CVTerm.Compound && term2 is CVTerm.Compound ->
                term1.elements.size == term2.elements.size &&
                        restore {
                            term2.elements.entries.all { (key2, element2) ->
                                val element1 = term1.elements[key2]
                                if (element1 != null) {
                                    element1.relevant == element2.relevant && run {
                                        val upper1 = element1.type.value
                                        subtypeTerms(upper1, element2.type.value).also {
                                            value = value.bindUnchecked(Entry(false, key2, null, upper1, true, TYPE, value.stage))
                                        }
                                    }
                                } else false
                            }
                        }
            term1 is CVTerm.Tuple && term2 is CVTerm.Tuple ->
                restore {
                    (term1.elements zip term2.elements).all { (element1, element2) ->
                        element1.relevant == element2.relevant && run {
                            val upper1 = Store(value.normalizer).evalTerm(element1.type)
                            subtypeTerms(upper1, Store(value.normalizer).evalTerm(element2.type)).also {
                                value = value.bindUnchecked(Entry(false, element2.name.text, null, upper1, true, TYPE, value.stage))
                            }
                        }
                    }
                }
            term1 is CVTerm.Ref && term2 is CVTerm.Ref -> subtypeTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Fun && term2 is CVTerm.Fun ->
                term1.params.size == term2.params.size &&
                        term2.effs.containsAll(term1.effs) &&
                        restore {
                            (term1.params zip term2.params).all { (param1, param2) ->
                                value = value.bindUnchecked(Entry(param1.termRelevant, "", null, null /* TODO */, param1.typeRelevant, TYPE, value.stage))
                                param1.termRelevant == param2.termRelevant &&
                                        param1.typeRelevant == param2.typeRelevant &&
                                        subtypeTerms(Store(value.normalizer).evalTerm(param2.type), Store(value.normalizer).evalTerm(param1.type))
                            } &&
                                    subtypeTerms(Store(value.normalizer).evalTerm(term1.resultant), Store(value.normalizer).evalTerm(term2.resultant))
                        }
            term1 is CVTerm.Code && term2 is CVTerm.Code -> subtypeTerms(term1.element.value, term2.element.value)
            else -> Store(value.normalizer).unifyTerms(term1, term2)
        }
    }

    private fun Store<Context>.checkExhaustiveness(patterns: List<CPat>, type: CVTerm, id: Id) {
        fun check(patterns: List<CPat>, type: CVTerm): Boolean =
            patterns.filterIsInstance<CPat.Var>().isNotEmpty() || (when (val type = value.normalizer.force(type)) {
                is CVTerm.Eq -> patterns.filterIsInstance<CPat.Refl>().isNotEmpty()
                is CVTerm.Unit -> patterns.filterIsInstance<CPat.UnitOf>().isNotEmpty()
                is CVTerm.Bool -> {
                    val patterns = patterns.filterIsInstance<CPat.BoolOf>()
                    patterns.find { it.value } != null &&
                            patterns.find { !it.value } != null
                }
                is CVTerm.Ref -> {
                    val patterns = patterns.filterIsInstance<CPat.RefOf>()
                    check(patterns.map { it.element }, type.element.value)
                }
                else -> false
            })

        if (!check(patterns, type)) {
            diagnose(Diagnostic.NotExhausted(id))
        }
    }

    /**
     * Infers the type of the [pat] under the context.
     */
    private fun Store<Context>.inferPat(pat: SPat): Pair<CPat, CVTerm> =
        when (pat) {
            is SPat.Var -> {
                val type = value.normalizer.fresh(pat.id)
                value = value.bind(pat.id, Entry(true /* TODO */, pat.name, END, ANY, true, type, value.stage))
                CPat.Var(pat.name, pat.id) to type
            }
            is SPat.UnitOf -> CPat.UnitOf(pat.id) to UNIT
            is SPat.BoolOf -> CPat.BoolOf(pat.value, pat.id) to BOOL
            is SPat.ByteOf -> CPat.ByteOf(pat.value, pat.id) to BYTE
            is SPat.ShortOf -> CPat.ShortOf(pat.value, pat.id) to SHORT
            is SPat.IntOf -> CPat.IntOf(pat.value, pat.id) to INT
            is SPat.LongOf -> CPat.LongOf(pat.value, pat.id) to LONG
            is SPat.FloatOf -> CPat.FloatOf(pat.value, pat.id) to FLOAT
            is SPat.DoubleOf -> CPat.DoubleOf(pat.value, pat.id) to DOUBLE
            is SPat.StringOf -> CPat.StringOf(pat.value, pat.id) to STRING
            is SPat.ByteArrayOf -> {
                val elements = pat.elements.map { element -> checkPat(element, BYTE) }
                CPat.ByteArrayOf(elements, pat.id) to BYTE_ARRAY
            }
            is SPat.IntArrayOf -> {
                val elements = pat.elements.map { element -> checkPat(element, INT) }
                CPat.IntArrayOf(elements, pat.id) to INT_ARRAY
            }
            is SPat.LongArrayOf -> {
                val elements = pat.elements.map { element -> checkPat(element, LONG) }
                CPat.LongArrayOf(elements, pat.id) to LONG_ARRAY
            }
            is SPat.ListOf -> {
                if (pat.elements.isEmpty()) {
                    CPat.ListOf(emptyList(), pat.id) to END
                } else { // TODO: use union of element types
                    val (head, headType) = inferPat(pat.elements.first())
                    val elements = pat.elements.drop(1).map { element -> checkPat(element, headType) }
                    val size = CVTerm.IntOf(elements.size)
                    CPat.ListOf(listOf(head) + elements, pat.id) to CVTerm.List(lazyOf(headType), lazyOf(size))
                }
            }
            is SPat.CompoundOf -> {
                val (elementTerms, elementTypes) = pat.elements.map { (name, element) ->
                    val (element, elementType) = inferPat(element)
                    (name to element) to (name.text to CVTerm.Compound.Entry(true, name, lazyOf(elementType), null))
                }.unzip()
                CPat.CompoundOf(elementTerms, pat.id) to CVTerm.Compound(elementTypes.toLinkedHashMap())
            }
            is SPat.TupleOf -> {
                val elements = pat.elements.map { element -> inferPat(element) }
                val elementTerms = elements.map { (element, _) -> element }
                val elementTypes = elements.map { (_, type) -> CTerm.Tuple.Entry(true, Name("", freshId()), Store(value.normalizer).quoteTerm(type), null) }
                CPat.TupleOf(elementTerms, pat.id) to CVTerm.Tuple(elementTypes)
            }
            is SPat.RefOf -> {
                val (element, elementType) = inferPat(pat.element)
                CPat.RefOf(element, pat.id) to CVTerm.Ref(lazyOf(elementType))
            }
            is SPat.Refl -> {
                val left = lazy { value.normalizer.fresh(pat.id) }
                CPat.Refl(pat.id) to CVTerm.Eq(left, left)
            }
        }.also { (_, type) ->
            types[pat.id] = type
        }

    /**
     * Checks the [pat] against the [type] under the context.
     */
    private fun Store<Context>.checkPat(pat: SPat, type: CVTerm): CPat {
        val type = value.normalizer.force(type).also {
            types[pat.id] = it
        }
        return when {
            pat is SPat.Var -> {
                value = value.bind(pat.id, Entry(true /* TODO */, pat.name, END, ANY, true, type, value.stage))
                CPat.Var(pat.name, pat.id)
            }
            pat is SPat.ListOf && type is CVTerm.List -> {
                val elements = pat.elements.map { element ->
                    checkPat(element, type.element.value)
                }
                when (val size = value.normalizer.force(type.size.value)) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, pat.id))
                    else -> {}
                }
                CPat.ListOf(elements, pat.id)
            }
            pat is SPat.CompoundOf && type is CVTerm.Compound -> {
                val elements = (pat.elements zip type.elements.entries).map { (element, entry) ->
                    val pat = checkPat(element.second, entry.value.type.value)
                    element.first to pat
                }
                CPat.CompoundOf(elements, pat.id)
            }
            pat is SPat.TupleOf && type is CVTerm.Tuple -> {
                val elements = (pat.elements zip type.elements).map { (element, entry) ->
                    val type = Store(value.normalizer).evalTerm(entry.type)
                    checkPat(element, type)
                }
                CPat.TupleOf(elements, pat.id)
            }
            pat is SPat.RefOf && type is CVTerm.Ref -> {
                val element = checkPat(pat.element, type.element.value)
                CPat.RefOf(element, pat.id)
            }
            pat is SPat.Refl && type is CVTerm.Eq -> {
                value = value.copy(normalizer = Store(value.normalizer).apply {
                    match(type.left.value, type.right.value)
                }.value)
                CPat.Refl(pat.id)
            }
            else -> {
                val (inferred, inferredType) = inferPat(pat)
                val isSubtype = subtypeTerms(inferredType, type)
                if (!isSubtype) {
                    types[pat.id] = END
                    val expected = printTerm(Store(value.normalizer).quoteTerm(type))
                    val actual = printTerm(Store(value.normalizer).quoteTerm(inferredType))
                    diagnose(Diagnostic.TermMismatch(expected, actual, pat.id))
                }
                inferred
            }
        }
    }

    /**
     * Matches the [term1] and the [term2].
     */
    private fun Store<Normalizer>.match(term1: CVTerm, term2: CVTerm) {
        val term1 = value.force(term1)
        val term2 = value.force(term2)
        when {
            term2 is CVTerm.Var -> value = value.subst(term2.level, lazyOf(term1))
            term1 is CVTerm.Var -> value = value.subst(term1.level, lazyOf(term2))
            term1 is CVTerm.ByteArrayOf && term2 is CVTerm.ByteArrayOf -> (term1.elements zip term2.elements).forEach { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.IntArrayOf && term2 is CVTerm.IntArrayOf -> (term1.elements zip term2.elements).forEach { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.LongArrayOf && term2 is CVTerm.LongArrayOf -> (term1.elements zip term2.elements).forEach { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.ListOf && term2 is CVTerm.ListOf -> (term1.elements zip term2.elements).forEach { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.CompoundOf && term2 is CVTerm.CompoundOf -> (term1.elements.entries zip term2.elements.entries).forEach { (entry1, entry2) ->
                if (entry1.key == entry2.key) {
                    match(entry1.value.element.value, entry2.value.element.value)
                }
            }
            term1 is CVTerm.TupleOf && term2 is CVTerm.TupleOf -> (term1.elements zip term2.elements).forEach { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.RefOf && term2 is CVTerm.RefOf -> match(term1.element.value, term2.element.value)
            else -> Unit // TODO
        }
    }

    /**
     * Elaborates the [eff].
     */
    private fun elabEff(eff: SEff): CEff = when (eff) {
        is SEff.Name -> CEff.Name(eff.name)
    }

    /**
     * Ensures that the representation of the [type] is monomorphic under this normalizer.
     */
    private fun Store<Normalizer>.checkRepr(id: Id, type: CVTerm) {
        fun join(term1: CVTerm, term2: CVTerm): Boolean {
            val term1 = value.force(term1)
            val term2 = value.force(term2)
            return when (term1) {
                is CVTerm.Or -> term1.variants.all { join(it.value, term2) }
                is CVTerm.And -> term1.variants.any { join(it.value, term2) }
                is CVTerm.Unit -> term2 is CVTerm.Unit
                is CVTerm.Bool -> term2 is CVTerm.Bool
                is CVTerm.Byte -> term2 is CVTerm.Byte
                is CVTerm.Short -> term2 is CVTerm.Short
                is CVTerm.Int -> term2 is CVTerm.Int
                is CVTerm.Long -> term2 is CVTerm.Long
                is CVTerm.Float -> term2 is CVTerm.Float
                is CVTerm.Double -> term2 is CVTerm.Double
                is CVTerm.String -> term2 is CVTerm.String
                is CVTerm.ByteArray -> term2 is CVTerm.ByteArray
                is CVTerm.IntArray -> term2 is CVTerm.IntArray
                is CVTerm.LongArray -> term2 is CVTerm.LongArray
                is CVTerm.List -> term2 is CVTerm.List
                is CVTerm.Compound -> term2 is CVTerm.Compound
                is CVTerm.Tuple -> term2 is CVTerm.Tuple
                is CVTerm.Ref -> term2 is CVTerm.Ref
                is CVTerm.Eq -> term2 is CVTerm.Eq
                is CVTerm.Fun -> term2 is CVTerm.Fun
                is CVTerm.Type -> term2 is CVTerm.Type
                else -> false
            }
        }

        when (type) {
            is CVTerm.Var -> diagnose(Diagnostic.PolyRepr(id))
            is CVTerm.Or -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolyRepr(id))
                }
            }
            is CVTerm.And -> if (type.variants.isEmpty()) {
                diagnose(Diagnostic.PolyRepr(id))
            }
            else -> {}
        }
    }

    private fun diagnose(diagnostic: Diagnostic): CVTerm {
        diagnostics += diagnostic
        return END
    }

    data class Result(
        val item: CItem,
        val types: Map<Id, CVTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>,
        val completions: Map<Id, List<Pair<String, CVTerm>>>,
    )

    private data class Typing(
        val term: CTerm,
        val type: CVTerm,
        val effs: Set<CEff> = emptySet(),
    )

    private data class Effecting(
        val term: CTerm,
        val effs: Set<CEff>,
    )

    private data class Entry(
        val termRelevant: Boolean,
        val name: String,
        val lower: CVTerm?,
        val upper: CVTerm?,
        val typeRelevant: Boolean,
        val type: CVTerm,
        val stage: Int,
    )

    private inner class Context(
        val entries: PersistentList<Entry>,
        val normalizer: Normalizer,
        val meta: Boolean,
        val stage: Int,
        val termRelevant: Boolean,
        val typeRelevant: Boolean,
    ) {
        val size: Int get() = entries.size

        fun lookup(name: String): Int = entries.indexOfLast { it.name == name }

        fun bind(id: Id, entry: Entry, term: CVTerm? = null): Context = bindUnchecked(entry, term).also { checkPhase(id, entry.type) }

        fun bindUnchecked(entry: Entry, term: CVTerm? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(term ?: CVTerm.Var(entry.name, size))), meta, stage, termRelevant, typeRelevant)

        fun empty(): Context = Context(persistentListOf(), normalizer.empty(), meta, stage, termRelevant, typeRelevant)

        fun copy(
            entries: PersistentList<Entry> = this.entries,
            normalizer: Normalizer = this.normalizer,
            static: Boolean = this.meta,
            stage: Int = this.stage,
            termRelevant: Boolean = this.termRelevant,
            typeRelevant: Boolean = this.typeRelevant,
        ): Context = Context(entries, normalizer, static, stage, termRelevant, typeRelevant)

        fun checkPhase(id: Id, type: CVTerm) {
            if (!meta && type is CVTerm.Code) diagnose(Diagnostic.PhaseMismatch(id))
        }
    }

    companion object : Pass<Pair<SItem, Map<String, CItem>>, Result> {
        private val UNIT = CVTerm.Unit()
        private val BOOL = CVTerm.Bool()
        private val BYTE = CVTerm.Byte()
        private val SHORT = CVTerm.Short()
        private val INT = CVTerm.Int()
        private val LONG = CVTerm.Long()
        private val FLOAT = CVTerm.Float()
        private val DOUBLE = CVTerm.Double()
        private val STRING = CVTerm.String()
        private val BYTE_ARRAY = CVTerm.ByteArray()
        private val INT_ARRAY = CVTerm.IntArray()
        private val LONG_ARRAY = CVTerm.LongArray()
        private val END = CVTerm.Or(emptyList())
        private val ANY = CVTerm.And(emptyList())
        private val TYPE = CVTerm.Type()

        override operator fun invoke(config: Config, input: Pair<SItem, Map<String, CItem>>): Result {
            val (item, dependencies) = input
            return Elab(dependencies).run {
                val normalizer = Normalizer(persistentListOf(), items, solutions)
                val (item, _) = Store(Context(persistentListOf(), normalizer, false, 0, true, true)).inferItem(item)
                Result(item, types, normalizer, diagnostics, completions)
            }
        }
    }
}
