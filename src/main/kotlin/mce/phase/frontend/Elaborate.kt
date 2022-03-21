package mce.phase.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.ast.Id
import mce.ast.freshId
import mce.phase.*
import mce.util.State
import mce.util.run
import mce.util.toLinkedHashMap
import mce.ast.core.Effect as CEffect
import mce.ast.core.Entry as CEntry
import mce.ast.core.Item as CItem
import mce.ast.core.Modifier as CModifier
import mce.ast.core.Module as CModule
import mce.ast.core.Parameter as CParameter
import mce.ast.core.Pattern as CPattern
import mce.ast.core.Signature as CSignature
import mce.ast.core.Term as CTerm
import mce.ast.core.VModule as CVModule
import mce.ast.core.VSignature as CVSignature
import mce.ast.core.VTerm as CVTerm
import mce.ast.surface.Effect as SEffect
import mce.ast.surface.Item as SItem
import mce.ast.surface.Modifier as SModifier
import mce.ast.surface.Module as SModule
import mce.ast.surface.Parameter as SParameter
import mce.ast.surface.Pattern as SPattern
import mce.ast.surface.Signature as SSignature
import mce.ast.surface.Term as STerm

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
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
        val modifiers = item.modifiers.mapTo(mutableSetOf()) { elaborateModifier(it) }
        when (item) {
            is SItem.Def -> {
                !modify { copy(meta = item.modifiers.contains(SModifier.META), termRelevant = false, typeRelevant = true) }
                val parameters = !bindParameters(item.parameters)
                val resultant = !checkTerm(item.resultant, TYPE)
                val vResultant = !lift({ normalizer }, evalTerm(resultant))
                val effects = item.effects.map { elaborateEffect(it) }.toSet()
                val body = if (item.modifiers.contains(SModifier.BUILTIN)) {
                    CTerm.Hole(item.body.id)
                } else {
                    !modify { copy(termRelevant = true, typeRelevant = false) }
                    !checkTerm(item.body, vResultant)
                }
                (!get()).checkPhase(item.body.id, vResultant)
                CItem.Def(item.imports, item.exports, modifiers, item.name, parameters, resultant, effects, body, item.id) to CVSignature.Def(item.name, parameters, resultant, null)
            }
            is SItem.Mod -> {
                val type = !checkModule(item.type, CVModule.Type(null))
                val vType = !lift({ normalizer }, evalModule(type))
                val body = !checkModule(item.body, vType)
                CItem.Mod(item.imports, item.exports, modifiers, item.name, vType, body, item.id) to CVSignature.Mod(item.name, vType, null)
            }
            is SItem.Test -> {
                val body = !checkTerm(item.body, BOOL)
                CItem.Test(item.imports, item.exports, modifiers, item.name, body, item.id) to CVSignature.Test(item.name, null)
            }
        }
    }

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun checkItem(item: SItem, signature: CVSignature): State<Context, CItem> = {
        val (inferred, inferredSignature) = !inferItem(item)
        if (!(!lift({ Normalizer(persistentListOf(), items, solutions) }, unifySignatures(inferredSignature, signature)))) {
            diagnose(Diagnostic.SignatureMismatch(item.id)) // TODO: serialize signatures
        }
        inferred
    }

    /**
     * Elaborates the [modifier].
     */
    private fun elaborateModifier(modifier: SModifier): CModifier = when (modifier) {
        SModifier.BUILTIN -> CModifier.BUILTIN
        SModifier.META -> CModifier.META
    }

    /**
     * Binds the [parameters] to the context.
     */
    private fun bindParameters(parameters: List<SParameter>): State<Context, List<CParameter>> = {
        !parameters.mapM { parameter ->
            {
                val type = !checkTerm(parameter.type, TYPE)
                val vType = !lift({ normalizer }, evalTerm(type))
                val lower = parameter.lower?.let { !checkTerm(it, vType) }
                val vLower = lower?.let { !lift({ normalizer }, evalTerm(it)) }
                val upper = parameter.upper?.let { !checkTerm(it, vType) }
                val vUpper = upper?.let { !lift({ normalizer }, evalTerm(it)) }
                !modify { bind(parameter.id, Entry(parameter.termRelevant, parameter.name, vLower, vUpper, parameter.typeRelevant, vType, stage)) }
                CParameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
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
                val signatures = !module.signatures.mapM { elaborateSignature(it) }
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
                    diagnose(Diagnostic.ModuleMismatch(module.id))
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
                        signature1.parameters.size == signature2.parameters.size &&
                        !(signature1.parameters zip signature2.parameters).allM { (parameter1, parameter2) ->
                            unifyTerms(!evalTerm(parameter2.type), !evalTerm(parameter1.type)).also {
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
    private fun elaborateSignature(signature: SSignature): State<Context, CSignature> = {
        when (signature) {
            is SSignature.Def ->
                !restore {
                    !modify { copy(termRelevant = false, typeRelevant = true) }
                    val parameters = !bindParameters(signature.parameters)
                    val resultant = !checkTerm(signature.resultant, TYPE)
                    CSignature.Def(signature.name, parameters, resultant, signature.id)
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
                            normalizer.checkRepresentation(term.id, type)
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
            is STerm.BoxOf -> {
                val tTag = !checkTerm(term.tag, TYPE)
                val vTag = !lift({ normalizer }, evalTerm(tTag))
                val content = !checkTerm(term.content, vTag)
                Typing(CTerm.BoxOf(content, tTag, term.id), CVTerm.Box(lazyOf(vTag)))
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
            is STerm.Box -> {
                val content = !checkTerm(term.content, TYPE)
                Typing(CTerm.Box(content, term.id), TYPE)
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
                if (computation.effects.isNotEmpty()) {
                    diagnose(Diagnostic.EffectMismatch(emptyList(), computation.effects.map { printEffect(it) }, term.id))
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
                    if (item.parameters.size != computation.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(item.parameters.size, computation.arguments.size, computation.id))
                    }
                    !lift({ normalizer }) {
                        val arguments = !(computation.arguments zip item.parameters).mapM { (argument, parameter) ->
                            {
                                val id = argument.id
                                val argument = !checkTerm(argument, !evalTerm(parameter.type))
                                val vArgument = !evalTerm(argument)
                                parameter.lower?.let { !evalTerm(it) }?.also { lower ->
                                    if (!(!subtypeTerms(lower, vArgument))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(vArgument)), printTerm(!quoteTerm(lower)), id))
                                }
                                parameter.upper?.let { !evalTerm(it) }?.also { upper ->
                                    if (!(!subtypeTerms(vArgument, upper))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(upper)), printTerm(!quoteTerm(vArgument)), id))
                                }
                                !modify { bind(lazyOf(vArgument)) }
                                argument
                            }
                        }
                        val resultant = !evalTerm(item.resultant)
                        Typing(CTerm.Def(computation.name, arguments, computation.id), resultant, item.effects)
                    }
                }
                else -> Typing(CTerm.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id)))
            }
            is STerm.Let -> {
                val init = !inferComputation(computation.init)
                !restore {
                    !modify { bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, true, init.type, stage)) }
                    val body = !inferComputation(computation.body)
                    Typing(CTerm.Let(computation.name, init.term, body.term, computation.id), body.type, init.effects + body.effects)
                }
            }
            is STerm.Match -> {
                val type = !lift({ normalizer }, fresh(computation.id)) // TODO: use union of element types
                val scrutinee = !inferTerm(computation.scrutinee)
                val clauses = !computation.clauses.mapM { (pattern, body) ->
                    restore {
                        val pattern = !checkPattern(pattern, scrutinee.type)
                        val body = !checkTerm(body, type) /* TODO: effect */
                        pattern to body
                    }
                }
                Typing(CTerm.Match(scrutinee.term, clauses, computation.id), type)
            }
            is STerm.FunOf -> {
                val types = !lift({ normalizer }, { !computation.parameters.mapM { fresh(computation.id) } })
                !restore {
                    !(computation.parameters zip types).forEachM { (parameter, type) ->
                        modify { bind(parameter.id, Entry(true /* TODO */, parameter.text, END, ANY, true, type, stage)) }
                    }
                    val body = !inferComputation(computation.body)
                    !lift({ normalizer }) {
                        val parameters = (computation.parameters zip types).map { (parameter, type) ->
                            CParameter(true /* TODO */, parameter.text, !quoteTerm(END), !quoteTerm(ANY), true, !quoteTerm(type), parameter.id)
                        }
                        Typing(CTerm.FunOf(computation.parameters, body.term, computation.id), CVTerm.Fun(parameters, !quoteTerm(body.type), body.effects))
                    }
                }
            }
            is STerm.Apply -> {
                val function = !inferComputation(computation.function)
                val type = !lift({ normalizer }) {
                    when (val type = (!get()).force(function.type)) {
                        is CVTerm.Fun -> type
                        else -> {
                            val parameters = computation.arguments.map {
                                CParameter(true, "", null, null, true, !quoteTerm(!fresh(freshId())), freshId())
                            }
                            val vResultant = !fresh(freshId())
                            val resultant = !quoteTerm(vResultant)
                            val effects = emptySet<CEffect>() // TODO
                            !unifyTerms(type, CVTerm.Fun(parameters, resultant, effects, null))
                            CVTerm.Fun(parameters, resultant, effects, type.id)
                        }
                    }
                }
                if (type.parameters.size != computation.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.arguments.size, computation.id))
                }
                !lift({ normalizer }) {
                    val arguments = !(computation.arguments zip type.parameters).mapM { (argument, parameter) ->
                        {
                            val tArgument = !checkTerm(argument, !evalTerm(parameter.type))
                            val vArgument = !evalTerm(tArgument)
                            parameter.lower?.let { !evalTerm(it) }?.also { lower ->
                                if (!(!subtypeTerms(lower, vArgument))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(vArgument)), printTerm(!quoteTerm(lower)), argument.id))
                            }
                            parameter.upper?.let { !evalTerm(it) }?.also { upper ->
                                if (!(!subtypeTerms(vArgument, upper))) diagnose(Diagnostic.TermMismatch(printTerm(!quoteTerm(upper)), printTerm(!quoteTerm(vArgument)), argument.id))
                            }
                            !modify { bind(lazyOf(vArgument)) }
                            tArgument
                        }
                    }
                    val resultant = !evalTerm(type.resultant)
                    Typing(CTerm.Apply(function.term, arguments, computation.id), resultant, type.effects)
                }
            }
            is STerm.Fun ->
                !restore {
                    val parameters = !bindParameters(computation.parameters)
                    val resultant = !checkTerm(computation.resultant, TYPE) /* TODO: check effects */
                    val effects = computation.effects.map { elaborateEffect(it) }.toSet()
                    Typing(CTerm.Fun(parameters, resultant, effects, computation.id), TYPE)
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
     * Checks the [computation] against the [type] and the [effects] under the context.
     */
    private fun checkComputation(computation: STerm, type: CVTerm, effects: Set<CEffect>): State<Context, Effecting> = {
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
                    val body = !checkComputation(computation.body, type, effects)
                    val effects = init.effects + body.effects
                    Effecting(CTerm.Let(computation.name, init.term, body.term, computation.id), effects)
                }
            computation is STerm.Match -> {
                val scrutinee = !inferTerm(computation.scrutinee)
                val clauses = !computation.clauses.mapM { (pattern, body) ->
                    restore {
                        val pattern = !checkPattern(pattern, scrutinee.type)
                        val body = !checkComputation(body, type, effects)
                        pattern to body
                    }
                }
                val effects = clauses.flatMap { (_, body) -> body.effects }.toSet()
                Effecting(CTerm.Match(scrutinee.term, clauses.map { (pattern, body) -> pattern to body.term }, computation.id), effects)
            }
            computation is STerm.FunOf && type is CVTerm.Fun -> {
                if (type.parameters.size != computation.parameters.size) {
                    diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.parameters.size, computation.id))
                }
                !restore {
                    !modify { empty() }
                    !(computation.parameters zip type.parameters).forEachM { (name, parameter) ->
                        {
                            val lower = parameter.lower?.let { !lift({ normalizer }, evalTerm(it)) }
                            val upper = parameter.upper?.let { !lift({ normalizer }, evalTerm(it)) }
                            val type = !lift({ normalizer }, evalTerm(parameter.type))
                            !modify { bind(name.id, Entry(parameter.termRelevant, name.text, lower, upper, parameter.typeRelevant, type, stage)) }
                        }
                    }
                    val resultant = !checkComputation(computation.body, !lift({ normalizer }, evalTerm(type.resultant)), effects)
                    Effecting(CTerm.FunOf(computation.parameters, resultant.term, computation.id), emptySet())
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
                if (!(effects.containsAll(computation.effects))) {
                    diagnose(Diagnostic.EffectMismatch(effects.map { printEffect(it) }, computation.effects.map { printEffect(it) }, id))
                }
                Effecting(computation.term, computation.effects)
            }
        }
    }

    /**
     * Checks if the [term1] and the [term2] can be unified under the normalizer.
     */
    private fun unifyTerms(term1: CVTerm, term2: CVTerm): State<Normalizer, Boolean> = {
        val (value1, value2) = !gets { force(term1) to force(term2) }
        when {
            value1.id != null && value2.id != null && value1.id == value2.id -> true
            value1 is CVTerm.Meta && value2 is CVTerm.Meta && value1.index == value2.index -> true
            value1 is CVTerm.Meta ->
                !(when (val solved1 = !gets { getSolution(value1.index) }) {
                    null ->
                        gets {
                            solve(value1.index, value2)
                            true
                        }
                    else -> unifyTerms(solved1, value2)
                })
            value2 is CVTerm.Meta -> !unifyTerms(value2, value1)
            value1 is CVTerm.Var && value2 is CVTerm.Var -> value1.level == value2.level
            value1 is CVTerm.Def && value2 is CVTerm.Def && value1.name == value2.name -> true
            value1 is CVTerm.Match && value2 is CVTerm.Match -> false // TODO
            value1 is CVTerm.UnitOf && value2 is CVTerm.UnitOf -> true
            value1 is CVTerm.BoolOf && value2 is CVTerm.BoolOf -> value1.value == value2.value
            value1 is CVTerm.ByteOf && value2 is CVTerm.ByteOf -> value1.value == value2.value
            value1 is CVTerm.ShortOf && value2 is CVTerm.ShortOf -> value1.value == value2.value
            value1 is CVTerm.IntOf && value2 is CVTerm.IntOf -> value1.value == value2.value
            value1 is CVTerm.LongOf && value2 is CVTerm.LongOf -> value1.value == value2.value
            value1 is CVTerm.FloatOf && value2 is CVTerm.FloatOf -> value1.value == value2.value
            value1 is CVTerm.DoubleOf && value2 is CVTerm.DoubleOf -> value1.value == value2.value
            value1 is CVTerm.StringOf && value2 is CVTerm.StringOf -> value1.value == value2.value
            value1 is CVTerm.ByteArrayOf && value2 is CVTerm.ByteArrayOf ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            value1 is CVTerm.IntArrayOf && value2 is CVTerm.IntArrayOf ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            value1 is CVTerm.LongArrayOf && value2 is CVTerm.LongArrayOf ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            value1 is CVTerm.ListOf && value2 is CVTerm.ListOf ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
            value1 is CVTerm.CompoundOf && value2 is CVTerm.CompoundOf ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements.entries zip value2.elements.entries).allM { (entry1, entry2) ->
                            {
                                entry1.key.text == entry2.key.text &&
                                        !unifyTerms(entry1.value.value, entry2.value.value)
                            }
                        }
            value1 is CVTerm.BoxOf && value2 is CVTerm.BoxOf ->
                !unifyTerms(value1.content.value, value2.content.value) &&
                        !unifyTerms(value1.tag.value, value2.tag.value)
            value1 is CVTerm.RefOf && value2 is CVTerm.RefOf -> !unifyTerms(value1.element.value, value2.element.value)
            value1 is CVTerm.Refl && value2 is CVTerm.Refl -> true
            value1 is CVTerm.FunOf && value2 is CVTerm.FunOf ->
                value1.parameters.size == value2.parameters.size && run {
                    !value1.parameters.forEachM { parameter ->
                        modify { bind(lazyOf(CVTerm.Var(parameter.text, size))) }
                    }
                    !unifyTerms(!evalTerm(value1.body), !evalTerm(value2.body))
                }
            value1 is CVTerm.Apply && value2 is CVTerm.Apply ->
                !unifyTerms(value1.function, value2.function) &&
                        !(value1.arguments zip value2.arguments).allM { unifyTerms(it.first.value, it.second.value) }
            value1 is CVTerm.CodeOf && value2 is CVTerm.CodeOf -> !unifyTerms(value1.element.value, value2.element.value)
            value1 is CVTerm.Splice && value2 is CVTerm.Splice -> !unifyTerms(value1.element.value, value2.element.value)
            value1 is CVTerm.Or && value1.variants.isEmpty() && value2 is CVTerm.Or && value2.variants.isEmpty() -> true
            value1 is CVTerm.And && value1.variants.isEmpty() && value2 is CVTerm.And && value2.variants.isEmpty() -> true
            value1 is CVTerm.Unit && value2 is CVTerm.Unit -> true
            value1 is CVTerm.Bool && value2 is CVTerm.Bool -> true
            value1 is CVTerm.Byte && value2 is CVTerm.Byte -> true
            value1 is CVTerm.Short && value2 is CVTerm.Short -> true
            value1 is CVTerm.Int && value2 is CVTerm.Int -> true
            value1 is CVTerm.Long && value2 is CVTerm.Long -> true
            value1 is CVTerm.Float && value2 is CVTerm.Float -> true
            value1 is CVTerm.Double && value2 is CVTerm.Double -> true
            value1 is CVTerm.String && value2 is CVTerm.String -> true
            value1 is CVTerm.ByteArray && value2 is CVTerm.ByteArray -> true
            value1 is CVTerm.IntArray && value2 is CVTerm.IntArray -> true
            value1 is CVTerm.LongArray && value2 is CVTerm.LongArray -> true
            value1 is CVTerm.List && value2 is CVTerm.List ->
                !unifyTerms(value1.element.value, value2.element.value) &&
                        !unifyTerms(value1.size.value, value2.size.value)
            value1 is CVTerm.Compound && value2 is CVTerm.Compound ->
                value1.elements.size == value2.elements.size &&
                        !(value1.elements.entries zip value2.elements.entries).allM { (entry1, entry2) ->
                            {
                                !modify { bind(lazyOf(CVTerm.Var(entry1.key.text, size))) }
                                entry1.key.text == entry2.key.text &&
                                        entry1.value.relevant == entry2.value.relevant &&
                                        !unifyTerms(!evalTerm(entry1.value.type), !evalTerm(entry2.value.type))
                            }
                        }
            value1 is CVTerm.Box && value2 is CVTerm.Box -> !unifyTerms(value1.content.value, value2.content.value)
            value1 is CVTerm.Ref && value2 is CVTerm.Ref -> !unifyTerms(value1.element.value, value2.element.value)
            value1 is CVTerm.Eq && value2 is CVTerm.Eq ->
                !unifyTerms(value1.left.value, value2.left.value) &&
                        !unifyTerms(value1.right.value, value2.right.value)
            value1 is CVTerm.Fun && value2 is CVTerm.Fun ->
                value1.parameters.size == value2.parameters.size &&
                        value1.effects == value2.effects &&
                        !(value1.parameters zip value2.parameters).allM { (parameter1, parameter2) ->
                            {
                                !modify { bind(lazyOf(CVTerm.Var("", size))) }
                                !(unifyTerms(!evalTerm(parameter2.type), !evalTerm(parameter1.type)))
                            }
                        } &&
                        !unifyTerms(!evalTerm(value1.resultant), !evalTerm(value2.resultant))
            value1 is CVTerm.Code && value2 is CVTerm.Code -> !unifyTerms(value1.element.value, value2.element.value)
            value1 is CVTerm.Type && value2 is CVTerm.Type -> true
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
            term1 is CVTerm.Box && term2 is CVTerm.Box -> !subtypeTerms(term1.content.value, term2.content.value)
            term1 is CVTerm.Ref && term2 is CVTerm.Ref -> !subtypeTerms(term1.element.value, term2.element.value)
            term1 is CVTerm.Fun && term2 is CVTerm.Fun ->
                term1.parameters.size == term2.parameters.size &&
                        term2.effects.containsAll(term1.effects) &&
                        !restore {
                            !(term1.parameters zip term2.parameters).allM { (parameter1, parameter2) ->
                                {
                                    !modify { bindUnchecked(Entry(parameter1.termRelevant, "", null, null /* TODO */, parameter1.typeRelevant, TYPE, stage)) }
                                    parameter1.termRelevant == parameter2.termRelevant &&
                                            parameter1.typeRelevant == parameter2.typeRelevant &&
                                            !subtypeTerms(!lift({ normalizer }, evalTerm(parameter2.type)), !lift({ normalizer }, evalTerm(parameter1.type)))
                                }
                            } &&
                                    !(subtypeTerms(!lift({ normalizer }, evalTerm(term1.resultant)), !lift({ normalizer }, evalTerm(term2.resultant))))
                        }
            term1 is CVTerm.Code && term2 is CVTerm.Code -> !subtypeTerms(term1.element.value, term2.element.value)
            else -> !lift({ normalizer }, unifyTerms(term1, term2))
        }
    }

    /**
     * Infers the type of the [pattern] under the context.
     */
    private fun inferPattern(pattern: SPattern): State<Context, Pair<CPattern, CVTerm>> = {
        when (pattern) {
            is SPattern.Var -> {
                val type = !lift({ normalizer }, fresh(pattern.id))
                !modify { bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, true, type, stage)) }
                CPattern.Var(pattern.name, pattern.id) to type
            }
            is SPattern.UnitOf -> CPattern.UnitOf(pattern.id) to UNIT
            is SPattern.BoolOf -> CPattern.BoolOf(pattern.value, pattern.id) to BOOL
            is SPattern.ByteOf -> CPattern.ByteOf(pattern.value, pattern.id) to BYTE
            is SPattern.ShortOf -> CPattern.ShortOf(pattern.value, pattern.id) to SHORT
            is SPattern.IntOf -> CPattern.IntOf(pattern.value, pattern.id) to INT
            is SPattern.LongOf -> CPattern.LongOf(pattern.value, pattern.id) to LONG
            is SPattern.FloatOf -> CPattern.FloatOf(pattern.value, pattern.id) to FLOAT
            is SPattern.DoubleOf -> CPattern.DoubleOf(pattern.value, pattern.id) to DOUBLE
            is SPattern.StringOf -> CPattern.StringOf(pattern.value, pattern.id) to STRING
            is SPattern.ByteArrayOf -> {
                val elements = !pattern.elements.mapM { element -> checkPattern(element, BYTE) }
                CPattern.ByteArrayOf(elements, pattern.id) to BYTE_ARRAY
            }
            is SPattern.IntArrayOf -> {
                val elements = !pattern.elements.mapM { element -> checkPattern(element, INT) }
                CPattern.IntArrayOf(elements, pattern.id) to INT_ARRAY
            }
            is SPattern.LongArrayOf -> {
                val elements = !pattern.elements.mapM { element -> checkPattern(element, LONG) }
                CPattern.LongArrayOf(elements, pattern.id) to LONG_ARRAY
            }
            is SPattern.ListOf -> {
                if (pattern.elements.isEmpty()) {
                    CPattern.ListOf(emptyList(), pattern.id) to END
                } else { // TODO: use union of element types
                    val (head, headType) = !inferPattern(pattern.elements.first())
                    val elements = !pattern.elements.drop(1).mapM { element -> checkPattern(element, headType) }
                    val size = CVTerm.IntOf(elements.size)
                    CPattern.ListOf(listOf(head) + elements, pattern.id) to CVTerm.List(lazyOf(headType), lazyOf(size))
                }
            }
            is SPattern.CompoundOf -> {
                val elements = !pattern.elements.mapM { (name, element) ->
                    {
                        val (element, elementType) = !inferPattern(element)
                        Triple(name, element, elementType)
                    }
                }
                val elementTerms = elements.map { (name, element, _) -> name to element }.toLinkedHashMap()
                val elementTypes = elements.map { (name, _, type) -> name to CEntry(true, !lift({ normalizer }, quoteTerm(type)), null) }.toLinkedHashMap()
                CPattern.CompoundOf(elementTerms, pattern.id) to CVTerm.Compound(elementTypes)
            }
            is SPattern.BoxOf -> {
                val tag = !checkPattern(pattern.tag, TYPE)
                val vTag = tag.toType() ?: !lift({ normalizer }, fresh(pattern.id))
                val content = !checkPattern(pattern.content, vTag)
                CPattern.BoxOf(content, tag, pattern.id) to CVTerm.Box(lazyOf(vTag))
            }
            is SPattern.RefOf -> {
                val (element, elementType) = !inferPattern(pattern.element)
                CPattern.RefOf(element, pattern.id) to CVTerm.Ref(lazyOf(elementType))
            }
            is SPattern.Refl -> {
                val left = lazy { !lift({ normalizer }, fresh(pattern.id)) }
                CPattern.Refl(pattern.id) to CVTerm.Eq(left, left)
            }
            is SPattern.Unit -> CPattern.Unit(pattern.id) to UNIT
            is SPattern.Bool -> CPattern.Bool(pattern.id) to TYPE
            is SPattern.Byte -> CPattern.Byte(pattern.id) to TYPE
            is SPattern.Short -> CPattern.Short(pattern.id) to TYPE
            is SPattern.Int -> CPattern.Int(pattern.id) to TYPE
            is SPattern.Long -> CPattern.Long(pattern.id) to TYPE
            is SPattern.Float -> CPattern.Float(pattern.id) to TYPE
            is SPattern.Double -> CPattern.Double(pattern.id) to TYPE
            is SPattern.String -> CPattern.String(pattern.id) to TYPE
            is SPattern.ByteArray -> CPattern.ByteArray(pattern.id) to TYPE
            is SPattern.IntArray -> CPattern.IntArray(pattern.id) to TYPE
            is SPattern.LongArray -> CPattern.LongArray(pattern.id) to TYPE
        }.also { (_, type) ->
            types[pattern.id] = type
        }
    }

    /**
     * Checks the [pattern] against the [type] under the context.
     */
    private fun checkPattern(pattern: SPattern, type: CVTerm): State<Context, CPattern> = {
        val type = !gets {
            normalizer.force(type).also {
                types[pattern.id] = it
            }
        }
        when {
            pattern is SPattern.Var -> {
                !modify { bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, true, type, stage)) }
                CPattern.Var(pattern.name, pattern.id)
            }
            pattern is SPattern.ListOf && type is CVTerm.List -> {
                val elements = !pattern.elements.mapM { element ->
                    checkPattern(element, type.element.value)
                }
                when (val size = !gets { normalizer.force(type.size.value) }) {
                    is CVTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, pattern.id))
                    else -> {}
                }
                CPattern.ListOf(elements, pattern.id)
            }
            pattern is SPattern.CompoundOf && type is CVTerm.Compound -> {
                val elements = !(pattern.elements zip type.elements.entries).mapM { (element, entry) ->
                    {
                        val type = !lift({ normalizer }, evalTerm(entry.value.type))
                        val pattern = !checkPattern(element.second, type)
                        element.first to pattern
                    }
                }
                CPattern.CompoundOf(elements.toLinkedHashMap(), pattern.id)
            }
            pattern is SPattern.BoxOf && type is CVTerm.Box -> {
                val tag = !checkPattern(pattern.tag, TYPE)
                val vTag = tag.toType() ?: type.content.value
                val content = !checkPattern(pattern.content, vTag)
                CPattern.BoxOf(content, tag, pattern.id)
            }
            pattern is SPattern.RefOf && type is CVTerm.Ref -> {
                val element = !checkPattern(pattern.element, type.element.value)
                CPattern.RefOf(element, pattern.id)
            }
            pattern is SPattern.Refl && type is CVTerm.Eq -> {
                val normalizer = !lift({ normalizer }, {
                    !match(type.left.value, type.right.value)
                    !get()
                })
                !modify { copy(normalizer = normalizer) }
                CPattern.Refl(pattern.id)
            }
            else -> {
                val (inferred, inferredType) = !inferPattern(pattern)
                val isSubtype = !subtypeTerms(inferredType, type)
                if (!isSubtype) {
                    types[pattern.id] = END
                    val expected = printTerm(!lift({ normalizer }, quoteTerm(type)))
                    val actual = printTerm(!lift({ normalizer }, quoteTerm(inferredType)))
                    diagnose(Diagnostic.TermMismatch(expected, actual, pattern.id))
                }
                inferred
            }
        }
    }

    /**
     * Converts this pattern to a semantic term.
     */
    private fun CPattern.toType(): CVTerm? = when (this) {
        is CPattern.Unit -> UNIT
        is CPattern.Bool -> BOOL
        is CPattern.Byte -> BYTE
        is CPattern.Short -> SHORT
        is CPattern.Int -> INT
        is CPattern.Long -> LONG
        is CPattern.Float -> FLOAT
        is CPattern.Double -> DOUBLE
        is CPattern.String -> STRING
        is CPattern.ByteArray -> BYTE_ARRAY
        is CPattern.IntArray -> INT_ARRAY
        is CPattern.LongArray -> LONG_ARRAY
        else -> null
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
            term1 is CVTerm.BoxOf && term2 is CVTerm.BoxOf -> {
                !match(term1.content.value, term2.content.value)
                !match(term1.tag.value, term2.tag.value)
            }
            term1 is CVTerm.RefOf && term2 is CVTerm.RefOf -> !match(term1.element.value, term2.element.value)
            else -> Unit // TODO
        }
    }

    /**
     * Elaborates the [effect].
     */
    private fun elaborateEffect(effect: SEffect): CEffect = when (effect) {
        is SEffect.Name -> CEffect.Name(effect.name)
    }

    /**
     * Ensures that the representation of the [type] is monomorphic under this normalizer.
     */
    private fun Normalizer.checkRepresentation(id: Id, type: CVTerm) {
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
                is CVTerm.Box -> term2 is CVTerm.Box
                is CVTerm.Ref -> term2 is CVTerm.Ref
                is CVTerm.Eq -> term2 is CVTerm.Eq
                is CVTerm.Fun -> term2 is CVTerm.Fun
                is CVTerm.Type -> term2 is CVTerm.Type
                else -> false
            }
        }

        when (type) {
            is CVTerm.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            is CVTerm.Or -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolymorphicRepresentation(id))
                }
            }
            is CVTerm.And -> if (type.variants.isEmpty()) {
                diagnose(Diagnostic.PolymorphicRepresentation(id))
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
        val effects: Set<CEffect> = emptySet(),
    )

    private data class Effecting(
        val term: CTerm,
        val effects: Set<CEffect>,
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

    companion object {
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

        operator fun invoke(item: SItem, items: Map<String, CItem>): Result = Elaborate(items).run {
            val normalizer = Normalizer(persistentListOf(), items, solutions)
            val (item, _) = inferItem(item).run(Context(persistentListOf(), normalizer, false, 0, true, true))
            Result(item, types, normalizer, diagnostics, completions)
        }
    }
}
