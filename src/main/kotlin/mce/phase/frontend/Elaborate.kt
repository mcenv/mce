package mce.phase.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.ast.Id
import mce.ast.freshId
import mce.phase.Normalizer
import mce.phase.frontend.Diagnostic.Companion.serializeEffect
import mce.phase.frontend.Diagnostic.Companion.serializeTerm
import mce.util.*
import mce.ast.Core as C
import mce.ast.Surface as S

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
    private val items: Map<String, C.Item>,
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val completions: MutableMap<Id, List<Pair<String, C.VTerm>>> = mutableMapOf()
    private val types: MutableMap<Id, C.VTerm> = mutableMapOf()
    private val solutions: MutableList<C.VTerm?> = mutableListOf()

    /**
     * Infers the signature of the [item].
     */
    private fun inferItem(item: S.Item): Pair<C.Item, C.VSignature> {
        val context = Context(persistentListOf(), Normalizer(persistentListOf(), items, solutions), false, 0, true)
        val modifiers = item.modifiers.mapTo(mutableSetOf()) { elaborateModifier(it) }
        return when (item) {
            is S.Item.Def -> {
                val context1 = context.meta(item.modifiers.contains(S.Modifier.META))
                val (context2, parameters) = bindParameters(item.parameters) with context1.irrelevant()
                val resultant = context2.checkTerm(item.resultant, TYPE)
                val vResultant = context2.normalizer.evalTerm(resultant)
                val effects = item.effects.map { elaborateEffect(it) }.toSet()
                val body = if (item.modifiers.contains(S.Modifier.BUILTIN)) C.Term.Hole(item.body.id) else context2.relevant().checkTerm(item.body, vResultant)
                context2.checkPhase(item.body.id, vResultant)
                C.Item.Def(item.imports, item.exports, modifiers, item.name, parameters, resultant, effects, body, item.id) to C.VSignature.Def(item.name, parameters, resultant, null)
            }
            is S.Item.Mod -> {
                val (context, type) = checkModule(item.type, C.VModule.Type(null)) with context
                val vType = context.normalizer.evalModule(type)
                val (_, body) = checkModule(item.body, vType) with context
                C.Item.Mod(item.imports, item.exports, modifiers, item.name, vType, body, item.id) to C.VSignature.Mod(item.name, vType, null)
            }
            is S.Item.Test -> {
                val body = context.checkTerm(item.body, BOOL)
                C.Item.Test(item.imports, item.exports, modifiers, item.name, body, item.id) to C.VSignature.Test(item.name, null)
            }
        }
    }

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun checkItem(item: S.Item, signature: C.VSignature): C.Item {
        val (inferred, inferredSignature) = inferItem(item)
        if (!Normalizer(persistentListOf(), items, solutions).unifySignatures(inferredSignature, signature)) {
            diagnose(Diagnostic.SignatureMismatch(item.id)) // TODO: serialize signatures
        }
        return inferred
    }

    /**
     * Elaborates the [modifier].
     */
    private fun elaborateModifier(modifier: S.Modifier): C.Modifier = when (modifier) {
        S.Modifier.BUILTIN -> C.Modifier.BUILTIN
        S.Modifier.META -> C.Modifier.META
    }

    /**
     * Binds the [parameters] to the context.
     */
    private fun bindParameters(parameters: List<S.Parameter>): State<Context, List<C.Parameter>> =
        parameters.map { parameter ->
            State<Context, C.Parameter> {
                val type = checkTerm(parameter.type, TYPE)
                val vType = normalizer.evalTerm(type)
                val lower = parameter.lower?.let { checkTerm(it, vType) }
                val vLower = lower?.let { normalizer.evalTerm(it) }
                val upper = parameter.upper?.let { checkTerm(it, vType) }
                val vUpper = upper?.let { normalizer.evalTerm(it) }
                bind(parameter.id, Entry(parameter.relevant, parameter.name, vLower, vUpper, vType, stage)) to C.Parameter(parameter.relevant, parameter.name, lower, upper, type, parameter.id)
            }
        }.fold()

    /**
     * Infers the type of the [module] under the context.
     */
    private fun inferModule(module: S.Module): State<Context, Pair<C.Module, C.VModule>> = when (module) {
        is S.Module.Var -> when (val item = items[module.name]) {
            is C.Item.Mod -> pure(C.Module.Var(module.name, module.id) to item.type)
            else -> {
                diagnose(Diagnostic.ModNotFound(module.name, module.id))
                pure(C.Module.Var(module.name, module.id) to TODO())
            }
        }
        is S.Module.Str -> {
            val (items, signatures) = module.items.map { inferItem(it) }.unzip()
            pure(C.Module.Str(items, module.id) to C.VModule.Sig(signatures, null))
        }
        is S.Module.Sig -> module.signatures.map { elaborateSignature(it) }.fold() / { signatures ->
            C.Module.Sig(signatures, module.id) to C.VModule.Type(null)
        }
        is S.Module.Type -> pure(C.Module.Type(module.id) to C.VModule.Type(null))
    }

    /**
     * Checks the [module] against the [type] under the context.
     */
    private fun checkModule(module: S.Module, type: C.VModule): State<Context, C.Module> = when {
        module is S.Module.Str && type is C.VModule.Sig -> {
            val items = (module.items zip type.signatures).map { (item, signature) -> checkItem(item, signature) }
            pure(C.Module.Str(items, module.id))
        }
        else -> inferModule(module) % { (inferred, inferredType) ->
            gets {
                if (!normalizer.unifyModules(inferredType, type)) {
                    diagnose(Diagnostic.ModuleMismatch(module.id))
                }
                inferred
            }
        }
    }

    /**
     * Checks if the [module1] and the [module2] can be unified under this normalizer.
     */
    private fun Normalizer.unifyModules(module1: C.VModule, module2: C.VModule): Boolean = when (module1) {
        is C.VModule.Var -> module2 is C.VModule.Var && module1.name == module2.name
        is C.VModule.Sig -> module2 is C.VModule.Sig && (module1.signatures zip module2.signatures).all { (signature1, signature2) -> unifySignatures(signature1, signature2) }
        is C.VModule.Str -> false // TODO
        is C.VModule.Type -> module2 is C.VModule.Type
    }

    /**
     * Checks if the [signature1] and the [signature2] can be unified under this normalizer.
     */
    private fun Normalizer.unifySignatures(signature1: C.VSignature, signature2: C.VSignature): Boolean = when (signature1) {
        is C.VSignature.Def -> signature2 is C.VSignature.Def && signature1.name == signature2.name && signature1.parameters.size == signature2.parameters.size && run {
            val (normalizer, success) = (signature1.parameters zip signature2.parameters).foldAll(this) { normalizer, (parameter1, parameter2) ->
                normalizer.bind(lazyOf(C.VTerm.Var("", normalizer.size))) to normalizer.unifyTerms(normalizer.evalTerm(parameter2.type), normalizer.evalTerm(parameter1.type))
            }
            success && normalizer.unifyTerms(normalizer.evalTerm(signature1.resultant), normalizer.evalTerm(signature2.resultant))
        }
        is C.VSignature.Mod -> signature2 is C.VSignature.Mod && signature1.name == signature2.name && unifyModules(signature1.type, signature2.type)
        is C.VSignature.Test -> signature2 is C.VSignature.Test && signature1.name == signature2.name
    }

    /**
     * Elaborates the [signature] under the context.
     */
    private fun elaborateSignature(signature: S.Signature): State<Context, C.Signature> = when (signature) {
        is S.Signature.Def -> modify<Context> { irrelevant() } % {
            bindParameters(signature.parameters) % { parameters ->
                gets {
                    val resultant = checkTerm(signature.resultant, TYPE)
                    C.Signature.Def(signature.name, parameters, resultant, signature.id)
                }
            }
        }
        is S.Signature.Mod -> checkModule(signature.type, C.VModule.Type(null)) / { type ->
            C.Signature.Mod(signature.name, type, signature.id)
        }
        is S.Signature.Test -> pure(C.Signature.Test(signature.name, signature.id))
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun Context.inferTerm(term: S.Term): Typing = when (term) {
        is S.Term.Hole -> {
            completions[term.id] = entries.map { it.name to it.type }
            Typing(C.Term.Hole(term.id), diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quoteTerm(END)), term.id)))
        }
        is S.Term.Meta -> {
            val type = normalizer.fresh(term.id)
            Typing(checkTerm(term, type), type)
        }
        is S.Term.Anno -> {
            val type = normalizer.evalTerm(irrelevant().checkTerm(term.type, TYPE))
            Typing(checkTerm(term.element, type), type)
        }
        is S.Term.Var -> {
            val level = lookup(term.name)
            val type = when (level) {
                -1 -> diagnose(Diagnostic.VarNotFound(term.name, term.id))
                else -> {
                    val entry = entries[level]
                    var type = entry.type
                    if (stage != entry.stage) type = diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id))
                    if (relevant && !entry.relevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                    normalizer.checkRepresentation(term.id, type)
                    type
                }
            }
            Typing(C.Term.Var(term.name, level, term.id), type)
        }
        is S.Term.UnitOf -> Typing(C.Term.UnitOf(term.id), UNIT)
        is S.Term.BoolOf -> Typing(C.Term.BoolOf(term.value, term.id), BOOL)
        is S.Term.ByteOf -> Typing(C.Term.ByteOf(term.value, term.id), BYTE)
        is S.Term.ShortOf -> Typing(C.Term.ShortOf(term.value, term.id), SHORT)
        is S.Term.IntOf -> Typing(C.Term.IntOf(term.value, term.id), INT)
        is S.Term.LongOf -> Typing(C.Term.LongOf(term.value, term.id), LONG)
        is S.Term.FloatOf -> Typing(C.Term.FloatOf(term.value, term.id), FLOAT)
        is S.Term.DoubleOf -> Typing(C.Term.DoubleOf(term.value, term.id), DOUBLE)
        is S.Term.StringOf -> Typing(C.Term.StringOf(term.value, term.id), STRING)
        is S.Term.ByteArrayOf -> {
            val elements = term.elements.map { checkTerm(it, BYTE) }
            Typing(C.Term.ByteArrayOf(elements, term.id), BYTE_ARRAY)
        }
        is S.Term.IntArrayOf -> {
            val elements = term.elements.map { checkTerm(it, INT) }
            Typing(C.Term.IntArrayOf(elements, term.id), INT_ARRAY)
        }
        is S.Term.LongArrayOf -> {
            val elements = term.elements.map { checkTerm(it, LONG) }
            Typing(C.Term.LongArrayOf(elements, term.id), LONG_ARRAY)
        }
        is S.Term.ListOf -> if (term.elements.isEmpty()) {
            Typing(C.Term.ListOf(emptyList(), term.id), C.VTerm.List(lazyOf(END), lazyOf(C.VTerm.IntOf(0))))
        } else { // TODO: use union of element types
            val first = inferTerm(term.elements.first())
            val elements = listOf(first.term) + term.elements.drop(1).map { checkTerm(it, first.type) }
            val size = C.VTerm.IntOf(elements.size)
            Typing(C.Term.ListOf(elements, term.id), C.VTerm.List(lazyOf(first.type), lazyOf(size)))
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { (name, element) -> name to inferTerm(element) }
            Typing(C.Term.CompoundOf(elements.map { (name, element) -> name to element.term }.toLinkedHashMap(), term.id), C.VTerm.Compound(elements.map { (name, element) -> name to normalizer.quoteTerm(element.type) }.toLinkedHashMap()))
        }
        is S.Term.BoxOf -> {
            val tTag = checkTerm(term.tag, TYPE)
            val vTag = normalizer.evalTerm(tTag)
            val content = checkTerm(term.content, vTag)
            Typing(C.Term.BoxOf(content, tTag, term.id), C.VTerm.Box(lazyOf(vTag)))
        }
        is S.Term.RefOf -> {
            val element = inferTerm(term.element)
            Typing(C.Term.RefOf(element.term, term.id), C.VTerm.Ref(lazyOf(element.type)))
        }
        is S.Term.Refl -> {
            val left = lazy { normalizer.fresh(term.id) }
            Typing(C.Term.Refl(term.id), C.VTerm.Eq(left, left))
        }
        is S.Term.CodeOf -> {
            val element = up().inferTerm(term.element)
            Typing(C.Term.CodeOf(element.term, term.id), C.VTerm.Code(lazyOf(element.type)))
        }
        is S.Term.Splice -> {
            val element = down().inferTerm(term.element)
            val type = when (val type = normalizer.force(element.type)) {
                is C.VTerm.Code -> type.element.value
                else -> type.also {
                    normalizer.unifyTerms(it, C.VTerm.Code(lazyOf(normalizer.fresh(freshId()))))
                }
            }
            Typing(C.Term.Splice(element.term, term.id), type)
        }
        is S.Term.Or -> {
            val variants = term.variants.map { checkTerm(it, TYPE) }
            Typing(C.Term.Or(variants, term.id), TYPE)
        }
        is S.Term.And -> {
            val variants = term.variants.map { checkTerm(it, TYPE) }
            Typing(C.Term.And(variants, term.id), TYPE)
        }
        is S.Term.Unit -> Typing(C.Term.Unit(term.id), TYPE)
        is S.Term.Bool -> Typing(C.Term.Bool(term.id), TYPE)
        is S.Term.Byte -> Typing(C.Term.Byte(term.id), TYPE)
        is S.Term.Short -> Typing(C.Term.Short(term.id), TYPE)
        is S.Term.Int -> Typing(C.Term.Int(term.id), TYPE)
        is S.Term.Long -> Typing(C.Term.Long(term.id), TYPE)
        is S.Term.Float -> Typing(C.Term.Float(term.id), TYPE)
        is S.Term.Double -> Typing(C.Term.Double(term.id), TYPE)
        is S.Term.String -> Typing(C.Term.String(term.id), TYPE)
        is S.Term.ByteArray -> Typing(C.Term.ByteArray(term.id), TYPE)
        is S.Term.IntArray -> Typing(C.Term.IntArray(term.id), TYPE)
        is S.Term.LongArray -> Typing(C.Term.LongArray(term.id), TYPE)
        is S.Term.List -> {
            val element = checkTerm(term.element, TYPE)
            val size = irrelevant().checkTerm(term.size, INT)
            Typing(C.Term.List(element, size, term.id), TYPE)
        }
        is S.Term.Compound -> {
            val (_, elements) = term.elements.foldMap(this) { context, (name, element) ->
                val element = context.checkTerm(element, TYPE)
                context.bind(name.id, Entry(false, name.text, END, ANY, context.normalizer.evalTerm(element), stage)) to (name to element)
            }
            Typing(C.Term.Compound(elements.toLinkedHashMap(), term.id), TYPE)
        }
        is S.Term.Box -> {
            val content = checkTerm(term.content, TYPE)
            Typing(C.Term.Box(content, term.id), TYPE)
        }
        is S.Term.Ref -> {
            val element = checkTerm(term.element, TYPE)
            Typing(C.Term.Ref(element, term.id), TYPE)
        }
        is S.Term.Eq -> {
            val left = inferTerm(term.left)
            val right = checkTerm(term.right, left.type)
            Typing(C.Term.Eq(left.term, right, term.id), TYPE)
        }
        is S.Term.Code -> Typing(C.Term.Code(checkTerm(term.element, TYPE), term.id), TYPE)
        is S.Term.Type -> Typing(C.Term.Type(term.id), TYPE)
        else -> inferComputation(term).also { computation ->
            if (computation.effects.isNotEmpty()) {
                diagnose(Diagnostic.EffectMismatch(emptyList(), computation.effects.map { serializeEffect(it) }, term.id))
            }
        }
    }.also {
        types[term.id] = it.type
    }

    /**
     * Infers the type of the [computation] under this context.
     */
    private fun Context.inferComputation(computation: S.Term): Typing = when (computation) {
        is S.Term.Def -> when (val item = items[computation.name]) {
            is C.Item.Def -> {
                if (item.parameters.size != computation.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(item.parameters.size, computation.arguments.size, computation.id))
                }
                val (_, arguments) = (computation.arguments zip item.parameters).foldMap(this) { context, (argument, parameter) ->
                    val tArgument = checkTerm(argument, context.normalizer.evalTerm(parameter.type))
                    val vArgument = context.normalizer.evalTerm(tArgument)
                    val lower = parameter.lower?.let { context.normalizer.evalTerm(it) }?.also { lower ->
                        if (!context.subtypeTerms(lower, vArgument)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quoteTerm(vArgument)), serializeTerm(context.normalizer.quoteTerm(lower)), argument.id))
                    }
                    val upper = parameter.upper?.let { context.normalizer.evalTerm(it) }?.also { upper ->
                        if (!context.subtypeTerms(vArgument, upper)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quoteTerm(upper)), serializeTerm(context.normalizer.quoteTerm(vArgument)), argument.id))
                    }
                    val type = context.normalizer.evalTerm(parameter.type)
                    context.bind(argument.id, Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument) to tArgument
                }
                val resultant = item.parameters.fold(this) { context, parameter ->
                    val lower = parameter.lower?.let { context.normalizer.evalTerm(it) }
                    val upper = parameter.upper?.let { context.normalizer.evalTerm(it) }
                    val type = context.normalizer.evalTerm(parameter.type)
                    context.bind(parameter.id, Entry(parameter.relevant, parameter.name, lower, upper, type, stage))
                }.normalizer.evalTerm(item.resultant)
                Typing(C.Term.Def(computation.name, arguments, computation.id), resultant, item.effects)
            }
            else -> Typing(C.Term.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id)))
        }
        is S.Term.Let -> {
            val init = inferComputation(computation.init)
            val body = bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, init.type, stage)).inferComputation(computation.body)
            Typing(C.Term.Let(computation.name, init.term, body.term, computation.id), body.type, init.effects + body.effects)
        }
        is S.Term.Match -> {
            val type = normalizer.fresh(computation.id) // TODO: use union of element types
            val scrutinee = inferTerm(computation.scrutinee)
            val clauses = computation.clauses.map { (pattern, body) ->
                val (context, pattern) = checkPattern(pattern, scrutinee.type)
                val body = context.checkTerm(body, type) // TODO
                (pattern to body)
            }
            Typing(C.Term.Match(scrutinee.term, clauses, computation.id), type)
        }
        is S.Term.FunOf -> {
            val types = computation.parameters.map { normalizer.fresh(computation.id) }
            val body = (computation.parameters zip types).fold(empty()) { context, (parameter, type) -> context.bind(parameter.id, Entry(true /* TODO */, parameter.text, END, ANY, type, stage)) }.inferComputation(computation.body)
            val parameters = (computation.parameters zip types).map { (parameter, type) -> C.Parameter(true /* TODO */, parameter.text, normalizer.quoteTerm(END), normalizer.quoteTerm(ANY), normalizer.quoteTerm(type), parameter.id) }
            Typing(C.Term.FunOf(computation.parameters, body.term, computation.id), C.VTerm.Fun(parameters, normalizer.quoteTerm(body.type), body.effects))
        }
        is S.Term.Apply -> {
            val function = inferComputation(computation.function)
            val type = when (val type = normalizer.force(function.type)) {
                is C.VTerm.Fun -> type
                else -> {
                    val parameters = computation.arguments.map {
                        C.Parameter(true, "", null, null, normalizer.quoteTerm(normalizer.fresh(freshId())), freshId())
                    }
                    val vResultant = normalizer.fresh(freshId())
                    val resultant = normalizer.quoteTerm(vResultant)
                    val effects = emptySet<C.Effect>() // TODO
                    normalizer.unifyTerms(type, C.VTerm.Fun(parameters, resultant, effects, null))
                    C.VTerm.Fun(parameters, resultant, effects, type.id)
                }
            }
            if (type.parameters.size != computation.arguments.size) {
                diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.arguments.size, computation.id))
            }
            val (context, arguments) = (computation.arguments zip type.parameters).foldMap(this) { context, (argument, parameter) ->
                val tArgument = checkTerm(argument, context.normalizer.evalTerm(parameter.type))
                val vArgument = context.normalizer.evalTerm(tArgument)
                val lower = parameter.lower?.let { context.normalizer.evalTerm(it) }?.also { lower ->
                    if (!context.subtypeTerms(lower, vArgument)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quoteTerm(vArgument)), serializeTerm(context.normalizer.quoteTerm(lower)), argument.id))
                }
                val upper = parameter.upper?.let { context.normalizer.evalTerm(it) }?.also { upper ->
                    if (!context.subtypeTerms(vArgument, upper)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quoteTerm(upper)), serializeTerm(context.normalizer.quoteTerm(vArgument)), argument.id))
                }
                val type = context.normalizer.evalTerm(parameter.type)
                context.bind(argument.id, Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument) to tArgument
            }
            val resultant = context.normalizer.evalTerm(type.resultant)
            Typing(C.Term.Apply(function.term, arguments, computation.id), resultant, type.effects)
        }
        is S.Term.Fun -> {
            val (context, parameters) = bindParameters(computation.parameters) with this
            val resultant = context.checkTerm(computation.resultant, TYPE) /* TODO: check effects */
            val effects = computation.effects.map { elaborateEffect(it) }.toSet()
            Typing(C.Term.Fun(parameters, resultant, effects, computation.id), TYPE)
        }
        else -> inferTerm(computation)
    }

    /**
     * Checks the [term] against the [type] under this context.
     */
    private fun Context.checkTerm(term: S.Term, type: C.VTerm): C.Term {
        val type = normalizer.force(type)
        types[term.id] = type
        return when {
            term is S.Term.Hole -> {
                completions[term.id] = entries.map { it.name to it.type }
                diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quoteTerm(type)), term.id))
                C.Term.Hole(term.id)
            }
            term is S.Term.Meta -> normalizer.quoteTerm(normalizer.fresh(term.id))
            term is S.Term.ListOf && type is C.VTerm.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value) }
                when (val size = normalizer.force(type.size.value)) {
                    is C.VTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                    else -> {}
                }
                C.Term.ListOf(elements, term.id)
            }
            term is S.Term.CompoundOf && type is C.VTerm.Compound -> {
                val (_, elements) = (term.elements zip type.elements.values).foldMap(this) { context, (element, type) ->
                    val vType = context.normalizer.evalTerm(type)
                    val tElement = context.checkTerm(element.second, vType)
                    val vElement = context.normalizer.evalTerm(tElement)
                    context.bind(element.first.id, Entry(true /* TODO */, element.first.text, END, ANY, vType, context.stage), vElement) to (element.first to tElement)
                }
                C.Term.CompoundOf(elements.toLinkedHashMap(), term.id)
            }
            term is S.Term.RefOf && type is C.VTerm.Ref -> {
                val element = checkTerm(term.element, type.element.value)
                C.Term.RefOf(element, term.id)
            }
            term is S.Term.CodeOf && type is C.VTerm.Code -> {
                val element = up().checkTerm(term.element, type.element.value)
                C.Term.CodeOf(element, term.id)
            }
            else -> checkComputation(term, type, emptySet()).term
        }
    }

    /**
     * Checks the [computation] against the [type] and the [effects] under this context.
     */
    private fun Context.checkComputation(computation: S.Term, type: C.VTerm, effects: Set<C.Effect>): Effecting {
        val type = normalizer.force(type)
        types[computation.id] = type
        return when {
            computation is S.Term.Let -> {
                val init = inferComputation(computation.init)
                val body = bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, init.type, stage)).checkComputation(computation.body, type, effects)
                val effects = init.effects + body.effects
                Effecting(C.Term.Let(computation.name, init.term, body.term, computation.id), effects)
            }
            computation is S.Term.Match -> {
                val scrutinee = inferTerm(computation.scrutinee)
                val clauses = computation.clauses.map { (pattern, body) ->
                    val (context, pattern) = checkPattern(pattern, scrutinee.type)
                    val body = context.checkComputation(body, type, effects)
                    pattern to body
                }
                val effects = clauses.flatMap { (_, body) -> body.effects }.toSet()
                Effecting(C.Term.Match(scrutinee.term, clauses.map { (pattern, body) -> pattern to body.term }, computation.id), effects)
            }
            computation is S.Term.FunOf && type is C.VTerm.Fun -> {
                if (type.parameters.size != computation.parameters.size) {
                    diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.parameters.size, computation.id))
                }
                val context = (computation.parameters zip type.parameters).fold(empty()) { context, (name, parameter) ->
                    val lower = parameter.lower?.let { context.normalizer.evalTerm(it) }
                    val upper = parameter.upper?.let { context.normalizer.evalTerm(it) }
                    val type = context.normalizer.evalTerm(parameter.type)
                    context.bind(name.id, Entry(parameter.relevant, name.text, lower, upper, type, stage))
                }
                val resultant = context.checkComputation(computation.body, context.normalizer.evalTerm(type.resultant), effects)
                Effecting(C.Term.FunOf(computation.parameters, resultant.term, computation.id), emptySet())
            }
            else -> {
                val id = computation.id
                val computation = inferComputation(computation)
                if (!subtypeTerms(computation.type, type)) {
                    types[id] = END
                    diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(type)), serializeTerm(normalizer.quoteTerm(computation.type)), id))
                }
                if (!(effects.containsAll(computation.effects))) {
                    diagnose(Diagnostic.EffectMismatch(effects.map { serializeEffect(it) }, computation.effects.map { serializeEffect(it) }, id))
                }
                Effecting(computation.term, computation.effects)
            }
        }
    }

    /**
     * Checks if the [term1] and the [term2] can be unified under [this] normalizer.
     */
    private fun Normalizer.unifyTerms(term1: C.VTerm, term2: C.VTerm): Boolean {
        val value1 = force(term1)
        val value2 = force(term2)
        return when {
            value1.id != null && value2.id != null && value1.id == value2.id -> true
            value1 is C.VTerm.Meta && value2 is C.VTerm.Meta && value1.index == value2.index -> true
            value1 is C.VTerm.Meta -> when (val solved1 = getSolution(value1.index)) {
                null -> {
                    solve(value1.index, value2)
                    true
                }
                else -> unifyTerms(solved1, value2)
            }
            value2 is C.VTerm.Meta -> unifyTerms(value2, value1)
            value1 is C.VTerm.Var && value2 is C.VTerm.Var -> value1.level == value2.level
            value1 is C.VTerm.Def && value2 is C.VTerm.Def && value1.name == value2.name -> true
            value1 is C.VTerm.Match && value2 is C.VTerm.Match -> false // TODO
            value1 is C.VTerm.UnitOf && value2 is C.VTerm.UnitOf -> true
            value1 is C.VTerm.BoolOf && value2 is C.VTerm.BoolOf -> value1.value == value2.value
            value1 is C.VTerm.ByteOf && value2 is C.VTerm.ByteOf -> value1.value == value2.value
            value1 is C.VTerm.ShortOf && value2 is C.VTerm.ShortOf -> value1.value == value2.value
            value1 is C.VTerm.IntOf && value2 is C.VTerm.IntOf -> value1.value == value2.value
            value1 is C.VTerm.LongOf && value2 is C.VTerm.LongOf -> value1.value == value2.value
            value1 is C.VTerm.FloatOf && value2 is C.VTerm.FloatOf -> value1.value == value2.value
            value1 is C.VTerm.DoubleOf && value2 is C.VTerm.DoubleOf -> value1.value == value2.value
            value1 is C.VTerm.StringOf && value2 is C.VTerm.StringOf -> value1.value == value2.value
            value1 is C.VTerm.ByteArrayOf && value2 is C.VTerm.ByteArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.IntArrayOf && value2 is C.VTerm.IntArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.LongArrayOf && value2 is C.VTerm.LongArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.ListOf && value2 is C.VTerm.ListOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.CompoundOf && value2 is C.VTerm.CompoundOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements.entries zip value2.elements.entries).all { (entry1, entry2) -> entry1.key.text == entry2.key.text && unifyTerms(entry1.value.value, entry2.value.value) }
            value1 is C.VTerm.BoxOf && value2 is C.VTerm.BoxOf -> unifyTerms(value1.content.value, value2.content.value) && unifyTerms(value1.tag.value, value2.tag.value)
            value1 is C.VTerm.RefOf && value2 is C.VTerm.RefOf -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Refl && value2 is C.VTerm.Refl -> true
            value1 is C.VTerm.FunOf && value2 is C.VTerm.FunOf -> value1.parameters.size == value2.parameters.size &&
                    value1.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter.text, normalizer.size))) }.run { unifyTerms(evalTerm(value1.body), evalTerm(value2.body)) }
            value1 is C.VTerm.Apply && value2 is C.VTerm.Apply -> unifyTerms(value1.function, value2.function) &&
                    (value1.arguments zip value2.arguments).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.CodeOf && value2 is C.VTerm.CodeOf -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Splice && value2 is C.VTerm.Splice -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Or && value1.variants.isEmpty() && value2 is C.VTerm.Or && value2.variants.isEmpty() -> true
            value1 is C.VTerm.And && value1.variants.isEmpty() && value2 is C.VTerm.And && value2.variants.isEmpty() -> true
            value1 is C.VTerm.Unit && value2 is C.VTerm.Unit -> true
            value1 is C.VTerm.Bool && value2 is C.VTerm.Bool -> true
            value1 is C.VTerm.Byte && value2 is C.VTerm.Byte -> true
            value1 is C.VTerm.Short && value2 is C.VTerm.Short -> true
            value1 is C.VTerm.Int && value2 is C.VTerm.Int -> true
            value1 is C.VTerm.Long && value2 is C.VTerm.Long -> true
            value1 is C.VTerm.Float && value2 is C.VTerm.Float -> true
            value1 is C.VTerm.Double && value2 is C.VTerm.Double -> true
            value1 is C.VTerm.String && value2 is C.VTerm.String -> true
            value1 is C.VTerm.ByteArray && value2 is C.VTerm.ByteArray -> true
            value1 is C.VTerm.IntArray && value2 is C.VTerm.IntArray -> true
            value1 is C.VTerm.LongArray && value2 is C.VTerm.LongArray -> true
            value1 is C.VTerm.List && value2 is C.VTerm.List -> unifyTerms(value1.element.value, value2.element.value) && unifyTerms(value1.size.value, value2.size.value)
            value1 is C.VTerm.Compound && value2 is C.VTerm.Compound -> value1.elements.size == value2.elements.size &&
                    (value1.elements.entries zip value2.elements.entries).foldAll(this) { normalizer, (entry1, entry2) ->
                        (normalizer.bind(lazyOf(C.VTerm.Var(entry1.key.text, normalizer.size))) to normalizer.unifyTerms(normalizer.evalTerm(entry1.value), normalizer.evalTerm(entry2.value))).mapSecond {
                            it && entry1.key.text == entry2.key.text
                        }
                    }.second
            value1 is C.VTerm.Box && value2 is C.VTerm.Box -> unifyTerms(value1.content.value, value2.content.value)
            value1 is C.VTerm.Ref && value2 is C.VTerm.Ref -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Eq && value2 is C.VTerm.Eq -> unifyTerms(value1.left.value, value2.left.value) && unifyTerms(value1.right.value, value2.right.value)
            value1 is C.VTerm.Fun && value2 is C.VTerm.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (normalizer, success) = (value1.parameters zip value2.parameters).foldAll(this) { normalizer, (parameter1, parameter2) ->
                    normalizer.bind(lazyOf(C.VTerm.Var("", normalizer.size))) to normalizer.unifyTerms(normalizer.evalTerm(parameter2.type), normalizer.evalTerm(parameter1.type))
                }
                success && normalizer.unifyTerms(normalizer.evalTerm(value1.resultant), normalizer.evalTerm(value2.resultant))
            } && value1.effects == value2.effects
            value1 is C.VTerm.Code && value2 is C.VTerm.Code -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Type && value2 is C.VTerm.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [term1] is a subtype of the [term2] under [this] context.
     */
    private fun Context.subtypeTerms(term1: C.VTerm, term2: C.VTerm): Boolean {
        val value1 = normalizer.force(term1)
        val value2 = normalizer.force(term2)
        return when {
            value1 is C.VTerm.Var && value2 is C.VTerm.Var && value1.level == value2.level -> true
            value1 is C.VTerm.Var && entries[value1.level].upper != null -> subtypeTerms(entries[value1.level].upper!!, value2)
            value2 is C.VTerm.Var && entries[value2.level].lower != null -> subtypeTerms(value1, entries[value2.level].lower!!)
            value1 is C.VTerm.Apply && value2 is C.VTerm.Apply -> subtypeTerms(value1.function, value2.function) && (value1.arguments zip value2.arguments).all {
                normalizer.unifyTerms(
                    it.first.value,
                    it.second.value
                ) /* pointwise subtyping */
            }
            value1 is C.VTerm.Or -> value1.variants.all { subtypeTerms(it.value, value2) }
            value2 is C.VTerm.Or -> value2.variants.any { subtypeTerms(value1, it.value) }
            value2 is C.VTerm.And -> value2.variants.all { subtypeTerms(value1, it.value) }
            value1 is C.VTerm.And -> value1.variants.any { subtypeTerms(it.value, value2) }
            value1 is C.VTerm.List && value2 is C.VTerm.List -> subtypeTerms(value1.element.value, value2.element.value) && normalizer.unifyTerms(value1.size.value, value2.size.value)
            value1 is C.VTerm.Compound && value2 is C.VTerm.Compound -> value2.elements.entries.foldAll(this) { context, entry2 ->
                val element1 = value1.elements[entry2.key]
                if (element1 != null) {
                    val upper1 = context.normalizer.evalTerm(element1)
                    context.bindUnchecked(Entry(false, entry2.key.text, null, upper1, TYPE, stage)) to context.subtypeTerms(upper1, context.normalizer.evalTerm(entry2.value))
                } else context to false
            }.second
            value1 is C.VTerm.Box && value2 is C.VTerm.Box -> subtypeTerms(value1.content.value, value2.content.value)
            value1 is C.VTerm.Ref && value2 is C.VTerm.Ref -> subtypeTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Fun && value2 is C.VTerm.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (context, success) = (value1.parameters zip value2.parameters).foldAll(this) { context, (parameter1, parameter2) ->
                    context.bindUnchecked(Entry(parameter1.relevant, "", null, null /* TODO */, TYPE, stage)) to (parameter1.relevant == parameter2.relevant && context.subtypeTerms(
                        context.normalizer.evalTerm(parameter2.type),
                        context.normalizer.evalTerm(parameter1.type)
                    ))
                }
                success && context.subtypeTerms(context.normalizer.evalTerm(value1.resultant), context.normalizer.evalTerm(value2.resultant))
            } && value2.effects.containsAll(value1.effects)
            value1 is C.VTerm.Code && value2 is C.VTerm.Code -> subtypeTerms(value1.element.value, value2.element.value)
            else -> normalizer.unifyTerms(value1, value2)
        }
    }

    /**
     * Infers the type of the [pattern] under this context.
     */
    private fun Context.inferPattern(pattern: S.Pattern): Triple<Context, C.Pattern, C.VTerm> = when (pattern) {
        is S.Pattern.Var -> {
            val type = normalizer.fresh(pattern.id)
            val context = bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, type, stage))
            Triple(context, C.Pattern.Var(pattern.name, pattern.id), type)
        }
        is S.Pattern.UnitOf -> Triple(this, C.Pattern.UnitOf(pattern.id), UNIT)
        is S.Pattern.BoolOf -> Triple(this, C.Pattern.BoolOf(pattern.value, pattern.id), BOOL)
        is S.Pattern.ByteOf -> Triple(this, C.Pattern.ByteOf(pattern.value, pattern.id), BYTE)
        is S.Pattern.ShortOf -> Triple(this, C.Pattern.ShortOf(pattern.value, pattern.id), SHORT)
        is S.Pattern.IntOf -> Triple(this, C.Pattern.IntOf(pattern.value, pattern.id), INT)
        is S.Pattern.LongOf -> Triple(this, C.Pattern.LongOf(pattern.value, pattern.id), LONG)
        is S.Pattern.FloatOf -> Triple(this, C.Pattern.FloatOf(pattern.value, pattern.id), FLOAT)
        is S.Pattern.DoubleOf -> Triple(this, C.Pattern.DoubleOf(pattern.value, pattern.id), DOUBLE)
        is S.Pattern.StringOf -> Triple(this, C.Pattern.StringOf(pattern.value, pattern.id), STRING)
        is S.Pattern.ByteArrayOf -> {
            val (context, elements) = pattern.elements.foldMap(this) { context, element -> context.checkPattern(element, BYTE) }
            Triple(context, C.Pattern.ByteArrayOf(elements, pattern.id), BYTE_ARRAY)
        }
        is S.Pattern.IntArrayOf -> {
            val (context, elements) = pattern.elements.foldMap(this) { context, element -> context.checkPattern(element, INT) }
            Triple(context, C.Pattern.IntArrayOf(elements, pattern.id), INT_ARRAY)
        }
        is S.Pattern.LongArrayOf -> {
            val (context, elements) = pattern.elements.foldMap(this) { context, element -> context.checkPattern(element, LONG) }
            Triple(context, C.Pattern.LongArrayOf(elements, pattern.id), LONG_ARRAY)
        }
        is S.Pattern.ListOf -> if (pattern.elements.isEmpty()) {
            Triple(this, C.Pattern.ListOf(emptyList(), pattern.id), END)
        } else { // TODO: use union of element types
            val (context1, head, headType) = inferPattern(pattern.elements.first())
            val (context2, elements) = pattern.elements.drop(1).foldMap(context1) { context, element -> context.checkPattern(element, headType) }
            val size = C.VTerm.IntOf(elements.size)
            Triple(context2, C.Pattern.ListOf(listOf(head) + elements, pattern.id), C.VTerm.List(lazyOf(headType), lazyOf(size)))
        }
        is S.Pattern.CompoundOf -> {
            val (context, elements) = pattern.elements.foldMap(this) { context, (name, element) ->
                val (context, element, elementType) = context.inferPattern(element)
                context to Triple(name, element, elementType)
            }
            Triple(
                context,
                C.Pattern.CompoundOf(elements.map { (name, element, _) -> name to element }.toLinkedHashMap(), pattern.id),
                C.VTerm.Compound(elements.map { (name, _, type) -> name to context.normalizer.quoteTerm(type) }.toLinkedHashMap())
            )
        }
        is S.Pattern.BoxOf -> {
            val (context1, tag) = checkPattern(pattern.tag, TYPE)
            val vTag = tag.toType() ?: context1.normalizer.fresh(pattern.id)
            val (context2, content) = context1.checkPattern(pattern.content, vTag)
            Triple(context2, C.Pattern.BoxOf(content, tag, pattern.id), C.VTerm.Box(lazyOf(vTag)))
        }
        is S.Pattern.RefOf -> {
            val (context, element, elementType) = inferPattern(pattern.element)
            Triple(context, C.Pattern.RefOf(element, pattern.id), C.VTerm.Ref(lazyOf(elementType)))
        }
        is S.Pattern.Refl -> {
            val left = lazy { normalizer.fresh(pattern.id) }
            Triple(this, C.Pattern.Refl(pattern.id), C.VTerm.Eq(left, left))
        }
        is S.Pattern.Unit -> Triple(this, C.Pattern.Unit(pattern.id), UNIT)
        is S.Pattern.Bool -> Triple(this, C.Pattern.Bool(pattern.id), TYPE)
        is S.Pattern.Byte -> Triple(this, C.Pattern.Byte(pattern.id), TYPE)
        is S.Pattern.Short -> Triple(this, C.Pattern.Short(pattern.id), TYPE)
        is S.Pattern.Int -> Triple(this, C.Pattern.Int(pattern.id), TYPE)
        is S.Pattern.Long -> Triple(this, C.Pattern.Long(pattern.id), TYPE)
        is S.Pattern.Float -> Triple(this, C.Pattern.Float(pattern.id), TYPE)
        is S.Pattern.Double -> Triple(this, C.Pattern.Double(pattern.id), TYPE)
        is S.Pattern.String -> Triple(this, C.Pattern.String(pattern.id), TYPE)
        is S.Pattern.ByteArray -> Triple(this, C.Pattern.ByteArray(pattern.id), TYPE)
        is S.Pattern.IntArray -> Triple(this, C.Pattern.IntArray(pattern.id), TYPE)
        is S.Pattern.LongArray -> Triple(this, C.Pattern.LongArray(pattern.id), TYPE)
    }.also { (_, _, type) ->
        types[pattern.id] = type
    }

    /**
     * Checks the [pattern] against the [type] under this context.
     */
    private fun Context.checkPattern(pattern: S.Pattern, type: C.VTerm): Pair<Context, C.Pattern> {
        val type = normalizer.force(type)
        types[pattern.id] = type
        return when {
            pattern is S.Pattern.Var -> {
                val context = bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, type, stage))
                context to C.Pattern.Var(pattern.name, pattern.id)
            }
            pattern is S.Pattern.ListOf && type is C.VTerm.List -> {
                val (context, elements) = pattern.elements.foldMap(this) { context, element -> context.checkPattern(element, type.element.value) }
                when (val size = context.normalizer.force(type.size.value)) {
                    is C.VTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, pattern.id))
                    else -> {}
                }
                context to C.Pattern.ListOf(elements, pattern.id)
            }
            pattern is S.Pattern.CompoundOf && type is C.VTerm.Compound -> {
                val (context, elements) = (pattern.elements zip type.elements.entries).foldMap(this) { context, (element, type) ->
                    context.checkPattern(element.second, context.normalizer.evalTerm(type.value)).mapSecond { element.first to it }
                }
                context to C.Pattern.CompoundOf(elements.toLinkedHashMap(), pattern.id)
            }
            pattern is S.Pattern.BoxOf && type is C.VTerm.Box -> {
                val (context1, tag) = checkPattern(pattern.tag, TYPE)
                val vTag = tag.toType() ?: type.content.value
                val (context2, content) = context1.checkPattern(pattern.content, vTag)
                context2 to C.Pattern.BoxOf(content, tag, pattern.id)
            }
            pattern is S.Pattern.RefOf && type is C.VTerm.Ref -> {
                val (context, element) = checkPattern(pattern.element, type.element.value)
                context to C.Pattern.RefOf(element, pattern.id)
            }
            pattern is S.Pattern.Refl && type is C.VTerm.Eq -> {
                val (normalizer, _) = match(type.left.value, type.right.value) with normalizer
                withNormalizer(normalizer) to C.Pattern.Refl(pattern.id)
            }
            else -> {
                val (context, inferred, inferredType) = inferPattern(pattern)
                if (!context.subtypeTerms(inferredType, type)) {
                    types[pattern.id] = END
                    diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quoteTerm(type)), serializeTerm(context.normalizer.quoteTerm(inferredType)), pattern.id))
                }
                context to inferred
            }
        }
    }

    /**
     * Converts this pattern to a semantic term.
     */
    private fun C.Pattern.toType(): C.VTerm? = when (this) {
        is C.Pattern.Unit -> UNIT
        is C.Pattern.Bool -> BOOL
        is C.Pattern.Byte -> BYTE
        is C.Pattern.Short -> SHORT
        is C.Pattern.Int -> INT
        is C.Pattern.Long -> LONG
        is C.Pattern.Float -> FLOAT
        is C.Pattern.Double -> DOUBLE
        is C.Pattern.String -> STRING
        is C.Pattern.ByteArray -> BYTE_ARRAY
        is C.Pattern.IntArray -> INT_ARRAY
        is C.Pattern.LongArray -> LONG_ARRAY
        else -> null
    }

    /**
     * Matches the [term1] and the [term2].
     */
    private fun match(term1: C.VTerm, term2: C.VTerm): State<Normalizer, Unit> =
        gets<Normalizer, Pair<C.VTerm, C.VTerm>> { force(term1) to force(term2) } % { (term1, term2) ->
            when {
                term2 is C.VTerm.Var -> modify { subst(term2.level, lazyOf(term1)) }
                term1 is C.VTerm.Var -> modify { subst(term1.level, lazyOf(term2)) }
                term1 is C.VTerm.ByteArrayOf && term2 is C.VTerm.ByteArrayOf -> (term1.elements zip term2.elements).map { (element1, element2) -> match(element1.value, element2.value) }.forEach()
                term1 is C.VTerm.IntArrayOf && term2 is C.VTerm.IntArrayOf -> (term1.elements zip term2.elements).map { (element1, element2) -> match(element1.value, element2.value) }.forEach()
                term1 is C.VTerm.LongArrayOf && term2 is C.VTerm.LongArrayOf -> (term1.elements zip term2.elements).map { (element1, element2) -> match(element1.value, element2.value) }.forEach()
                term1 is C.VTerm.ListOf && term2 is C.VTerm.ListOf -> (term1.elements zip term2.elements).map { (element1, element2) -> match(element1.value, element2.value) }.forEach()
                term1 is C.VTerm.CompoundOf && term2 is C.VTerm.CompoundOf -> (term1.elements.entries zip term2.elements.entries).map { (entry1, entry2) ->
                    if (entry1.key == entry2.key) match(entry1.value.value, entry2.value.value) else pure(Unit)
                }.forEach()
                term1 is C.VTerm.BoxOf && term2 is C.VTerm.BoxOf -> match(term1.content.value, term2.content.value) % { match(term1.tag.value, term2.tag.value) }
                term1 is C.VTerm.RefOf && term2 is C.VTerm.RefOf -> match(term1.element.value, term2.element.value)
                else -> pure(Unit) // TODO
            }
        }

    /**
     * Elaborates the [effect].
     */
    private fun elaborateEffect(effect: S.Effect): C.Effect = when (effect) {
        is S.Effect.Name -> C.Effect.Name(effect.name)
    }

    /**
     * Ensures that the representation of the [type] is monomorphic under this normalizer.
     */
    private fun Normalizer.checkRepresentation(id: Id, type: C.VTerm) {
        fun join(term1: C.VTerm, term2: C.VTerm): Boolean {
            val term1 = force(term1)
            val term2 = force(term2)
            return when (term1) {
                is C.VTerm.Or -> term1.variants.all { join(it.value, term2) }
                is C.VTerm.And -> term1.variants.any { join(it.value, term2) }
                is C.VTerm.Unit -> term2 is C.VTerm.Unit
                is C.VTerm.Bool -> term2 is C.VTerm.Bool
                is C.VTerm.Byte -> term2 is C.VTerm.Byte
                is C.VTerm.Short -> term2 is C.VTerm.Short
                is C.VTerm.Int -> term2 is C.VTerm.Int
                is C.VTerm.Long -> term2 is C.VTerm.Long
                is C.VTerm.Float -> term2 is C.VTerm.Float
                is C.VTerm.Double -> term2 is C.VTerm.Double
                is C.VTerm.String -> term2 is C.VTerm.String
                is C.VTerm.ByteArray -> term2 is C.VTerm.ByteArray
                is C.VTerm.IntArray -> term2 is C.VTerm.IntArray
                is C.VTerm.LongArray -> term2 is C.VTerm.LongArray
                is C.VTerm.List -> term2 is C.VTerm.List
                is C.VTerm.Compound -> term2 is C.VTerm.Compound
                is C.VTerm.Box -> term2 is C.VTerm.Box
                is C.VTerm.Ref -> term2 is C.VTerm.Ref
                is C.VTerm.Eq -> term2 is C.VTerm.Eq
                is C.VTerm.Fun -> term2 is C.VTerm.Fun
                is C.VTerm.Type -> term2 is C.VTerm.Type
                else -> false
            }
        }

        when (type) {
            is C.VTerm.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            is C.VTerm.Or -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolymorphicRepresentation(id))
                }
            }
            is C.VTerm.And -> if (type.variants.isEmpty()) {
                diagnose(Diagnostic.PolymorphicRepresentation(id))
            }
            else -> {}
        }
    }

    private fun diagnose(diagnostic: Diagnostic): C.VTerm {
        diagnostics += diagnostic
        return END
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>,
        val completions: Map<Id, List<Pair<String, C.VTerm>>>,
    )

    private data class Typing(
        val term: C.Term,
        val type: C.VTerm,
        val effects: Set<C.Effect> = emptySet(),
    )

    private data class Effecting(
        val term: C.Term,
        val effects: Set<C.Effect>,
    )

    private data class Entry(
        val relevant: Boolean,
        val name: String,
        val lower: C.VTerm?,
        val upper: C.VTerm?,
        val type: C.VTerm,
        val stage: Int,
    )

    private inner class Context(
        val entries: PersistentList<Entry>,
        val normalizer: Normalizer,
        val meta: Boolean,
        val stage: Int,
        val relevant: Boolean,
    ) {
        val size: Int get() = entries.size

        fun lookup(name: String): Int = entries.indexOfLast { it.name == name }

        fun bind(id: Id, entry: Entry, term: C.VTerm? = null): Context = bindUnchecked(entry, term).also { checkPhase(id, entry.type) }

        fun bindUnchecked(entry: Entry, term: C.VTerm? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(term ?: C.VTerm.Var(entry.name, size))), meta, stage, relevant)

        fun empty(): Context = Context(persistentListOf(), normalizer.empty(), meta, stage, relevant)

        fun withNormalizer(normalizer: Normalizer): Context = Context(entries, normalizer, meta, stage, relevant)

        fun meta(meta: Boolean): Context = Context(entries, normalizer, meta, stage, relevant)

        fun up(): Context = Context(entries, normalizer, meta, stage + 1, relevant)

        fun down(): Context = Context(entries, normalizer, meta, stage - 1, relevant)

        fun relevant(): Context = if (relevant) this else Context(entries, normalizer, meta, stage, true)

        fun irrelevant(): Context = if (!relevant) this else Context(entries, normalizer, meta, stage, false)

        fun checkPhase(id: Id, type: C.VTerm) {
            if (!meta && type is C.VTerm.Code) diagnose(Diagnostic.PhaseMismatch(id))
        }
    }

    companion object {
        private val UNIT = C.VTerm.Unit()
        private val BOOL = C.VTerm.Bool()
        private val BYTE = C.VTerm.Byte()
        private val SHORT = C.VTerm.Short()
        private val INT = C.VTerm.Int()
        private val LONG = C.VTerm.Long()
        private val FLOAT = C.VTerm.Float()
        private val DOUBLE = C.VTerm.Double()
        private val STRING = C.VTerm.String()
        private val BYTE_ARRAY = C.VTerm.ByteArray()
        private val INT_ARRAY = C.VTerm.IntArray()
        private val LONG_ARRAY = C.VTerm.LongArray()
        private val END = C.VTerm.Or(emptyList())
        private val ANY = C.VTerm.And(emptyList())
        private val TYPE = C.VTerm.Type()

        operator fun invoke(item: S.Item, items: Map<String, C.Item>): Result = Elaborate(items).run {
            val (item, _) = inferItem(item)
            Result(item, types, Normalizer(persistentListOf(), items, solutions), diagnostics, completions)
        }
    }
}
