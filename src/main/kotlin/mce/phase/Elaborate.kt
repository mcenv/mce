package mce.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.Diagnostic
import mce.Diagnostic.Companion.serializeEffect
import mce.Diagnostic.Companion.serializeTerm
import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C
import mce.graph.Surface as S

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
                val (context2, parameters) = context1.irrelevant().bindParameters(item.parameters)
                val resultant = context2.normalizer.eval(context2.checkTerm(item.resultant, TYPE))
                val effects = item.effects.map { elaborateEffect(it) }.toSet()
                val body = if (item.modifiers.contains(S.Modifier.BUILTIN)) C.Term.Hole(item.body.id) else run {
                    val body = context2.relevant().checkTerm(item.body, resultant)
                    if (parameters.isEmpty()) body else C.Term.FunOf(parameters.map { it.name }, body, freshId())
                }
                context2.checkPhase(item.body.id, resultant)
                C.Item.Def(item.imports, item.exports, modifiers, item.name, parameters, resultant, effects, body) to C.VSignature.Def(item.name, parameters, resultant, null)
            }
            is S.Item.Mod -> {
                val type = context.checkModule(item.type, C.VModule.Type(null))
                val vType: C.VModule = TODO()
                val body = context.checkModule(item.body, vType)
                C.Item.Mod(item.imports, item.exports, modifiers, item.name, vType, body) to C.VSignature.Mod(item.name, vType, null)
            }
        }
    }

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun checkItem(item: S.Item, signature: C.VSignature): C.Item {
        val (inferred, inferredSignature) = inferItem(item)
        if (!unifySignatures(inferredSignature, signature)) {
            diagnose(Diagnostic.SignatureMismatch(TODO()))
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
     * Binds the [parameters] to this context.
     */
    private fun Context.bindParameters(parameters: List<S.Parameter>): Pair<Context, List<C.Parameter>> = parameters.foldMap(this) { context, parameter ->
        val tType = context.checkTerm(parameter.type, TYPE)
        val vType = context.normalizer.eval(tType)
        val tLower = parameter.lower?.let { context.checkTerm(it, vType) }
        val vLower = tLower?.let { context.normalizer.eval(it) }
        val tUpper = parameter.upper?.let { context.checkTerm(it, vType) }
        val vUpper = tUpper?.let { context.normalizer.eval(it) }
        context.bindUnchecked(Entry(parameter.relevant, parameter.name, vLower, vUpper, vType, stage)) to C.Parameter(parameter.relevant, parameter.name, tLower, tUpper, tType)
    }

    /**
     * Infers the type of the [module] under this context.
     */
    private fun Context.inferModule(module: S.Module): Pair<C.Module, C.VModule> = when (module) {
        is S.Module.Var -> when (val item = items[module.name]) {
            is C.Item.Mod -> C.Module.Var(module.name, module.id) to item.type
            else -> {
                diagnose(Diagnostic.ModNotFound(module.name, module.id))
                C.Module.Var(module.name, module.id) to TODO()
            }
        }
        is S.Module.Str -> {
            val (items, signatures) = module.items.map { inferItem(it) }.unzip()
            C.Module.Str(items, module.id) to C.VModule.Sig(signatures, null)
        }
        is S.Module.Sig -> {
            val signatures = module.signatures.map { elaborateSignature(it) }
            C.Module.Sig(signatures, module.id) to C.VModule.Type(null)
        }
        is S.Module.Type -> C.Module.Type(module.id) to C.VModule.Type(null)
    }

    /**
     * Checks the [module] against the [type] under this context.
     */
    private fun Context.checkModule(module: S.Module, type: C.VModule): C.Module = when {
        module is S.Module.Str && type is C.VModule.Sig -> {
            val items = (module.items zip type.signatures).map { (item, signature) -> checkItem(item, signature) }
            C.Module.Str(items, module.id)
        }
        else -> {
            val (inferred, inferredType) = inferModule(module)
            if (!unifyModules(inferredType, type)) {
                diagnose(Diagnostic.ModuleMismatch(module.id))
            }
            inferred
        }
    }

    /**
     * Checks if the [module1] and the [module2] can be unified.
     */
    private fun unifyModules(module1: C.VModule, module2: C.VModule): Boolean = when (module1) {
        is C.VModule.Var -> module2 is C.VModule.Var && module1.name == module2.name
        is C.VModule.Sig -> module2 is C.VModule.Sig && (module1.signatures zip module2.signatures).all { (signature1, signature2) -> unifySignatures(signature1, signature2) }
        is C.VModule.Str -> false // TODO
        is C.VModule.Type -> module2 is C.VModule.Type
    }

    /**
     * Checks if the [signature1] and the [signature2] can be unified.
     */
    private fun unifySignatures(signature1: C.VSignature, signature2: C.VSignature): Boolean = when (signature1) {
        is C.VSignature.Def -> signature2 is C.VSignature.Def && false // TODO
        is C.VSignature.Mod -> signature2 is C.VSignature.Mod && signature1.name == signature2.name && unifyModules(signature1.type, signature2.type)
    }

    /**
     * Elaborates the [signature] under this context.
     */
    private fun Context.elaborateSignature(signature: S.Signature): C.Signature = when (signature) {
        is S.Signature.Def -> {
            val (context, parameters) = irrelevant().bindParameters(signature.parameters)
            val resultant = context.checkTerm(signature.resultant, TYPE)
            C.Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is S.Signature.Mod -> {
            val type = checkModule(signature.type, C.VModule.Type(null))
            C.Signature.Mod(signature.name, type, signature.id)
        }
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun Context.inferTerm(term: S.Term): Typing = when (term) {
        is S.Term.Hole -> {
            completions[term.id] = entries.map { it.name to it.type }
            Typing(C.Term.Hole(term.id), diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quote(END)), term.id)))
        }
        is S.Term.Meta -> {
            val type = normalizer.fresh(term.id)
            Typing(checkTerm(term, type), type)
        }
        is S.Term.Anno -> {
            val type = normalizer.eval(irrelevant().checkTerm(term.type, TYPE))
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
            val elements = term.elements.map { "" to inferTerm(it) }
            Typing(C.Term.CompoundOf(elements.map { it.second.term }, term.id), C.VTerm.Compound(elements.map { it.first to normalizer.quote(it.second.type) }))
        }
        is S.Term.BoxOf -> {
            val tTag = checkTerm(term.tag, TYPE)
            val vTag = normalizer.eval(tTag)
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
                // TODO: unify flex elim
                else -> diagnose(Diagnostic.CodeExpected(term.id))
            }
            Typing(C.Term.Splice(element.term, term.id), type)
        }
        is S.Term.Union -> {
            val variants = term.variants.map { checkTerm(it, TYPE) }
            Typing(C.Term.Union(variants, term.id), TYPE)
        }
        is S.Term.Intersection -> {
            val variants = term.variants.map { checkTerm(it, TYPE) }
            Typing(C.Term.Intersection(variants, term.id), TYPE)
        }
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
                context.bind(term.id, Entry(true /* TODO */, name, END, ANY, context.normalizer.eval(element), stage)) to (name to element)
            }
            Typing(C.Term.Compound(elements, term.id), TYPE)
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
                    val tArgument = checkTerm(argument, context.normalizer.eval(parameter.type))
                    val vArgument = context.normalizer.eval(tArgument)
                    val lower = parameter.lower?.let { context.normalizer.eval(it) }?.also { lower ->
                        if (!context.subtypeTerms(lower, vArgument)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quote(vArgument)), serializeTerm(context.normalizer.quote(lower)), argument.id))
                    }
                    val upper = parameter.upper?.let { context.normalizer.eval(it) }?.also { upper ->
                        if (!context.subtypeTerms(vArgument, upper)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quote(upper)), serializeTerm(context.normalizer.quote(vArgument)), argument.id))
                    }
                    val type = context.normalizer.eval(parameter.type)
                    context.bindUnchecked(Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument) to tArgument
                }
                item.parameters.fold(this) { context, parameter ->
                    val lower = parameter.lower?.let { context.normalizer.eval(it) }
                    val upper = parameter.upper?.let { context.normalizer.eval(it) }
                    val type = context.normalizer.eval(parameter.type)
                    context.bindUnchecked(Entry(parameter.relevant, parameter.name, lower, upper, type, stage))
                }
                Typing(C.Term.Def(computation.name, arguments, computation.id), item.resultant, item.effects)
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
            val body = (computation.parameters zip types).fold(empty()) { context, (parameter, type) -> context.bind(computation.id, Entry(true /* TODO */, parameter, END, ANY, type, stage)) }.inferComputation(computation.body)
            val parameters = (computation.parameters zip types).map { C.Parameter(true /* TODO */, it.first, normalizer.quote(END), normalizer.quote(ANY), normalizer.quote(it.second)) }
            Typing(C.Term.FunOf(computation.parameters, body.term, computation.id), C.VTerm.Fun(parameters, normalizer.quote(body.type), body.effects))
        }
        is S.Term.Apply -> {
            val function = inferComputation(computation.function)
            when (val type = normalizer.force(function.type)) {
                is C.VTerm.Fun -> {
                    if (type.parameters.size != computation.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.arguments.size, computation.id))
                    }
                    val (context, arguments) = (computation.arguments zip type.parameters).foldMap(this) { context, (argument, parameter) ->
                        val tArgument = checkTerm(argument, context.normalizer.eval(parameter.type))
                        val vArgument = context.normalizer.eval(tArgument)
                        val lower = parameter.lower?.let { context.normalizer.eval(it) }?.also { lower ->
                            if (!context.subtypeTerms(lower, vArgument)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quote(vArgument)), serializeTerm(context.normalizer.quote(lower)), argument.id))
                        }
                        val upper = parameter.upper?.let { context.normalizer.eval(it) }?.also { upper ->
                            if (!context.subtypeTerms(vArgument, upper)) diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quote(upper)), serializeTerm(context.normalizer.quote(vArgument)), argument.id))
                        }
                        val type = context.normalizer.eval(parameter.type)
                        context.bindUnchecked(Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument) to tArgument
                    }
                    val resultant = context.normalizer.eval(type.resultant)
                    Typing(C.Term.Apply(function.term, arguments, computation.id), resultant, type.effects)
                }
                // TODO: unify flex elim
                else -> Typing(C.Term.Apply(function.term, computation.arguments.map { checkTerm(it, ANY) }, computation.id), diagnose(Diagnostic.FunctionExpected(computation.function.id)), function.effects)
            }
        }
        is S.Term.Fun -> {
            val (context, parameters) = bindParameters(computation.parameters)
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
                diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quote(type)), term.id))
                C.Term.Hole(term.id)
            }
            term is S.Term.Meta -> normalizer.quote(normalizer.fresh(term.id))
            term is S.Term.ListOf && type is C.VTerm.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value) }
                when (val size = normalizer.force(type.size.value)) {
                    is C.VTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                    else -> {}
                }
                C.Term.ListOf(elements, term.id)
            }
            term is S.Term.CompoundOf && type is C.VTerm.Compound -> {
                val (_, elements) = (term.elements zip type.elements).foldMap(this) { context, (element, type) ->
                    val vType = context.normalizer.eval(type.second)
                    val element = context.checkTerm(element, vType)
                    val vElement = context.normalizer.eval(element)
                    context.bindUnchecked(Entry(true /* TODO */, type.first, END, ANY, vType, context.stage), vElement) to element
                }
                C.Term.CompoundOf(elements, term.id)
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
                    val lower = parameter.lower?.let { context.normalizer.eval(it) }
                    val upper = parameter.upper?.let { context.normalizer.eval(it) }
                    val type = context.normalizer.eval(parameter.type)
                    context.bindUnchecked(Entry(parameter.relevant, name, lower, upper, type, stage))
                }
                val resultant = context.checkComputation(computation.body, context.normalizer.eval(type.resultant), effects)
                Effecting(C.Term.FunOf(computation.parameters, resultant.term, computation.id), emptySet())
            }
            else -> {
                val id = computation.id
                val computation = inferComputation(computation)
                if (!subtypeTerms(computation.type, type)) {
                    types[id] = END
                    diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quote(type)), serializeTerm(normalizer.quote(computation.type)), id))
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
                    (value1.elements zip value2.elements).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.BoxOf && value2 is C.VTerm.BoxOf -> unifyTerms(value1.content.value, value2.content.value) && unifyTerms(value1.tag.value, value2.tag.value)
            value1 is C.VTerm.RefOf && value2 is C.VTerm.RefOf -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Refl && value2 is C.VTerm.Refl -> true
            value1 is C.VTerm.FunOf && value2 is C.VTerm.FunOf -> value1.parameters.size == value2.parameters.size &&
                    value1.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.VTerm.Var(parameter, normalizer.size))) }.run { unifyTerms(eval(value1.body), eval(value2.body)) }
            value1 is C.VTerm.Apply && value2 is C.VTerm.Apply -> unifyTerms(value1.function, value2.function) &&
                    (value1.arguments zip value2.arguments).all { unifyTerms(it.first.value, it.second.value) }
            value1 is C.VTerm.CodeOf && value2 is C.VTerm.CodeOf -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Splice && value2 is C.VTerm.Splice -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Union && value1.variants.isEmpty() && value2 is C.VTerm.Union && value2.variants.isEmpty() -> true
            value1 is C.VTerm.Intersection && value1.variants.isEmpty() && value2 is C.VTerm.Intersection && value2.variants.isEmpty() -> true
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
                    (value1.elements zip value2.elements).foldAll(this) { normalizer, (element1, element2) ->
                        normalizer.bind(lazyOf(C.VTerm.Var("", normalizer.size))) to normalizer.unifyTerms(normalizer.eval(element1.second), normalizer.eval(element2.second))
                    }.second
            value1 is C.VTerm.Box && value2 is C.VTerm.Box -> unifyTerms(value1.content.value, value2.content.value)
            value1 is C.VTerm.Ref && value2 is C.VTerm.Ref -> unifyTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Eq && value2 is C.VTerm.Eq -> unifyTerms(value1.left.value, value2.left.value) && unifyTerms(value1.right.value, value2.right.value)
            value1 is C.VTerm.Fun && value2 is C.VTerm.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (normalizer, success) = (value1.parameters zip value2.parameters).foldAll(this) { normalizer, (parameter1, parameter2) ->
                    normalizer.bind(lazyOf(C.VTerm.Var("", normalizer.size))) to normalizer.unifyTerms(normalizer.eval(parameter2.type), normalizer.eval(parameter1.type))
                }
                success && normalizer.unifyTerms(normalizer.eval(value1.resultant), normalizer.eval(value2.resultant))
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
            value1 is C.VTerm.Union -> value1.variants.all { subtypeTerms(it.value, value2) }
            value2 is C.VTerm.Union -> value2.variants.any { subtypeTerms(value1, it.value) }
            value2 is C.VTerm.Intersection -> value2.variants.all { subtypeTerms(value1, it.value) }
            value1 is C.VTerm.Intersection -> value1.variants.any { subtypeTerms(it.value, value2) }
            value1 is C.VTerm.List && value2 is C.VTerm.List -> subtypeTerms(value1.element.value, value2.element.value) && normalizer.unifyTerms(value1.size.value, value2.size.value)
            value1 is C.VTerm.Compound && value2 is C.VTerm.Compound -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).foldAll(this) { context, (elements1, elements2) ->
                        val upper1 = context.normalizer.eval(elements1.second)
                        context.bindUnchecked(Entry(true /* TODO */, "", null, upper1, TYPE, stage)) to context.subtypeTerms(upper1, context.normalizer.eval(elements2.second))
                    }.second
            value1 is C.VTerm.Box && value2 is C.VTerm.Box -> subtypeTerms(value1.content.value, value2.content.value)
            value1 is C.VTerm.Ref && value2 is C.VTerm.Ref -> subtypeTerms(value1.element.value, value2.element.value)
            value1 is C.VTerm.Fun && value2 is C.VTerm.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (context, success) = (value1.parameters zip value2.parameters).foldAll(this) { context, (parameter1, parameter2) ->
                    context.bindUnchecked(Entry(parameter1.relevant, "", null, null /* TODO */, TYPE, stage)) to (parameter1.relevant == parameter2.relevant && context.subtypeTerms(
                        context.normalizer.eval(parameter2.type),
                        context.normalizer.eval(parameter1.type)
                    ))
                }
                success && context.subtypeTerms(context.normalizer.eval(value1.resultant), context.normalizer.eval(value2.resultant))
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
            val (context, elements) = pattern.elements.foldMap(this) { context, element ->
                val (context, element, elementType) = context.inferPattern(element)
                context to (element to elementType)
            }
            Triple(context, C.Pattern.CompoundOf(elements.map { it.first }, pattern.id), C.VTerm.Compound(elements.map { "" to context.normalizer.quote(it.second) }))
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
                val (context, elements) = (pattern.elements zip type.elements).foldMap(this) { context, element -> context.checkPattern(element.first, context.normalizer.eval(element.second.second)) }
                context to C.Pattern.CompoundOf(elements, pattern.id)
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
                val normalizer = normalizer.match(type.left.value, type.right.value)
                withNormalizer(normalizer) to C.Pattern.Refl(pattern.id)
            }
            else -> {
                val (context, inferred, inferredType) = inferPattern(pattern)
                if (!context.subtypeTerms(inferredType, type)) {
                    types[pattern.id] = END
                    diagnose(Diagnostic.TermMismatch(serializeTerm(context.normalizer.quote(type)), serializeTerm(context.normalizer.quote(inferredType)), pattern.id))
                }
                context to inferred
            }
        }
    }

    /**
     * Converts this pattern to a semantic term.
     */
    private fun C.Pattern.toType(): C.VTerm? = when (this) {
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
    private fun Normalizer.match(term1: C.VTerm, term2: C.VTerm): Normalizer {
        val value1 = force(term1)
        val value2 = force(term2)
        return when {
            value2 is C.VTerm.Var -> subst(value2.level, lazyOf(value1))
            value1 is C.VTerm.Var -> subst(value1.level, lazyOf(value2))
            value1 is C.VTerm.ByteArrayOf && value2 is C.VTerm.ByteArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.VTerm.IntArrayOf && value2 is C.VTerm.IntArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.VTerm.LongArrayOf && value2 is C.VTerm.LongArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.VTerm.ListOf && value2 is C.VTerm.ListOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.VTerm.CompoundOf && value2 is C.VTerm.CompoundOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.VTerm.BoxOf && value2 is C.VTerm.BoxOf -> match(value1.content.value, value2.content.value).match(value1.tag.value, value2.tag.value)
            value1 is C.VTerm.RefOf && value2 is C.VTerm.RefOf -> match(value1.element.value, value2.element.value)
            else -> this // TODO
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
        fun join(left: C.VTerm, right: C.VTerm): Boolean = when (val left = force(left)) {
            is C.VTerm.Union -> left.variants.all { join(it.value, right) }
            is C.VTerm.Intersection -> left.variants.any { join(it.value, right) }
            is C.VTerm.Bool, is C.VTerm.Byte, is C.VTerm.Eq, is C.VTerm.Fun, is C.VTerm.Type -> when (force(right)) {
                is C.VTerm.Bool, is C.VTerm.Byte, is C.VTerm.Eq, is C.VTerm.Fun, is C.VTerm.Type -> true
                else -> false
            }
            is C.VTerm.Short -> right is C.VTerm.Short
            is C.VTerm.Int, is C.VTerm.Ref -> when (force(right)) {
                is C.VTerm.Int, is C.VTerm.Ref -> true
                else -> false
            }
            is C.VTerm.Long -> right is C.VTerm.Long
            is C.VTerm.Float -> right is C.VTerm.Float
            is C.VTerm.Double -> right is C.VTerm.Double
            is C.VTerm.String -> right is C.VTerm.String
            is C.VTerm.ByteArray -> right is C.VTerm.ByteArray
            is C.VTerm.IntArray -> right is C.VTerm.IntArray
            is C.VTerm.LongArray -> right is C.VTerm.LongArray
            is C.VTerm.List -> right is C.VTerm.List
            is C.VTerm.Compound, is C.VTerm.Box -> when (force(right)) {
                is C.VTerm.Compound, is C.VTerm.Box -> true
                else -> false
            }
            else -> false
        }

        when (type) {
            is C.VTerm.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            is C.VTerm.Union -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolymorphicRepresentation(id))
                }
            }
            is C.VTerm.Intersection -> if (type.variants.isEmpty()) {
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

        fun bind(id: Id, entry: Entry, term: C.VTerm? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(term ?: C.VTerm.Var(entry.name, size))), meta, stage, relevant).also { checkPhase(id, entry.type) }

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
        private val END = C.VTerm.Union(emptyList())
        private val ANY = C.VTerm.Intersection(emptyList())
        private val TYPE = C.VTerm.Type()

        operator fun invoke(item: S.Item, items: Map<String, C.Item>): Result = Elaborate(items).run {
            val (item, _) = inferItem(item)
            Result(item, types, Normalizer(persistentListOf(), items, solutions), diagnostics, completions)
        }
    }
}
