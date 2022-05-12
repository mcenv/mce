package mce.pass.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.Id
import mce.ast.Modifier
import mce.ast.Name
import mce.pass.*
import mce.util.Store
import mce.util.toLinkedHashMap
import mce.ast.core.Eff as CEff
import mce.ast.core.Item as CItem
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
import mce.ast.surface.Module as SModule
import mce.ast.surface.Param as SParam
import mce.ast.surface.Pat as SPat
import mce.ast.surface.Signature as SSignature
import mce.ast.surface.Term as STerm

// TODO: check/synth effects and stages
@Suppress("NAME_SHADOWING")
class Elab private constructor(
    private val items: MutableMap<String, CItem>,
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val completions: MutableMap<Id, List<Pair<String, CVTerm>>> = mutableMapOf()
    private val types: MutableMap<Id, CVTerm> = mutableMapOf()
    private val solutions: MutableList<CVTerm?> = mutableListOf()

    /**
     * Infers the signature of the [item].
     */
    private fun Store<Context>.inferItem(item: SItem): Pair<CItem, CVSignature> {
        val modifiers = item.modifiers.toSet()
        return when (item) {
            is SItem.Def -> {
                val phase = modifiers.toPhase()
                modify { it.copy(phase = phase, termRelevant = false, typeRelevant = true) }
                val params = bindParams(item.params, phase)
                val effs = item.effs.map { elabEff(it) }.toSet()
                val resultant = checkTerm(item.resultant, TYPE, phase, PURE)
                val vResultant = map { it.normalizer }.evalTerm(resultant.term)
                val body = if (item.modifiers.contains(Modifier.BUILTIN)) {
                    types[item.body.id] = vResultant
                    Typing(CTerm.Builtin(item.body.id), vResultant, phase, effs)
                } else {
                    if (modifiers.contains(Modifier.RECURSIVE)) {
                        items[item.name] = CItem.Def(modifiers, item.name, params, resultant.term, effs, CTerm.Builtin(item.body.id), item.id)
                    }

                    modify { it.copy(termRelevant = true, typeRelevant = false) }
                    checkTerm(item.body, vResultant, phase, effs)
                }
                CItem.Def(modifiers, item.name, params, resultant.term, effs, body.term, item.id) to CVSignature.Def(item.name, params, resultant.term, null)
            }
            is SItem.Mod -> {
                val type = checkModule(item.type, CVModule.Type(null))
                val vType = value.normalizer.evalModule(type)
                val body = checkModule(item.body, vType)
                CItem.Mod(modifiers, item.name, vType, body, item.id) to CVSignature.Mod(item.name, vType, null)
            }
            is SItem.Test -> {
                val phase = modifiers.toPhase()
                val body = checkTerm(item.body, BOOL, phase, INFER)
                CItem.Test(modifiers, item.name, body.term, item.id) to CVSignature.Test(item.name, null)
            }
            is SItem.Pack -> {
                val body = checkTerm(item.body, ANY, Phase.STATIC, PURE) // TODO: type
                CItem.Pack(body.term, item.id) to CVSignature.Pack(null)
            }
            is SItem.Advancement -> {
                modify { it.copy(phase = Phase.STATIC) }
                val body = checkTerm(item.body, ANY, Phase.STATIC, PURE) // TODO: type
                CItem.Advancement(modifiers, item.name, body.term, item.id) to CVSignature.Advancement(item.name, null)
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

    private fun Set<Modifier>.toPhase(): Phase {
        val static = contains(Modifier.STATIC)
        val dynamic = contains(Modifier.DYNAMIC)
        return when {
            dynamic && static -> TODO()
            dynamic -> Phase.DYNAMIC
            static -> Phase.STATIC
            else -> Phase.TOP
        }
    }

    /**
     * Binds the [params] to the context.
     */
    private fun Store<Context>.bindParams(params: List<SParam>, phase: Phase): List<CParam> =
        params.map { param ->
            val type = checkTerm(param.type, TYPE, phase, PURE)
            val vType = map { it.normalizer }.evalTerm(type.term)
            val lower = param.lower?.let { checkTerm(it, vType, phase, PURE) }
            val vLower = lower?.let { lower -> map { it.normalizer }.evalTerm(lower.term) }
            val upper = param.upper?.let { checkTerm(it, vType, phase, PURE) }
            val vUpper = upper?.let { upper -> map { it.normalizer }.evalTerm(upper.term) }
            modify { it.bind(Entry(param.termRelevant, param.name, vLower, vUpper, param.typeRelevant, vType, value.phase)) }
            types[param.id] = vType
            CParam(param.termRelevant, param.name, lower?.term, upper?.term, param.typeRelevant, type.term, param.id)
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
                if (!(map { it.normalizer }.unifyModules(inferredType, type))) {
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
                                modify { it.bind(lazyOf(CVTerm.Var("", value.size))) }
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
                    modify { it.copy(termRelevant = false, typeRelevant = true) }
                    val params = bindParams(signature.params, Phase.TOP /* TODO */)
                    val resultant = checkTerm(signature.resultant, TYPE, INFER, PURE)
                    CSignature.Def(signature.name, params, resultant.term, signature.id)
                }
            is SSignature.Mod -> {
                val type = checkModule(signature.type, CVModule.Type(null))
                CSignature.Mod(signature.name, type, signature.id)
            }
            is SSignature.Test -> CSignature.Test(signature.name, signature.id)
        }

    private fun subtypePhase(phase1: Phase, phase2: Phase, id: Id): Boolean = when {
        phase1 == phase2 -> true
        phase1 == Phase.BOTTOM -> true
        phase2 == Phase.TOP -> true
        else -> {
            diagnose(Diagnostic.PhaseMismatch(phase2, phase1, id))
            false
        }
    }

    /**
     * Infers the type of the [term] under the context.
     */
    private fun Store<Context>.inferTerm(term: STerm, phase: Phase?, effs: Set<CEff>?): Typing {
        val phase = phase ?: Phase.BOTTOM
        val effs = effs ?: PURE
        return when {
            term is STerm.Hole -> {
                completions[term.id] = value.entries.map { it.name to it.type }
                Typing(CTerm.Hole(term.id), diagnose(Diagnostic.TermExpected(printTerm(map { it.normalizer }.quoteTerm(END)), term.id)), phase, PURE)
            }
            term is STerm.Meta -> {
                val type = value.normalizer.fresh(term.id)
                val term = checkTerm(term, type, phase, INFER)
                Typing(term.term, type, phase, effs)
            }
            term is STerm.Command && subtypePhase(phase, Phase.DYNAMIC, term.id) -> restore {
                modify { it.copy(phase = Phase.STATIC) }
                val body = checkTerm(term.body, STRING, Phase.STATIC, PURE)
                Typing(CTerm.Command(body.term, term.id), UNIT, phase, PURE)
            }
            term is STerm.Block -> restore {
                val elements = term.elements.map { inferTerm(it, phase, INFER) }
                val type = elements.lastOrNull()?.type ?: UNIT
                val effs = elements.fold(effs) { effs, element -> effs union element.effs }
                Typing(CTerm.Block(elements.map { it.term }, term.id), type, phase, effs)
            }
            term is STerm.Anno -> restore {
                modify { it.copy(termRelevant = false, typeRelevant = true) }
                val type = map { it.normalizer }.evalTerm(checkTerm(term.type, TYPE, phase, PURE).term)
                val element = checkTerm(term.element, type, phase, PURE)
                Typing(element.term, type, phase, PURE)
            }
            term is STerm.Var -> {
                val level = value.lookup(term.name)
                val type = when (level) {
                    -1 -> diagnose(Diagnostic.VarNotFound(term.name, term.id))
                    else -> {
                        val entry = value.entries[level]
                        var type = entry.type
                        if (value.phase != entry.phase) type = diagnose(Diagnostic.PhaseMismatch(value.phase, entry.phase, term.id))
                        if (value.termRelevant && !entry.termRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                        if (value.typeRelevant && !entry.typeRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                        map { it.normalizer }.checkRepr(term.id, type)
                        type
                    }
                }
                Typing(CTerm.Var(term.name, level /* TODO: avoid [IndexOutOfBoundsException] */, term.id), type, phase, PURE)
            }
            term is STerm.Def -> when (val item = items[term.name]) {
                is CItem.Def -> {
                    if (item.params.size != term.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(item.params.size, term.arguments.size, term.id))
                    }
                    map { it.normalizer }.run {
                        val arguments = (term.arguments zip item.params).map { (argument, param) ->
                            val id = argument.id
                            val argument = checkTerm(argument, evalTerm(param.type), phase, PURE).term
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
                            modify { it.bind(lazyOf(vArgument)) }
                            argument
                        }
                        val resultant = evalTerm(item.resultant)
                        Typing(CTerm.Def(term.name, arguments, term.id), resultant, phase, item.effs)
                    }
                }
                else -> Typing(CTerm.Def(term.name, emptyList() /* TODO? */, term.id), diagnose(Diagnostic.DefNotFound(term.name, term.id)), phase, PURE)
            }
            term is STerm.Let -> {
                val init = inferTerm(term.init, phase, INFER)
                modify { it.bind(Entry(true /* TODO */, term.name, END, ANY, true, init.type, value.phase)) }
                Typing(CTerm.Let(term.name, init.term, term.id), UNIT, phase, init.effs)
            }
            term is STerm.Match -> {
                val type = value.normalizer.fresh(term.id) // TODO: use union of element types
                val scrutinee = inferTerm(term.scrutinee, phase, PURE)
                val clauses = term.clauses.map { (pat, body) ->
                    restore {
                        val pat = checkPat(pat, scrutinee.type)
                        val body = checkTerm(body, type, phase, INFER)
                        pat to body
                    }
                }
                checkExhaustiveness(clauses.map { it.first }, scrutinee.type, term.id)
                val effs = clauses.fold(effs) { effs, (_, body) -> effs union body.effs }
                Typing(CTerm.Match(scrutinee.term, clauses.map { (pat, body) -> pat to body.term }, term.id), type, phase, effs)
            }
            term is STerm.UnitOf -> Typing(CTerm.UnitOf(term.id), UNIT, phase, PURE)
            term is STerm.BoolOf -> Typing(CTerm.BoolOf(term.value, term.id), BOOL, phase, PURE)
            term is STerm.ByteOf -> Typing(CTerm.ByteOf(term.value, term.id), BYTE, phase, PURE)
            term is STerm.ShortOf -> Typing(CTerm.ShortOf(term.value, term.id), SHORT, phase, PURE)
            term is STerm.IntOf -> Typing(CTerm.IntOf(term.value, term.id), INT, phase, PURE)
            term is STerm.LongOf -> Typing(CTerm.LongOf(term.value, term.id), LONG, phase, PURE)
            term is STerm.FloatOf -> Typing(CTerm.FloatOf(term.value, term.id), FLOAT, phase, PURE)
            term is STerm.DoubleOf -> Typing(CTerm.DoubleOf(term.value, term.id), DOUBLE, phase, PURE)
            term is STerm.StringOf -> Typing(CTerm.StringOf(term.value, term.id), STRING, phase, PURE)
            term is STerm.ByteArrayOf -> {
                val elements = term.elements.map { checkTerm(it, BYTE, phase, PURE).term }
                Typing(CTerm.ByteArrayOf(elements, term.id), BYTE_ARRAY, phase, PURE)
            }
            term is STerm.IntArrayOf -> {
                val elements = term.elements.map { checkTerm(it, INT, phase, PURE).term }
                Typing(CTerm.IntArrayOf(elements, term.id), INT_ARRAY, phase, PURE)
            }
            term is STerm.LongArrayOf -> {
                val elements = term.elements.map { checkTerm(it, LONG, phase, PURE).term }
                Typing(CTerm.LongArrayOf(elements, term.id), LONG_ARRAY, phase, PURE)
            }
            term is STerm.ListOf -> if (term.elements.isEmpty()) {
                Typing(CTerm.ListOf(emptyList(), term.id), CVTerm.List(lazyOf(END), lazyOf(CVTerm.IntOf(0))), phase, PURE)
            } else { // TODO: use union of element types
                val head = inferTerm(term.elements.first(), phase, PURE)
                val tail = term.elements.drop(1).map { checkTerm(it, head.type, phase, PURE).term }
                val elements = listOf(head.term) + tail
                val size = CVTerm.IntOf(elements.size)
                Typing(CTerm.ListOf(elements, term.id), CVTerm.List(lazyOf(head.type), lazyOf(size)), phase, PURE)
            }
            term is STerm.CompoundOf -> {
                val (elementTerms, elementTypes) = term.elements.map { entry ->
                    val element = inferTerm(entry.element, phase, PURE)
                    CTerm.CompoundOf.Entry(entry.name, element.term) to CVTerm.Compound.Entry(true, entry.name, lazyOf(element.type), null)
                }.unzip()
                Typing(CTerm.CompoundOf(elementTerms, term.id), CVTerm.Compound(elementTypes.map { it.name.text to it }.toLinkedHashMap()), phase, PURE)
            }
            term is STerm.TupleOf -> {
                val elements = term.elements.map { element -> inferTerm(element, phase, PURE) }
                Typing(
                    CTerm.TupleOf(elements.map { it.term }, term.id),
                    CVTerm.Tuple(elements.map { CTerm.Tuple.Entry(true, Name("", freshId()), map { it.normalizer }.quoteTerm(it.type), null) }),
                    phase,
                    PURE
                )
            }
            term is STerm.RefOf -> {
                val element = inferTerm(term.element, phase, PURE)
                Typing(CTerm.RefOf(element.term, term.id), CVTerm.Ref(lazyOf(element.type)), phase, PURE)
            }
            term is STerm.Refl -> {
                val left = lazy { value.normalizer.fresh(term.id) }
                Typing(CTerm.Refl(term.id), CVTerm.Eq(left, left), phase, PURE)
            }
            term is STerm.FunOf -> {
                val types = term.params.map { value.normalizer.fresh(term.id) }
                restore {
                    (term.params zip types).forEach { (param, type) ->
                        this@Elab.types[param.id] = type
                        modify { it.bind(Entry(true /* TODO */, param.text, END, ANY, true, type, value.phase)) }
                    }
                    val body = inferTerm(term.body, phase, INFER)
                    map { it.normalizer }.run {
                        val params = (term.params zip types).map { (param, type) ->
                            CParam(true /* TODO */, param.text, quoteTerm(END), quoteTerm(ANY), true, quoteTerm(type), param.id)
                        }
                        Typing(CTerm.FunOf(term.params, body.term, term.id), CVTerm.Fun(params, quoteTerm(body.type), body.effs), phase, PURE)
                    }
                }
            }
            term is STerm.Apply -> {
                val function = inferTerm(term.function, phase, INFER)
                val type = map { it.normalizer }.run {
                    when (val type = value.force(function.type)) {
                        is CVTerm.Fun -> type
                        else -> {
                            val params = term.arguments.map {
                                CParam(true, "", null, null, true, quoteTerm(value.fresh(freshId())), freshId())
                            }
                            val resultant = quoteTerm(value.fresh(freshId()))
                            unifyTerms(type, CVTerm.Fun(params, resultant, function.effs, null))
                            CVTerm.Fun(params, resultant, function.effs, type.id)
                        }
                    }
                }
                if (type.params.size != term.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, term.arguments.size, term.id))
                }
                map { it.normalizer }.run {
                    val arguments = (term.arguments zip type.params).map { (argument, param) ->
                        val tArgument = checkTerm(argument, evalTerm(param.type), phase, PURE).term
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
                        modify { it.bind(lazyOf(vArgument)) }
                        tArgument
                    }
                    val resultant = evalTerm(type.resultant)
                    Typing(CTerm.Apply(function.term, arguments, term.id), resultant, phase, type.effs)
                }
            }
            term is STerm.CodeOf && subtypePhase(phase, Phase.STATIC, term.id) -> restore {
                modify { it.copy(phase = Phase.DYNAMIC) }
                val element = inferTerm(term.element, Phase.DYNAMIC, PURE)
                Typing(CTerm.CodeOf(element.term, term.id), CVTerm.Code(lazyOf(element.type)), Phase.STATIC, PURE)
            }
            term is STerm.Splice && subtypePhase(phase, Phase.DYNAMIC, term.id) -> restore {
                modify { it.copy(phase = Phase.STATIC) }
                val element = inferTerm(term.element, Phase.STATIC, PURE)
                val type = when (val type = value.normalizer.force(element.type)) {
                    is CVTerm.Code -> type.element.value
                    else -> type.also { type ->
                        map { it.normalizer }.unifyTerms(type, CVTerm.Code(lazyOf(value.normalizer.fresh(freshId()))))
                    }
                }
                Typing(CTerm.Splice(element.term, term.id), type, Phase.DYNAMIC, PURE)
            }
            term is STerm.Or -> {
                val variants = term.variants.map { checkTerm(it, TYPE, phase, PURE).term }
                Typing(CTerm.Or(variants, term.id), TYPE, phase, PURE)
            }
            term is STerm.And -> {
                val variants = term.variants.map { checkTerm(it, TYPE, phase, PURE).term }
                Typing(CTerm.And(variants, term.id), TYPE, phase, PURE)
            }
            term is STerm.Unit -> Typing(CTerm.Unit(term.id), TYPE, phase, PURE)
            term is STerm.Bool -> Typing(CTerm.Bool(term.id), TYPE, phase, PURE)
            term is STerm.Byte -> Typing(CTerm.Byte(term.id), TYPE, phase, PURE)
            term is STerm.Short -> Typing(CTerm.Short(term.id), TYPE, phase, PURE)
            term is STerm.Int -> Typing(CTerm.Int(term.id), TYPE, phase, PURE)
            term is STerm.Long -> Typing(CTerm.Long(term.id), TYPE, phase, PURE)
            term is STerm.Float -> Typing(CTerm.Float(term.id), TYPE, phase, PURE)
            term is STerm.Double -> Typing(CTerm.Double(term.id), TYPE, phase, PURE)
            term is STerm.String -> Typing(CTerm.String(term.id), TYPE, phase, PURE)
            term is STerm.ByteArray -> Typing(CTerm.ByteArray(term.id), TYPE, phase, PURE)
            term is STerm.IntArray -> Typing(CTerm.IntArray(term.id), TYPE, phase, PURE)
            term is STerm.LongArray -> Typing(CTerm.LongArray(term.id), TYPE, phase, PURE)
            term is STerm.List -> {
                val element = checkTerm(term.element, TYPE, phase, PURE)
                restore {
                    modify { it.copy(termRelevant = false, typeRelevant = true) }
                    val size = checkTerm(term.size, INT, phase, PURE)
                    Typing(CTerm.List(element.term, size.term, term.id), TYPE, phase, PURE)
                }
            }
            term is STerm.Compound -> {
                val elements = term.elements.map { entry ->
                    val element = checkTerm(entry.type, TYPE, phase, PURE)
                    CTerm.Compound.Entry(entry.relevant, entry.name, element.term, entry.id)
                }
                Typing(CTerm.Compound(elements, term.id), TYPE, phase, PURE)
            }
            term is STerm.Tuple -> restore {
                val elements = term.elements.map { entry ->
                    val element = checkTerm(entry.type, TYPE, phase, PURE)
                    modify { it.bind(Entry(false, entry.name.text, END, ANY, true, map { it.normalizer }.evalTerm(element.term), value.phase)) }
                    CTerm.Tuple.Entry(entry.relevant, entry.name, element.term, entry.id)
                }
                Typing(CTerm.Tuple(elements, term.id), TYPE, phase, PURE)
            }
            term is STerm.Ref -> {
                val element = checkTerm(term.element, TYPE, phase, PURE)
                Typing(CTerm.Ref(element.term, term.id), TYPE, phase, PURE)
            }
            term is STerm.Eq -> {
                val left = inferTerm(term.left, phase, PURE)
                val right = checkTerm(term.right, left.type, phase, PURE)
                Typing(CTerm.Eq(left.term, right.term, term.id), TYPE, phase, PURE)
            }
            term is STerm.Fun -> restore {
                val params = bindParams(term.params, phase)
                val resultant = checkTerm(term.resultant, TYPE, phase, PURE)
                val effs = term.effs.map { elabEff(it) }.toSet()
                Typing(CTerm.Fun(params, resultant.term, effs, term.id), TYPE, phase, PURE)
            }
            term is STerm.Code && subtypePhase(phase, Phase.STATIC, term.id) -> {
                val element = checkTerm(term.element, TYPE, phase, PURE)
                Typing(CTerm.Code(element.term, term.id), TYPE, Phase.STATIC, PURE)
            }
            term is STerm.Type -> Typing(CTerm.Type(term.id), TYPE, phase, PURE)
            else -> Typing(CTerm.Hole(term.id), END, phase, PURE)
        }.also {
            types[term.id] = it.type
        }
    }

    /**
     * Checks the [term] against the [type] under the context.
     */
    private fun Store<Context>.checkTerm(term: STerm, type: CVTerm, phase: Phase?, effs: Set<CEff>?): Typing {
        val type = value.normalizer.force(type).also {
            types[term.id] = it
        }
        val phase = phase ?: Phase.BOTTOM
        return when {
            term is STerm.Hole -> {
                completions[term.id] = value.entries.map { it.name to it.type }
                diagnose(Diagnostic.TermExpected(printTerm(map { it.normalizer }.quoteTerm(type)), term.id))
                Typing(CTerm.Hole(term.id), type, phase, PURE)
            }
            term is STerm.Meta ->
                Typing(map { it.normalizer }.quoteTerm(value.normalizer.fresh(term.id)), type, phase, PURE)
            term is STerm.Block -> restore {
                val elements = term.elements.map { inferTerm(it, phase, INFER) }
                val effs = elements.fold(mutableSetOf<CEff>()) { effs, element -> effs.also { it += element.effs } }
                Typing(CTerm.Block(elements.map { it.term }, term.id), type, Phase.TOP /* TODO */, effs)
            }
            term is STerm.Let -> {
                val init = inferTerm(term.init, phase, INFER)
                modify { it.bind(Entry(true /* TODO */, term.name, END, ANY, true, init.type, value.phase)) }
                Typing(CTerm.Let(term.name, init.term, term.id), type, init.phase, init.effs)
            }
            term is STerm.Match -> {
                val scrutinee = inferTerm(term.scrutinee, phase, PURE)
                val clauses = term.clauses.map { (pat, body) ->
                    restore {
                        val pat = checkPat(pat, scrutinee.type)
                        val body = checkTerm(body, type, scrutinee.phase, INFER)
                        pat to body
                    }
                }
                checkExhaustiveness(clauses.map { it.first }, scrutinee.type, term.id)
                val effs = clauses.fold(mutableSetOf<CEff>()) { effs, (_, body) -> effs.also { it += body.effs } }
                Typing(CTerm.Match(scrutinee.term, clauses.map { (pat, body) -> pat to body.term }, term.id), type, scrutinee.phase, effs)
            }
            term is STerm.ListOf && type is CVTerm.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value, phase, PURE).term }
                when (val size = value.normalizer.force(type.size.value)) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                    else -> {}
                }
                Typing(CTerm.ListOf(elements, term.id), type, phase, PURE)
            }
            term is STerm.CompoundOf && type is CVTerm.Compound -> {
                if (type.elements.size != term.elements.size) {
                    diagnose(Diagnostic.SizeMismatch(type.elements.size, term.elements.size, term.id))
                }
                val elements = (term.elements zip type.elements.values).map { (element, entry) ->
                    val tElement = checkTerm(element.element, entry.type.value, phase, PURE)
                    CTerm.CompoundOf.Entry(element.name, tElement.term)
                }
                Typing(CTerm.CompoundOf(elements, term.id), type, phase, PURE)
            }
            term is STerm.TupleOf && type is CVTerm.Tuple -> restore {
                val elements = (term.elements zip type.elements).map { (element, entry) ->
                    val vType = map { it.normalizer }.evalTerm(entry.type)
                    val tElement = checkTerm(element, vType, phase, PURE).term
                    val vElement = map { it.normalizer }.evalTerm(tElement)
                    modify { it.bind(Entry(entry.relevant, "", END, ANY, true, vType, value.phase), vElement) }
                    tElement
                }
                Typing(CTerm.TupleOf(elements, term.id), type, phase, PURE)
            }
            term is STerm.RefOf && type is CVTerm.Ref -> {
                val element = checkTerm(term.element, type.element.value, phase, PURE)
                Typing(CTerm.RefOf(element.term, term.id), type, phase, PURE)
            }
            term is STerm.FunOf && type is CVTerm.Fun -> {
                if (type.params.size != term.params.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, term.params.size, term.id))
                }
                restore {
                    modify { it.empty() }
                    (term.params zip type.params).forEach { (name, param) ->
                        val lower = param.lower?.let { map { it.normalizer }.evalTerm(it) }
                        val upper = param.upper?.let { map { it.normalizer }.evalTerm(it) }
                        val type = map { it.normalizer }.evalTerm(param.type)
                        this@Elab.types[param.id] = type
                        modify { it.bind(Entry(param.termRelevant, name.text, lower, upper, param.typeRelevant, type, value.phase)) }
                    }
                    val resultant = checkTerm(term.body, map { it.normalizer }.evalTerm(type.resultant), phase, effs)
                    Typing(CTerm.FunOf(term.params, resultant.term, term.id), type, resultant.phase, PURE)
                }
            }
            term is STerm.CodeOf && type is CVTerm.Code && subtypePhase(phase, Phase.STATIC, term.id) -> restore {
                modify { it.copy(phase = Phase.DYNAMIC) }
                val element = checkTerm(term.element, type.element.value, Phase.DYNAMIC, PURE)
                Typing(CTerm.CodeOf(element.term, term.id), type, phase, PURE)
            }
            else -> {
                val id = term.id
                val inferred = inferTerm(term, phase, INFER)
                if (!subtypeTerms(inferred.type, type)) {
                    types[id] = END
                    val expected = printTerm(map { it.normalizer }.quoteTerm(type))
                    val actual = printTerm(map { it.normalizer }.quoteTerm(inferred.type))
                    diagnose(Diagnostic.TermMismatch(expected, actual, id))
                }
                if (effs?.containsAll(inferred.effs) == false) {
                    diagnose(Diagnostic.EffMismatch(effs.map { printEff(it) }, inferred.effs.map { printEff(it) }, id))
                }
                Typing(inferred.term, type, inferred.phase, inferred.effs)
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
                        modify { it.bind(lazyOf(CVTerm.Var(param.text, value.size))) }
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
                            modify { it.bind(lazyOf(CVTerm.Var(key1, value.size))) }
                            element1.relevant == element2.relevant &&
                                    unifyTerms(element1.type.value, element2.type.value)
                        }
            term1 is CVTerm.Tuple && term2 is CVTerm.Tuple ->
                term1.elements.size == term2.elements.size &&
                        (term1.elements zip term2.elements).all { (entry1, entry2) ->
                            modify { it.bind(lazyOf(CVTerm.Var(entry1.name.text, value.size))) }
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
                            modify { it.bind(lazyOf(CVTerm.Var("", value.size))) }
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
                            map { it.normalizer }.unifyTerms(argument1.value, argument2.value) // pointwise subtyping
                        }
            term1 is CVTerm.Or -> term1.variants.all { subtypeTerms(it.value, term2) }
            term2 is CVTerm.Or -> term2.variants.any { subtypeTerms(term1, it.value) }
            term2 is CVTerm.And -> term2.variants.all { subtypeTerms(term1, it.value) }
            term1 is CVTerm.And -> term1.variants.any { subtypeTerms(it.value, term2) }
            term1 is CVTerm.List && term2 is CVTerm.List ->
                subtypeTerms(term1.element.value, term2.element.value) &&
                        map { it.normalizer }.unifyTerms(term1.size.value, term2.size.value)
            term1 is CVTerm.Compound && term2 is CVTerm.Compound ->
                term1.elements.size == term2.elements.size &&
                        restore {
                            term2.elements.entries.all { (key2, element2) ->
                                val element1 = term1.elements[key2]
                                if (element1 != null) {
                                    element1.relevant == element2.relevant && run {
                                        val upper1 = element1.type.value
                                        subtypeTerms(upper1, element2.type.value).also {
                                            modify { it.bind(Entry(false, key2, null, upper1, true, TYPE, value.phase)) }
                                        }
                                    }
                                } else false
                            }
                        }
            term1 is CVTerm.Tuple && term2 is CVTerm.Tuple ->
                restore {
                    (term1.elements zip term2.elements).all { (element1, element2) ->
                        element1.relevant == element2.relevant && run {
                            val upper1 = map { it.normalizer }.evalTerm(element1.type)
                            subtypeTerms(upper1, map { it.normalizer }.evalTerm(element2.type)).also {
                                modify { it.bind(Entry(false, element2.name.text, null, upper1, true, TYPE, value.phase)) }
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
                                modify { it.bind(Entry(param1.termRelevant, "", null, null /* TODO */, param1.typeRelevant, TYPE, value.phase)) }
                                param1.termRelevant == param2.termRelevant &&
                                        param1.typeRelevant == param2.typeRelevant &&
                                        subtypeTerms(map { it.normalizer }.evalTerm(param2.type), map { it.normalizer }.evalTerm(param1.type))
                            } &&
                                    subtypeTerms(map { it.normalizer }.evalTerm(term1.resultant), map { it.normalizer }.evalTerm(term2.resultant))
                        }
            term1 is CVTerm.Code && term2 is CVTerm.Code -> subtypeTerms(term1.element.value, term2.element.value)
            else -> map { it.normalizer }.unifyTerms(term1, term2)
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
                modify { it.bind(Entry(true /* TODO */, pat.name, END, ANY, true, type, value.phase)) }
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
                val elementTypes = elements.map { (_, type) -> CTerm.Tuple.Entry(true, Name("", freshId()), map { it.normalizer }.quoteTerm(type), null) }
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
                modify { it.bind(Entry(true /* TODO */, pat.name, END, ANY, true, type, value.phase)) }
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
                    val type = map { it.normalizer }.evalTerm(entry.type)
                    checkPat(element, type)
                }
                CPat.TupleOf(elements, pat.id)
            }
            pat is SPat.RefOf && type is CVTerm.Ref -> {
                val element = checkPat(pat.element, type.element.value)
                CPat.RefOf(element, pat.id)
            }
            pat is SPat.Refl && type is CVTerm.Eq -> {
                modify {
                    it.copy(normalizer = map { it.normalizer }.apply {
                        match(type.left.value, type.right.value)
                    }.value)
                }
                CPat.Refl(pat.id)
            }
            else -> {
                val (inferred, inferredType) = inferPat(pat)
                val isSubtype = subtypeTerms(inferredType, type)
                if (!isSubtype) {
                    types[pat.id] = END
                    val expected = printTerm(map { it.normalizer }.quoteTerm(type))
                    val actual = printTerm(map { it.normalizer }.quoteTerm(inferredType))
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
            term2 is CVTerm.Var -> modify { it.subst(term2.level, lazyOf(term1)) }
            term1 is CVTerm.Var -> modify { it.subst(term1.level, lazyOf(term2)) }
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

    enum class Phase {
        TOP,
        DYNAMIC,
        STATIC,
        BOTTOM,
    }

    private data class Typing(
        val term: CTerm,
        val type: CVTerm,
        val phase: Phase,
        val effs: Set<CEff>,
    )

    private data class Entry(
        val termRelevant: Boolean,
        val name: String,
        val lower: CVTerm?,
        val upper: CVTerm?,
        val typeRelevant: Boolean,
        val type: CVTerm,
        val phase: Phase,
    )

    private inner class Context(
        val entries: PersistentList<Entry>,
        val normalizer: Normalizer,
        val phase: Phase,
        val termRelevant: Boolean,
        val typeRelevant: Boolean,
    ) {
        val size: Int get() = entries.size

        fun lookup(name: String): Int = entries.indexOfLast { it.name == name }

        fun bind(entry: Entry, term: CVTerm? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(term ?: CVTerm.Var(entry.name, size))), phase, termRelevant, typeRelevant)

        fun empty(): Context = Context(persistentListOf(), normalizer.empty(), phase, termRelevant, typeRelevant)

        fun copy(
            entries: PersistentList<Entry> = this.entries,
            normalizer: Normalizer = this.normalizer,
            phase: Phase = this.phase,
            termRelevant: Boolean = this.termRelevant,
            typeRelevant: Boolean = this.typeRelevant,
        ): Context = Context(entries, normalizer, phase, termRelevant, typeRelevant)
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
        private val INFER = null
        private val PURE = emptySet<CEff>()

        override operator fun invoke(config: Config, input: Pair<SItem, Map<String, CItem>>): Result {
            val (item, dependencies) = input
            return Elab(dependencies.toMutableMap()).run {
                val normalizer = Normalizer(persistentListOf(), items, solutions)
                val (item, _) = Store(Context(persistentListOf(), normalizer, Phase.TOP, true, true)).inferItem(item)
                Result(item, types, normalizer, diagnostics, completions)
            }
        }
    }
}
