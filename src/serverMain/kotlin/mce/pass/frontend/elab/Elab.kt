package mce.pass.frontend.elab

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.Id
import mce.pass.*
import mce.pass.frontend.Diagnostic
import mce.pass.frontend.printEff
import mce.pass.frontend.printTerm
import mce.util.State
import mce.util.run
import mce.util.toLinkedHashMap
import mce.pass.frontend.decode.Eff as SEff
import mce.pass.frontend.decode.Item as SItem
import mce.pass.frontend.decode.Modifier as SModifier
import mce.pass.frontend.decode.Module as SModule
import mce.pass.frontend.decode.Param as SParam
import mce.pass.frontend.decode.Pat as SPat
import mce.pass.frontend.decode.Signature as SSignature
import mce.pass.frontend.decode.Term as STerm
import mce.pass.frontend.elab.Eff as CEff
import mce.pass.frontend.elab.Entry as CEntry
import mce.pass.frontend.elab.Item as CItem
import mce.pass.frontend.elab.Modifier as CModifier
import mce.pass.frontend.elab.Module as CModule
import mce.pass.frontend.elab.Param as CParam
import mce.pass.frontend.elab.Pat as CPat
import mce.pass.frontend.elab.Signature as CSignature
import mce.pass.frontend.elab.Term as CTerm
import mce.pass.frontend.elab.VModule as CVModule
import mce.pass.frontend.elab.VSignature as CVSignature
import mce.pass.frontend.elab.VTerm as CVTerm

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
    private fun inferItem(item: SItem): State<Context, Pair<CItem, CVSignature>> = {
        val modifiers = item.modifiers.mapTo(mutableSetOf()) { elabModifier(it) }
        when (item) {
            is SItem.Def -> {
                !modify { copy(meta = item.modifiers.contains(SModifier.STATIC), termRelevant = false, typeRelevant = true) }
                val params = !bindParams(item.params)
                val resultant = !checkTerm(item.resultant, TYPE)
                val vResultant = !lift({ normalizer }, evalTerm(resultant))
                val effs = item.effs.map { elabEff(it) }.toSet()
                val body = if (item.modifiers.contains(SModifier.BUILTIN)) {
                    CTerm.Hole(item.body.id)
                } else {
                    !modify { copy(termRelevant = true, typeRelevant = false) }
                    !checkTerm(item.body, vResultant)
                }
                (!get()).checkPhase(item.body.id, vResultant)
                CItem.Def(modifiers, item.name, params, resultant, effs, body, item.id) to CVSignature.Def(item.name, params, resultant, null)
            }
            is SItem.Mod -> {
                val type = !checkModule(item.type, CVModule.Type(null))
                val vType = !lift({ normalizer }, evalModule(type))
                val body = !checkModule(item.body, vType)
                CItem.Mod(modifiers, item.name, vType, body, item.id) to CVSignature.Mod(item.name, vType, null)
            }
            is SItem.Test -> {
                val body = !checkTerm(item.body, BOOL)
                CItem.Test(modifiers, item.name, body, item.id) to CVSignature.Test(item.name, null)
            }
        }
    }

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun checkItem(item: SItem, signature: CVSignature): State<Context, CItem> = {
        val (inferred, inferredSignature) = !inferItem(item)
        if (!(!lift({ Normalizer(persistentListOf(), items, solutions) }, unifySignatures(inferredSignature, signature)))) {
            diagnose(Diagnostic.SigMismatch(item.id)) // TODO: serialize signatures
        }
        inferred
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
    private fun bindParams(params: List<SParam>): State<Context, List<CParam>> = {
        !params.mapM { param ->
            {
                val type = !checkTerm(param.type, TYPE)
                val vType = !lift({ normalizer }, evalTerm(type))
                val lower = param.lower?.let { !checkTerm(it, vType) }
                val vLower = lower?.let { !lift({ normalizer }, evalTerm(it)) }
                val upper = param.upper?.let { !checkTerm(it, vType) }
                val vUpper = upper?.let { !lift({ normalizer }, evalTerm(it)) }
                !modify { bind(param.id, Entry(param.termRelevant, param.name, vLower, vUpper, param.typeRelevant, vType, stage)) }
                CParam(param.termRelevant, param.name, lower, upper, param.typeRelevant, type, param.id)
            }
        }
    }

    /**
     * Infers the type of the [module] under the context.
     */
    private fun inferModule(module: SModule): State<Context, Pair<CModule, CVModule>> = {
        when (module) {
            is SModule.Var -> when (val item = items[module.name]) {
                is CItem.Mod -> CModule.Var(module.name, module.id) to item.type
                else -> {
                    diagnose(Diagnostic.ModNotFound(module.name, module.id))
                    CModule.Var(module.name, module.id) to TODO()
                }
            }
            is SModule.Str -> {
                val (items, signatures) = (!module.items.mapM { inferItem(it) }).unzip()
                CModule.Str(items, module.id) to CVModule.Sig(signatures, null)
            }
            is SModule.Sig -> {
                val signatures = !module.signatures.mapM { elabSignature(it) }
                CModule.Sig(signatures, module.id) to CVModule.Type(null)
            }
            is SModule.Type -> CModule.Type(module.id) to CVModule.Type(null)
        }
    }

    /**
     * Checks the [module] against the [type] under the context.
     */
    private fun checkModule(module: SModule, type: CVModule): State<Context, CModule> = {
        when {
            module is SModule.Str && type is CVModule.Sig -> {
                val items = !(module.items zip type.signatures).mapM { (item, signature) -> checkItem(item, signature) }
                CModule.Str(items, module.id)
            }
            else -> {
                val (inferred, inferredType) = !inferModule(module)
                if (!(!lift({ normalizer }, unifyModules(inferredType, type)))) {
                    diagnose(Diagnostic.ModMismatch(module.id))
                }
                inferred
            }
        }
    }

    /**
     * Checks if the [module1] and the [module2] can be unified under the normalizer.
     */
    private fun unifyModules(module1: CVModule, module2: CVModule): State<Normalizer, Boolean> = {
        when (module1) {
            is CVModule.Var ->
                module2 is CVModule.Var &&
                        module1.name == module2.name
            is CVModule.Sig ->
                module2 is CVModule.Sig &&
                        !(module1.signatures zip module2.signatures).allM { (signature1, signature2) -> unifySignatures(signature1, signature2) }
            is CVModule.Str -> false // TODO
            is CVModule.Type -> module2 is CVModule.Type
        }
    }

    /**
     * Checks if the [signature1] and the [signature2] can be unified under the normalizer.
     */
    private fun unifySignatures(signature1: CVSignature, signature2: CVSignature): State<Normalizer, Boolean> = {
        when (signature1) {
            is CVSignature.Def ->
                signature2 is CVSignature.Def &&
                        signature1.name == signature2.name &&
                        signature1.params.size == signature2.params.size &&
                        !(signature1.params zip signature2.params).allM { (param1, param2) ->
                            unifyTerms(!evalTerm(param2.type), !evalTerm(param1.type)).also {
                                !modify { bind(lazyOf(CVTerm.Var("", size))) }
                            }
                        } &&
                        !unifyTerms(!evalTerm(signature1.resultant), !evalTerm(signature2.resultant))
            is CVSignature.Mod ->
                signature2 is CVSignature.Mod &&
                        signature1.name == signature2.name &&
                        !unifyModules(signature1.type, signature2.type)
            is CVSignature.Test ->
                signature2 is CVSignature.Test &&
                        signature1.name == signature2.name
        }
    }

    /**
     * Elaborates the [signature] under the context.
     */
    private fun elabSignature(signature: SSignature): State<Context, CSignature> = {
        when (signature) {
            is SSignature.Def ->
                !restore {
                    !modify { copy(termRelevant = false, typeRelevant = true) }
                    val params = !bindParams(signature.params)
                    val resultant = !checkTerm(signature.resultant, TYPE)
                    CSignature.Def(signature.name, params, resultant, signature.id)
                }
            is SSignature.Mod -> {
                val type = !checkModule(signature.type, CVModule.Type(null))
                CSignature.Mod(signature.name, type, signature.id)
            }
            is SSignature.Test -> CSignature.Test(signature.name, signature.id)
        }
    }

    /**
     * Infers the type of the [term] under the context.
     */
    private fun inferTerm(term: STerm): State<Context, Typing> = {
        when (term) {
            is STerm.Hole -> {
                completions[term.id] = (!get()).entries.map { it.name to it.type }
                Typing(CTerm.Hole(term.id), diagnose(Diagnostic.TermExpected(printTerm(!lift({ normalizer }, quoteTerm(END))), term.id)))
            }
            is STerm.Meta -> {
                val type = !lift({ normalizer }, fresh(term.id))
                val term = !checkTerm(term, type)
                Typing(term, type)
            }
            is STerm.Anno ->
                !restore {
                    !modify { copy(termRelevant = false, typeRelevant = true) }
                    val type = !lift({ normalizer }, evalTerm(!checkTerm(term.type, TYPE)))
                    val term = !checkTerm(term.element, type)
                    Typing(term, type)
                }
            is STerm.Var ->
                !gets {
                    val level = lookup(term.name)
                    val type = when (level) {
                        -1 -> diagnose(Diagnostic.VarNotFound(term.name, term.id))
                        else -> {
                            val entry = entries[level]
                            var type = entry.type
                            if (stage != entry.stage) type = diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id))
                            if (termRelevant && !entry.termRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                            if (typeRelevant && !entry.typeRelevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                            normalizer.checkRepr(term.id, type)
                            type
                        }
                    }
                    Typing(CTerm.Var(term.name, level, term.id), type)
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
                val elements = !term.elements.mapM { checkTerm(it, BYTE) }
                Typing(CTerm.ByteArrayOf(elements, term.id), BYTE_ARRAY)
            }
            is STerm.IntArrayOf -> {
                val elements = !term.elements.mapM { checkTerm(it, INT) }
                Typing(CTerm.IntArrayOf(elements, term.id), INT_ARRAY)
            }
            is STerm.LongArrayOf -> {
                val elements = !term.elements.mapM { checkTerm(it, LONG) }
                Typing(CTerm.LongArrayOf(elements, term.id), LONG_ARRAY)
            }
            is STerm.ListOf -> if (term.elements.isEmpty()) {
                Typing(CTerm.ListOf(emptyList(), term.id), CVTerm.List(lazyOf(END), lazyOf(CVTerm.IntOf(0))))
            } else { // TODO: use union of element types
                val head = !inferTerm(term.elements.first())
                val tail = !term.elements.drop(1).mapM { checkTerm(it, head.type) }
                val elements = listOf(head.term) + tail
                val size = CVTerm.IntOf(elements.size)
                Typing(CTerm.ListOf(elements, term.id), CVTerm.List(lazyOf(head.type), lazyOf(size)))
            }
            is STerm.CompoundOf -> {
                val elements = !term.elements.mapM { (name, element) ->
                    {
                        val element = !(inferTerm(element))
                        name to element
                    }
                }
                Typing(
                    CTerm.CompoundOf(elements.map { (name, element) -> name to element.term }.toLinkedHashMap(), term.id),
                    CVTerm.Compound(elements.map { (name, element) -> name to CEntry(true, !lift({ normalizer }, quoteTerm(element.type)), null) }.toLinkedHashMap())
                )
            }
            is STerm.RefOf -> {
                val element = !inferTerm(term.element)
                Typing(CTerm.RefOf(element.term, term.id), CVTerm.Ref(lazyOf(element.type)))
            }
            is STerm.Refl -> {
                val left = lazy { !lift({ normalizer }, fresh(term.id)) }
                Typing(CTerm.Refl(term.id), CVTerm.Eq(left, left))
            }
            is STerm.CodeOf ->
                !restore {
                    !modify { copy(stage = stage + 1) }
                    val element = !inferTerm(term.element)
                    Typing(CTerm.CodeOf(element.term, term.id), CVTerm.Code(lazyOf(element.type)))
                }
            is STerm.Splice ->
                !restore {
                    !modify { copy(stage = stage - 1) }
                    val element = !inferTerm(term.element)
                    val type = when (val type = !gets { normalizer.force(element.type) }) {
                        is CVTerm.Code -> type.element.value
                        else -> type.also {
                            !lift({ normalizer }) { !unifyTerms(it, CVTerm.Code(lazyOf(!fresh(freshId())))) }
                        }
                    }
                    Typing(CTerm.Splice(element.term, term.id), type)
                }
            is STerm.Or -> {
                val variants = !term.variants.mapM { checkTerm(it, TYPE) }
                Typing(CTerm.Or(variants, term.id), TYPE)
            }
            is STerm.And -> {
                val variants = !term.variants.mapM { checkTerm(it, TYPE) }
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
                val element = !checkTerm(term.element, TYPE)
                !restore {
                    !modify { copy(termRelevant = false, typeRelevant = true) }
                    val size = !checkTerm(term.size, INT)
                    Typing(CTerm.List(element, size, term.id), TYPE)
                }
            }
            is STerm.Compound ->
                !restore {
                    val elements = !term.elements.mapM { entry ->
                        {
                            val element = !checkTerm(entry.type, TYPE)
                            !modify { bind(entry.id, Entry(false, entry.name.text, END, ANY, true, !lift({ normalizer }, evalTerm(element)), stage)) }
                            entry.name to CEntry(entry.relevant, element, entry.id)
                        }
                    }
                    Typing(CTerm.Compound(elements.toLinkedHashMap(), term.id), TYPE)
                }
            is STerm.Ref -> {
                val element = !checkTerm(term.element, TYPE)
                Typing(CTerm.Ref(element, term.id), TYPE)
            }
            is STerm.Eq -> {
                val left = !inferTerm(term.left)
                val right = !checkTerm(term.right, left.type)
                Typing(CTerm.Eq(left.term, right, term.id), TYPE)
            }
            is STerm.Code -> {
                val element = !checkTerm(term.element, TYPE)
                Typing(CTerm.Code(element, term.id), TYPE)
            }
            is STerm.Type -> Typing(CTerm.Type(term.id), TYPE)
            else -> {
                val computation = !inferComputation(term)
                if (computation.effs.isNotEmpty()) {
                    diagnose(Diagnostic.EffMismatch(emptyList(), computation.effs.map { printEff(it) }, term.id))
                }
                computation
            }
        }.also {
            types[term.id] = it.type
        }
    }

    /**
     * Infers the type of the [computation] under the context.
     */
    private fun inferComputation(computation: STerm): State<Context, Typing> = {
        when (computation) {
            is STerm.Def -> when (val item = items[computation.name]) {
                is CItem.Def -> {
                    if (item.params.size != computation.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(item.params.size, computation.arguments.size, computation.id))
                    }
                    !lift({ normalizer }) {
                        val arguments = !(computation.arguments zip item.params).mapM { (argument, param) ->
                            {
                                val id = argument.id
                                val argument = !checkTerm(argument, !evalTerm(param.type))
                                val vArgument = !evalTerm(argument)
                                param.lower?.let { !evalTerm(it) }?.also { lower ->
                                    if (!(!subtypeTerms(lower, vArgument))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(vArgument)), printTerm(!quoteTerm(lower)), id))
                                }
                                param.upper?.let { !evalTerm(it) }?.also { upper ->
                                    if (!(!subtypeTerms(vArgument, upper))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(upper)), printTerm(!quoteTerm(vArgument)), id))
                                }
                                !modify { bind(lazyOf(vArgument)) }
                                argument
                            }
                        }
                        val resultant = !evalTerm(item.resultant)
                        Typing(CTerm.Def(computation.name, arguments, computation.id), resultant, item.effs)
                    }
                }
                else -> Typing(CTerm.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id)))
            }
            is STerm.Let -> {
                val init = !inferComputation(computation.init)
                !restore {
                    !modify { bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, true, init.type, stage)) }
                    val body = !inferComputation(computation.body)
                    Typing(CTerm.Let(computation.name, init.term, body.term, computation.id), body.type, init.effs + body.effs)
                }
            }
            is STerm.Match -> {
                val type = !lift({ normalizer }, fresh(computation.id)) // TODO: use union of element types
                val scrutinee = !inferTerm(computation.scrutinee)
                val clauses = !computation.clauses.mapM { (pat, body) ->
                    restore {
                        val pat = !checkPat(pat, scrutinee.type)
                        val body = !checkTerm(body, type) /* TODO: effect */
                        pat to body
                    }
                }
                !checkExhaustiveness(clauses.map { it.first }, scrutinee.type, computation.id)
                Typing(CTerm.Match(scrutinee.term, clauses, computation.id), type)
            }
            is STerm.FunOf -> {
                val types = !lift({ normalizer }, { !computation.params.mapM { fresh(computation.id) } })
                !restore {
                    !(computation.params zip types).forEachM { (param, type) ->
                        modify { bind(param.id, Entry(true /* TODO */, param.text, END, ANY, true, type, stage)) }
                    }
                    val body = !inferComputation(computation.body)
                    !lift({ normalizer }) {
                        val params = (computation.params zip types).map { (param, type) ->
                            CParam(true /* TODO */, param.text, !quoteTerm(END), !quoteTerm(ANY), true, !quoteTerm(type), param.id)
                        }
                        Typing(CTerm.FunOf(computation.params, body.term, computation.id), CVTerm.Fun(params, !quoteTerm(body.type), body.effs))
                    }
                }
            }
            is STerm.Apply -> {
                val function = !inferComputation(computation.function)
                val type = !lift({ normalizer }) {
                    when (val type = (!get()).force(function.type)) {
                        is CVTerm.Fun -> type
                        else -> {
                            val params = computation.arguments.map {
                                CParam(true, "", null, null, true, !quoteTerm(!fresh(freshId())), freshId())
                            }
                            val vResultant = !fresh(freshId())
                            val resultant = !quoteTerm(vResultant)
                            val effs = emptySet<CEff>() // TODO
                            !unifyTerms(type, CVTerm.Fun(params, resultant, effs, null))
                            CVTerm.Fun(params, resultant, effs, type.id)
                        }
                    }
                }
                if (type.params.size != computation.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, computation.arguments.size, computation.id))
                }
                !lift({ normalizer }) {
                    val arguments = !(computation.arguments zip type.params).mapM { (argument, param) ->
                        {
                            val tArgument = !checkTerm(argument, !evalTerm(param.type))
                            val vArgument = !evalTerm(tArgument)
                            param.lower?.let { !evalTerm(it) }?.also { lower ->
                                if (!(!subtypeTerms(lower, vArgument))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(vArgument)), printTerm(!quoteTerm(lower)), argument.id))
                            }
                            param.upper?.let { !evalTerm(it) }?.also { upper ->
                                if (!(!subtypeTerms(vArgument, upper))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(upper)), printTerm(!quoteTerm(vArgument)), argument.id))
                            }
                            !modify { bind(lazyOf(vArgument)) }
                            tArgument
                        }
                    }
                    val resultant = !evalTerm(type.resultant)
                    Typing(CTerm.Apply(function.term, arguments, computation.id), resultant, type.effs)
                }
            }
            is STerm.Fun ->
                !restore {
                    val params = !bindParams(computation.params)
                    val resultant = !checkTerm(computation.resultant, TYPE) /* TODO: check effects */
                    val effs = computation.effs.map { elabEff(it) }.toSet()
                    Typing(CTerm.Fun(params, resultant, effs, computation.id), TYPE)
                }
            else -> !inferTerm(computation)
        }
    }

    /**
     * Checks the [term] against the [type] under the context.
     */
    private fun checkTerm(term: STerm, type: CVTerm): State<Context, CTerm> = {
        val type = !gets {
            normalizer.force(type).also {
                types[term.id] = it
            }
        }
        when {
            term is STerm.Hole -> {
                completions[term.id] = (!get()).entries.map { it.name to it.type }
                diagnose(Diagnostic.TermExpected(printTerm(!lift({ normalizer }, quoteTerm(type))), term.id))
                CTerm.Hole(term.id)
            }
            term is STerm.Meta ->
                !lift({ normalizer }) {
                    !quoteTerm(!fresh(term.id))
                }
            term is STerm.ListOf && type is CVTerm.List -> {
                val elements = !term.elements.mapM { checkTerm(it, type.element.value) }
                when (val size = !gets { normalizer.force(type.size.value) }) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                    else -> {}
                }
                CTerm.ListOf(elements, term.id)
            }
            term is STerm.CompoundOf && type is CVTerm.Compound ->
                !restore {
                    val elements = !(term.elements zip type.elements.values).mapM { (element, entry) ->
                        {
                            val vType = !lift({ normalizer }, evalTerm(entry.type))
                            val tElement = !checkTerm(element.second, vType)
                            val vElement = !lift({ normalizer }, evalTerm(tElement))
                            !modify { bind(element.first.id, Entry(entry.relevant, element.first.text, END, ANY, true, vType, stage), vElement) }
                            element.first to tElement
                        }
                    }
                    CTerm.CompoundOf(elements.toLinkedHashMap(), term.id)
                }
            term is STerm.RefOf && type is CVTerm.Ref -> {
                val element = !checkTerm(term.element, type.element.value)
                CTerm.RefOf(element, term.id)
            }
            term is STerm.CodeOf && type is CVTerm.Code ->
                !restore {
                    !modify { copy(stage = stage + 1) }
                    val element = !checkTerm(term.element, type.element.value)
                    CTerm.CodeOf(element, term.id)
                }
            else -> {
                val computation = !checkComputation(term, type, emptySet())
                computation.term
            }
        }
    }

    /**
     * Checks the [computation] against the [type] and the [effs] under the context.
     */
    private fun checkComputation(computation: STerm, type: CVTerm, effs: Set<CEff>): State<Context, Effecting> = {
        val type = !gets {
            normalizer.force(type).also {
                types[computation.id] = it
            }
        }
        when {
            computation is STerm.Let ->
                !restore {
                    val init = !inferComputation(computation.init)
                    !modify { bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, true, init.type, stage)) }
                    val body = !checkComputation(computation.body, type, effs)
                    val effs = init.effs + body.effs
                    Effecting(CTerm.Let(computation.name, init.term, body.term, computation.id), effs)
                }
            computation is STerm.Match -> {
                val scrutinee = !inferTerm(computation.scrutinee)
                val clauses = !computation.clauses.mapM { (pat, body) ->
                    restore {
                        val pat = !checkPat(pat, scrutinee.type)
                        val body = !checkComputation(body, type, effs)
                        pat to body
                    }
                }
                !checkExhaustiveness(clauses.map { it.first }, scrutinee.type, computation.id)
                val effs = clauses.flatMap { (_, body) -> body.effs }.toSet()
                Effecting(CTerm.Match(scrutinee.term, clauses.map { (pat, body) -> pat to body.term }, computation.id), effs)
            }
            computation is STerm.FunOf && type is CVTerm.Fun -> {
                if (type.params.size != computation.params.size) {
                    diagnose(Diagnostic.SizeMismatch(type.params.size, computation.params.size, computation.id))
                }
                !restore {
                    !modify { empty() }
                    !(computation.params zip type.params).forEachM { (name, param) ->
                        {
                            val lower = param.lower?.let { !lift({ normalizer }, evalTerm(it)) }
                            val upper = param.upper?.let { !lift({ normalizer }, evalTerm(it)) }
                            val type = !lift({ normalizer }, evalTerm(param.type))
                            !modify { bind(name.id, Entry(param.termRelevant, name.text, lower, upper, param.typeRelevant, type, stage)) }
                        }
                    }
                    val resultant = !checkComputation(computation.body, !lift({ normalizer }, evalTerm(type.resultant)), effs)
                    Effecting(CTerm.FunOf(computation.params, resultant.term, computation.id), emptySet())
                }
            }
            else -> {
                val id = computation.id
                val computation = !inferComputation(computation)
                val isSubtype = !subtypeTerms(computation.type, type)
                if (!isSubtype) {
                    types[id] = END
                    val expected = printTerm(!lift({ normalizer }, quoteTerm(type)))
                    val actual = printTerm(!lift({ normalizer }, quoteTerm(computation.type)))
                    diagnose(Diagnostic.TermMismatch(expected, actual, id))
                }
                if (!(effs.containsAll(computation.effs))) {
                    diagnose(Diagnostic.EffMismatch(effs.map { printEff(it) }, computation.effs.map { printEff(it) }, id))
                }
                Effecting(computation.term, computation.effs)
            }
        }
    }

    /**
     * Checks if the [term1] and the [term2] can be unified under the normalizer.
     */
    private fun unifyTerms(term1: CVTerm, term2: CVTerm): State<Normalizer, Boolean> = {
        val (term1, term2) = !gets { force(term1) to force(term2) }
        when {
            term1.id != null && term2.id != null && term1.id == term2.id -> true
            term1 is CVTerm.Meta && term2 is CVTerm.Meta && term1.index == term2.index -> true
            term1 is CVTerm.Meta ->
                !(when (val solved1 = !gets { getSolution(term1.index) }) {
                    null ->
                        gets {
                            solve(term1.index, term2)
                            true
                        }
                    else -> unifyTerms(solved1, term2)
                })
            term2 is CVTerm.Meta -> !unifyTerms(term2, term1)
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
                        !(term1.elements zip term2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.IntArrayOf && term2 is CVTerm.IntArrayOf ->
                term1.elements.size == term2.elements.size &&
                        !(term1.elements zip term2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.LongArrayOf && term2 is CVTerm.LongArrayOf ->
                term1.elements.size == term2.elements.size &&
                        !(term1.elements zip term2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.ListOf && term2 is CVTerm.ListOf ->
                term1.elements.size == term2.elements.size &&
                        !(term1.elements zip term2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.CompoundOf && term2 is CVTerm.CompoundOf ->
                term1.elements.size == term2.elements.size &&
                        !(term1.elements.entries zip term2.elements.entries).allM { (entry1, entry2) ->
                            {
                                entry1.key.text == entry2.key.text &&
                                        !unifyTerms(entry1.value.value, entry2.value.value)
                            }
                        }
            term1 is CVTerm.RefOf && term2 is CVTerm.RefOf -> !unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Refl && term2 is CVTerm.Refl -> true
            term1 is CVTerm.FunOf && term2 is CVTerm.FunOf ->
                term1.params.size == term2.params.size && run {
                    !term1.params.forEachM { param ->
                        modify { bind(lazyOf(CVTerm.Var(param.text, size))) }
                    }
                    !unifyTerms(!evalTerm(term1.body), !evalTerm(term2.body))
                }
            term1 is CVTerm.Apply && term2 is CVTerm.Apply ->
                !unifyTerms(term1.function, term2.function) &&
                        !(term1.arguments zip term2.arguments).allM { unifyTerms(it.first.value, it.second.value) }
            term1 is CVTerm.CodeOf && term2 is CVTerm.CodeOf -> !unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Splice && term2 is CVTerm.Splice -> !unifyTerms(term1.element.value, term2.element.value)
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
                !unifyTerms(term1.element.value, term2.element.value) &&
                        !unifyTerms(term1.size.value, term2.size.value)
            term1 is CVTerm.Compound && term2 is CVTerm.Compound ->
                term1.elements.size == term2.elements.size &&
                        !(term1.elements.entries zip term2.elements.entries).allM { (entry1, entry2) ->
                            {
                                !modify { bind(lazyOf(CVTerm.Var(entry1.key.text, size))) }
                                entry1.key.text == entry2.key.text &&
                                        entry1.value.relevant == entry2.value.relevant &&
                                        !unifyTerms(!evalTerm(entry1.value.type), !evalTerm(entry2.value.type))
                            }
                        }
            term1 is CVTerm.Ref && term2 is CVTerm.Ref -> !unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Eq && term2 is CVTerm.Eq ->
                !unifyTerms(term1.left.value, term2.left.value) &&
                        !unifyTerms(term1.right.value, term2.right.value)
            term1 is CVTerm.Fun && term2 is CVTerm.Fun ->
                term1.params.size == term2.params.size &&
                        term1.effs == term2.effs &&
                        !(term1.params zip term2.params).allM { (param1, param2) ->
                            {
                                !modify { bind(lazyOf(CVTerm.Var("", size))) }
                                !(unifyTerms(!evalTerm(param2.type), !evalTerm(param1.type)))
                            }
                        } &&
                        !unifyTerms(!evalTerm(term1.resultant), !evalTerm(term2.resultant))
            term1 is CVTerm.Code && term2 is CVTerm.Code -> !unifyTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Type && term2 is CVTerm.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [term1] is a subtype of the [term2] under the context.
     */
    private fun subtypeTerms(term1: CVTerm, term2: CVTerm): State<Context, Boolean> = {
        val (term1, term2) = !(gets { normalizer.force(term1) to normalizer.force(term2) })
        when {
            term1 is CVTerm.Var && term2 is CVTerm.Var && term1.level == term2.level -> true
            term1 is CVTerm.Var && !gets { entries[term1.level].upper } != null -> {
                val upper = !gets { entries[term1.level].upper!! }
                !subtypeTerms(upper, term2)
            }
            term2 is CVTerm.Var && !gets { entries[term2.level].lower } != null -> {
                val lower = !gets { entries[term2.level].lower!! }
                !subtypeTerms(term1, lower)
            }
            term1 is CVTerm.Apply && term2 is CVTerm.Apply ->
                !subtypeTerms(term1.function, term2.function) &&
                        !(term1.arguments zip term2.arguments).allM { (argument1, argument2) ->
                            lift({ normalizer }, unifyTerms(argument1.value, argument2.value)) // pointwise subtyping
                        }
            term1 is CVTerm.Or -> !term1.variants.allM { subtypeTerms(it.value, term2) }
            term2 is CVTerm.Or -> !term2.variants.anyM { subtypeTerms(term1, it.value) }
            term2 is CVTerm.And -> !term2.variants.allM { subtypeTerms(term1, it.value) }
            term1 is CVTerm.And -> !term1.variants.anyM { subtypeTerms(it.value, term2) }
            term1 is CVTerm.List && term2 is CVTerm.List ->
                !subtypeTerms(term1.element.value, term2.element.value) &&
                        !lift({ normalizer }, unifyTerms(term1.size.value, term2.size.value))
            term1 is CVTerm.Compound && term2 is CVTerm.Compound ->
                !restore {
                    !term2.elements.entries.allM { (key2, element2) ->
                        {
                            val element1 = term1.elements[key2]
                            if (element1 != null) {
                                element1.relevant == element2.relevant && run {
                                    val upper1 = !lift({ normalizer }, evalTerm(element1.type))
                                    !subtypeTerms(upper1, !lift({ normalizer }, evalTerm(element2.type))).also {
                                        !modify { bindUnchecked(Entry(false, key2.text, null, upper1, true, TYPE, stage)) }
                                    }
                                }
                            } else false
                        }
                    }
                }
            term1 is CVTerm.Ref && term2 is CVTerm.Ref -> !subtypeTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Fun && term2 is CVTerm.Fun ->
                term1.params.size == term2.params.size &&
                        term2.effs.containsAll(term1.effs) &&
                        !restore {
                            !(term1.params zip term2.params).allM { (param1, param2) ->
                                {
                                    !modify { bindUnchecked(Entry(param1.termRelevant, "", null, null /* TODO */, param1.typeRelevant, TYPE, stage)) }
                                    param1.termRelevant == param2.termRelevant &&
                                            param1.typeRelevant == param2.typeRelevant &&
                                            !subtypeTerms(!lift({ normalizer }, evalTerm(param2.type)), !lift({ normalizer }, evalTerm(param1.type)))
                                }
                            } &&
                                    !(subtypeTerms(!lift({ normalizer }, evalTerm(term1.resultant)), !lift({ normalizer }, evalTerm(term2.resultant))))
                        }
            term1 is CVTerm.Code && term2 is CVTerm.Code -> !subtypeTerms(term1.element.value, term2.element.value)
            else -> !lift({ normalizer }, unifyTerms(term1, term2))
        }
    }

    private fun checkExhaustiveness(patterns: List<CPat>, type: CVTerm, id: Id): State<Context, Unit> = {
        fun check(patterns: List<CPat>, type: CVTerm): Boolean =
            patterns.filterIsInstance<CPat.Var>().isNotEmpty() || (when (val type = !gets { normalizer.force(type) }) {
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
    private fun inferPat(pat: SPat): State<Context, Pair<CPat, CVTerm>> = {
        when (pat) {
            is SPat.Var -> {
                val type = !lift({ normalizer }, fresh(pat.id))
                !modify { bind(pat.id, Entry(true /* TODO */, pat.name, END, ANY, true, type, stage)) }
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
                val elements = !pat.elements.mapM { element -> checkPat(element, BYTE) }
                CPat.ByteArrayOf(elements, pat.id) to BYTE_ARRAY
            }
            is SPat.IntArrayOf -> {
                val elements = !pat.elements.mapM { element -> checkPat(element, INT) }
                CPat.IntArrayOf(elements, pat.id) to INT_ARRAY
            }
            is SPat.LongArrayOf -> {
                val elements = !pat.elements.mapM { element -> checkPat(element, LONG) }
                CPat.LongArrayOf(elements, pat.id) to LONG_ARRAY
            }
            is SPat.ListOf -> {
                if (pat.elements.isEmpty()) {
                    CPat.ListOf(emptyList(), pat.id) to END
                } else { // TODO: use union of element types
                    val (head, headType) = !inferPat(pat.elements.first())
                    val elements = !pat.elements.drop(1).mapM { element -> checkPat(element, headType) }
                    val size = CVTerm.IntOf(elements.size)
                    CPat.ListOf(listOf(head) + elements, pat.id) to CVTerm.List(lazyOf(headType), lazyOf(size))
                }
            }
            is SPat.CompoundOf -> {
                val elements = !pat.elements.mapM { (name, element) ->
                    {
                        val (element, elementType) = !inferPat(element)
                        Triple(name, element, elementType)
                    }
                }
                val elementTerms = elements.map { (name, element, _) -> name to element }.toLinkedHashMap()
                val elementTypes = elements.map { (name, _, type) -> name to CEntry(true, !lift({ normalizer }, quoteTerm(type)), null) }.toLinkedHashMap()
                CPat.CompoundOf(elementTerms, pat.id) to CVTerm.Compound(elementTypes)
            }
            is SPat.RefOf -> {
                val (element, elementType) = !inferPat(pat.element)
                CPat.RefOf(element, pat.id) to CVTerm.Ref(lazyOf(elementType))
            }
            is SPat.Refl -> {
                val left = lazy { !lift({ normalizer }, fresh(pat.id)) }
                CPat.Refl(pat.id) to CVTerm.Eq(left, left)
            }
            is SPat.Or -> {
                val variants = !pat.variants.mapM { checkPat(it, TYPE) }
                CPat.Or(variants, pat.id) to TYPE
            }
            is SPat.And -> {
                val variants = !pat.variants.mapM { checkPat(it, TYPE) }
                CPat.And(variants, pat.id) to TYPE
            }
            is SPat.Unit -> CPat.Unit(pat.id) to TYPE
            is SPat.Bool -> CPat.Bool(pat.id) to TYPE
            is SPat.Byte -> CPat.Byte(pat.id) to TYPE
            is SPat.Short -> CPat.Short(pat.id) to TYPE
            is SPat.Int -> CPat.Int(pat.id) to TYPE
            is SPat.Long -> CPat.Long(pat.id) to TYPE
            is SPat.Float -> CPat.Float(pat.id) to TYPE
            is SPat.Double -> CPat.Double(pat.id) to TYPE
            is SPat.String -> CPat.String(pat.id) to TYPE
            is SPat.ByteArray -> CPat.ByteArray(pat.id) to TYPE
            is SPat.IntArray -> CPat.IntArray(pat.id) to TYPE
            is SPat.LongArray -> CPat.LongArray(pat.id) to TYPE
            is SPat.List -> {
                val element = !checkPat(pat.element, TYPE)
                val size = !checkPat(pat.size, INT)
                CPat.List(element, size, pat.id) to TYPE
            }
            is SPat.Compound -> {
                val elements = !pat.elements.mapM { (name, element) ->
                    {
                        name to !checkPat(element, TYPE)
                    }
                }
                CPat.Compound(elements.toLinkedHashMap(), pat.id) to TYPE
            }
            is SPat.Ref -> {
                val element = !checkPat(pat.element, TYPE)
                CPat.Ref(element, pat.id) to TYPE
            }
            is SPat.Eq -> {
                val left = !inferPat(pat.left)
                val right = !checkPat(pat.right, left.second)
                CPat.Eq(left.first, right, pat.id) to TYPE
            }
            is SPat.Code -> {
                val element = !checkPat(pat.element, TYPE)
                CPat.Code(element, pat.id) to TYPE
            }
            is SPat.Type -> CPat.Type(pat.id) to TYPE
        }.also { (_, type) ->
            types[pat.id] = type
        }
    }

    /**
     * Checks the [pat] against the [type] under the context.
     */
    private fun checkPat(pat: SPat, type: CVTerm): State<Context, CPat> = {
        val type = !gets {
            normalizer.force(type).also {
                types[pat.id] = it
            }
        }
        when {
            pat is SPat.Var -> {
                !modify { bind(pat.id, Entry(true /* TODO */, pat.name, END, ANY, true, type, stage)) }
                CPat.Var(pat.name, pat.id)
            }
            pat is SPat.ListOf && type is CVTerm.List -> {
                val elements = !pat.elements.mapM { element ->
                    checkPat(element, type.element.value)
                }
                when (val size = !gets { normalizer.force(type.size.value) }) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, pat.id))
                    else -> {}
                }
                CPat.ListOf(elements, pat.id)
            }
            pat is SPat.CompoundOf && type is CVTerm.Compound -> {
                val elements = !(pat.elements zip type.elements.entries).mapM { (element, entry) ->
                    {
                        val type = !lift({ normalizer }, evalTerm(entry.value.type))
                        val pat = !checkPat(element.second, type)
                        element.first to pat
                    }
                }
                CPat.CompoundOf(elements.toLinkedHashMap(), pat.id)
            }
            pat is SPat.RefOf && type is CVTerm.Ref -> {
                val element = !checkPat(pat.element, type.element.value)
                CPat.RefOf(element, pat.id)
            }
            pat is SPat.Refl && type is CVTerm.Eq -> {
                val normalizer = !lift({ normalizer }, {
                    !match(type.left.value, type.right.value)
                    !get()
                })
                !modify { copy(normalizer = normalizer) }
                CPat.Refl(pat.id)
            }
            else -> {
                val (inferred, inferredType) = !inferPat(pat)
                val isSubtype = !subtypeTerms(inferredType, type)
                if (!isSubtype) {
                    types[pat.id] = END
                    val expected = printTerm(!lift({ normalizer }, quoteTerm(type)))
                    val actual = printTerm(!lift({ normalizer }, quoteTerm(inferredType)))
                    diagnose(Diagnostic.TermMismatch(expected, actual, pat.id))
                }
                inferred
            }
        }
    }

    /**
     * Matches the [term1] and the [term2].
     */
    private fun match(term1: CVTerm, term2: CVTerm): State<Normalizer, Unit> = {
        val (term1, term2) = !gets { force(term1) to force(term2) }
        when {
            term2 is CVTerm.Var -> !modify { subst(term2.level, lazyOf(term1)) }
            term1 is CVTerm.Var -> !modify { subst(term1.level, lazyOf(term2)) }
            term1 is CVTerm.ByteArrayOf && term2 is CVTerm.ByteArrayOf -> !(term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.IntArrayOf && term2 is CVTerm.IntArrayOf -> !(term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.LongArrayOf && term2 is CVTerm.LongArrayOf -> !(term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.ListOf && term2 is CVTerm.ListOf -> !(term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
            term1 is CVTerm.CompoundOf && term2 is CVTerm.CompoundOf -> !(term1.elements.entries zip term2.elements.entries).forEachM { (entry1, entry2) ->
                if (entry1.key == entry2.key) match(entry1.value.value, entry2.value.value) else pure(Unit)
            }
            term1 is CVTerm.RefOf && term2 is CVTerm.RefOf -> !match(term1.element.value, term2.element.value)
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
    private fun Normalizer.checkRepr(id: Id, type: CVTerm) {
        fun join(term1: CVTerm, term2: CVTerm): Boolean {
            val term1 = force(term1)
            val term2 = force(term2)
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
            meta: Boolean = this.meta,
            stage: Int = this.stage,
            termRelevant: Boolean = this.termRelevant,
            typeRelevant: Boolean = this.typeRelevant,
        ): Context = Context(entries, normalizer, meta, stage, termRelevant, typeRelevant)

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

        override operator fun invoke(config: Config, input: Pair<SItem, Map<String, CItem>>): Result = Elab(input.second).run {
            val normalizer = Normalizer(persistentListOf(), items, solutions)
            val (item, _) = inferItem(input.first).run(Context(persistentListOf(), normalizer, false, 0, true, true))
            Result(item, types, normalizer, diagnostics, completions)
        }
    }
}
