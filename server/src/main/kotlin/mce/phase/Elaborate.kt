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
    private val items: Map<String, C.Item>
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val types: MutableMap<Id, C.Value> = mutableMapOf()
    private val solutions: MutableList<C.Value?> = mutableListOf()

    private fun elaborateItem(item: S.Item): Pair<Normalizer, C.Item> = when (item) {
        is S.Item.Def -> {
            val context1 = run {
                val meta = item.modifiers.contains(S.Modifier.META)
                Context(persistentListOf(), Normalizer(persistentListOf(), items, solutions), meta, 0, false)
            }
            val (context2, parameters) = context1.elaborateParameters(item.parameters)
            val modifiers = item.modifiers.mapTo(mutableSetOf()) { elaborateModifier(it) }
            val resultant = context2.normalizer.eval(context2.checkTerm(item.resultant, TYPE))
            val effects = item.effects.map { elaborateEffect(it) }.toSet()
            val body = if (item.modifiers.contains(S.Modifier.BUILTIN)) C.Term.Hole(item.body.id) else run {
                val body = context2.relevant().checkTerm(item.body, resultant)
                if (parameters.isEmpty()) body else C.Term.FunOf(parameters.map { it.name }, body, freshId())
            }
            context2.checkPhase(item.body.id, resultant)
            context2.normalizer to C.Item.Def(item.imports, item.exports, modifiers, item.name, parameters, resultant, effects, body)
        }
    }

    private fun elaborateModifier(modifier: S.Modifier): C.Modifier = when (modifier) {
        S.Modifier.BUILTIN -> C.Modifier.BUILTIN
        S.Modifier.META -> C.Modifier.META
    }

    private fun Context.elaborateParameters(parameters: List<S.Parameter>): Pair<Context, List<C.Parameter>> = parameters.foldMap(this) { context, parameter ->
        val tType = context.checkTerm(parameter.type, TYPE)
        val vType = context.normalizer.eval(tType)
        val tLower = parameter.lower?.let { context.checkTerm(it, vType) }
        val vLower = tLower?.let { context.normalizer.eval(it) }
        val tUpper = parameter.upper?.let { context.checkTerm(it, vType) }
        val vUpper = tUpper?.let { context.normalizer.eval(it) }
        context.bindUnchecked(Entry(parameter.relevant, parameter.name, vLower, vUpper, vType, stage)) to C.Parameter(parameter.relevant, parameter.name, tLower, tUpper, tType)
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun Context.inferTerm(term: S.Term): Typing = when (term) {
        is S.Term.Hole -> Typing(C.Term.Hole(term.id), diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quote(END)), term.id)))
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
            Typing(C.Term.ListOf(emptyList(), term.id), C.Value.List(lazyOf(END)))
        } else { // TODO: use union of element types
            val first = inferTerm(term.elements.first())
            val elements = listOf(first.term) + term.elements.drop(1).map { checkTerm(it, first.type) }
            Typing(C.Term.ListOf(elements, term.id), C.Value.List(lazyOf(first.type)))
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { "" to inferTerm(it) }
            Typing(C.Term.CompoundOf(elements.map { it.second.term }, term.id), C.Value.Compound(elements.map { it.first to normalizer.quote(it.second.type) }))
        }
        is S.Term.BoxOf -> {
            val tTag = checkTerm(term.tag, TYPE)
            val vTag = normalizer.eval(tTag)
            val content = checkTerm(term.content, vTag)
            Typing(C.Term.BoxOf(content, tTag, term.id), C.Value.Box(lazyOf(vTag)))
        }
        is S.Term.RefOf -> {
            val element = inferTerm(term.element)
            Typing(C.Term.RefOf(element.term, term.id), C.Value.Ref(lazyOf(element.type)))
        }
        is S.Term.Refl -> {
            val left = lazy { normalizer.fresh(term.id) }
            Typing(C.Term.Refl(term.id), C.Value.Eq(left, left))
        }
        is S.Term.CodeOf -> {
            val element = up().inferTerm(term.element)
            Typing(C.Term.CodeOf(element.term, term.id), C.Value.Code(lazyOf(element.type)))
        }
        is S.Term.Splice -> {
            val element = down().inferTerm(term.element)
            val type = when (val type = normalizer.force(element.type)) {
                is C.Value.Code -> type.element.value
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
            Typing(C.Term.List(element, term.id), TYPE)
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
            null -> Typing(C.Term.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id)))
            is C.Item.Def -> {
                if (item.parameters.size != computation.arguments.size) {
                    diagnose(Diagnostic.ArityMismatch(item.parameters.size, computation.arguments.size, computation.id))
                }
                val (_, arguments) = (computation.arguments zip item.parameters).foldMap(this) { context, (argument, parameter) ->
                    val tArgument = checkTerm(argument, context.normalizer.eval(parameter.type))
                    val vArgument = context.normalizer.eval(tArgument)
                    val lower = parameter.lower?.let { context.normalizer.eval(it) }?.also { lower ->
                        if (!context.subtype(lower, vArgument)) diagnose(Diagnostic.TypeMismatch(serializeTerm(context.normalizer.quote(vArgument)), serializeTerm(context.normalizer.quote(lower)), argument.id))
                    }
                    val upper = parameter.upper?.let { context.normalizer.eval(it) }?.also { upper ->
                        if (!context.subtype(vArgument, upper)) diagnose(Diagnostic.TypeMismatch(serializeTerm(context.normalizer.quote(upper)), serializeTerm(context.normalizer.quote(vArgument)), argument.id))
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
            Typing(C.Term.FunOf(computation.parameters, body.term, computation.id), C.Value.Fun(parameters, normalizer.quote(body.type), body.effects))
        }
        is S.Term.Apply -> {
            val function = inferComputation(computation.function)
            when (val type = normalizer.force(function.type)) {
                is C.Value.Fun -> {
                    if (type.parameters.size != computation.arguments.size) {
                        diagnose(Diagnostic.ArityMismatch(type.parameters.size, computation.arguments.size, computation.id))
                    }
                    val (context, arguments) = (computation.arguments zip type.parameters).foldMap(this) { context, (argument, parameter) ->
                        val tArgument = checkTerm(argument, context.normalizer.eval(parameter.type))
                        val vArgument = context.normalizer.eval(tArgument)
                        val lower = parameter.lower?.let { context.normalizer.eval(it) }?.also { lower ->
                            if (!context.subtype(lower, vArgument)) diagnose(Diagnostic.TypeMismatch(serializeTerm(context.normalizer.quote(vArgument)), serializeTerm(context.normalizer.quote(lower)), argument.id))
                        }
                        val upper = parameter.upper?.let { context.normalizer.eval(it) }?.also { upper ->
                            if (!context.subtype(vArgument, upper)) diagnose(Diagnostic.TypeMismatch(serializeTerm(context.normalizer.quote(upper)), serializeTerm(context.normalizer.quote(vArgument)), argument.id))
                        }
                        val type = context.normalizer.eval(parameter.type)
                        context.bindUnchecked(Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument) to tArgument
                    }
                    val resultant = context.normalizer.eval(type.resultant)
                    Typing(C.Term.Apply(function.term, arguments, computation.id), resultant, type.effects)
                }
                else -> Typing(C.Term.Apply(function.term, computation.arguments.map { checkTerm(it, ANY) }, computation.id), diagnose(Diagnostic.FunctionExpected(computation.function.id)), function.effects)
            }
        }
        is S.Term.Fun -> {
            val (context, parameters) = elaborateParameters(computation.parameters)
            val resultant = context.checkTerm(computation.resultant, TYPE) /* TODO: check effects */
            val effects = computation.effects.map { elaborateEffect(it) }.toSet()
            Typing(C.Term.Fun(parameters, resultant, effects, computation.id), TYPE)
        }
        else -> inferTerm(computation)
    }

    /**
     * Checks the [term] against the [type] under this context.
     */
    private fun Context.checkTerm(term: S.Term, type: C.Value): C.Term {
        val type = normalizer.force(type)
        types[term.id] = type
        return when {
            term is S.Term.Hole -> {
                diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quote(type)), term.id))
                C.Term.Hole(term.id)
            }
            term is S.Term.Meta -> normalizer.quote(normalizer.fresh(term.id))
            term is S.Term.ListOf && type is C.Value.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value) }
                C.Term.ListOf(elements, term.id)
            }
            term is S.Term.CompoundOf && type is C.Value.Compound -> {
                val (_, elements) = (term.elements zip type.elements).foldMap(this) { context, (element, type) ->
                    val vType = context.normalizer.eval(type.second)
                    val element = context.checkTerm(element, vType)
                    val vElement = context.normalizer.eval(element)
                    context.bindUnchecked(Entry(true /* TODO */, type.first, END, ANY, vType, context.stage), vElement) to element
                }
                C.Term.CompoundOf(elements, term.id)
            }
            term is S.Term.RefOf && type is C.Value.Ref -> {
                val element = checkTerm(term.element, type.element.value)
                C.Term.RefOf(element, term.id)
            }
            term is S.Term.CodeOf && type is C.Value.Code -> {
                val element = up().checkTerm(term.element, type.element.value)
                C.Term.CodeOf(element, term.id)
            }
            else -> checkComputation(term, type, emptySet()).term
        }
    }

    /**
     * Checks the [computation] against the [type] and the [effects] under this context.
     */
    private fun Context.checkComputation(computation: S.Term, type: C.Value, effects: Set<C.Effect>): Effecting {
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
            computation is S.Term.FunOf && type is C.Value.Fun -> {
                if (type.parameters.size != computation.parameters.size) {
                    diagnose(Diagnostic.ArityMismatch(type.parameters.size, computation.parameters.size, computation.id))
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
                if (!subtype(computation.type, type)) {
                    types[id] = END
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(normalizer.quote(type)), serializeTerm(normalizer.quote(computation.type)), id))
                }
                if (!(effects.containsAll(computation.effects))) {
                    diagnose(Diagnostic.EffectMismatch(effects.map { serializeEffect(it) }, computation.effects.map { serializeEffect(it) }, id))
                }
                Effecting(computation.term, computation.effects)
            }
        }
    }

    /**
     * Infers the type of the [pattern] under this context.
     */
    private fun Context.inferPattern(pattern: S.Pattern): Triple<Context, C.Pattern, C.Value> = when (pattern) {
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
            Triple(context2, C.Pattern.ListOf(listOf(head) + elements, pattern.id), C.Value.List(lazyOf(headType)))
        }
        is S.Pattern.CompoundOf -> {
            val (context, elements) = pattern.elements.foldMap(this) { context, element ->
                val (context, element, elementType) = context.inferPattern(element)
                context to (element to elementType)
            }
            Triple(context, C.Pattern.CompoundOf(elements.map { it.first }, pattern.id), C.Value.Compound(elements.map { "" to context.normalizer.quote(it.second) }))
        }
        is S.Pattern.BoxOf -> {
            val (context1, content, contentType) = inferPattern(pattern.content)
            val (context2, tag) = context1.checkPattern(pattern.tag, TYPE)
            Triple(context2, C.Pattern.BoxOf(content, tag, pattern.id), C.Value.Box(lazyOf(contentType)))
        }
        is S.Pattern.RefOf -> {
            val (context, element, elementType) = inferPattern(pattern.element)
            Triple(context, C.Pattern.RefOf(element, pattern.id), C.Value.Ref(lazyOf(elementType)))
        }
        is S.Pattern.Refl -> {
            val left = lazy { normalizer.fresh(pattern.id) }
            Triple(this, C.Pattern.Refl(pattern.id), C.Value.Eq(left, left))
        }
        is S.Pattern.Bool -> Triple(this, C.Pattern.Bool(pattern.id), TYPE)
        is S.Pattern.Byte -> Triple(this, C.Pattern.Byte(pattern.id), TYPE)
        is S.Pattern.Short -> Triple(this, C.Pattern.Short(pattern.id), TYPE)
        is S.Pattern.Int -> Triple(this, C.Pattern.Int(pattern.id), TYPE)
        is S.Pattern.Long -> Triple(this, C.Pattern.Long(pattern.id), TYPE)
        is S.Pattern.Float -> Triple(this, C.Pattern.Float(pattern.id), TYPE)
        is S.Pattern.Double -> Triple(this, C.Pattern.Double(pattern.id), TYPE)
        is S.Pattern.String -> Triple(this, C.Pattern.String(pattern.id), TYPE)
        is S.Pattern.ByteArray -> Triple(this, C.Pattern.ByteArray(pattern.id), BYTE_ARRAY)
        is S.Pattern.IntArray -> Triple(this, C.Pattern.IntArray(pattern.id), INT_ARRAY)
        is S.Pattern.LongArray -> Triple(this, C.Pattern.LongArray(pattern.id), LONG_ARRAY)
    }.also { (_, _, type) ->
        types[pattern.id] = type
    }

    /**
     * Checks the [pattern] against the [type] under this context.
     */
    private fun Context.checkPattern(pattern: S.Pattern, type: C.Value): Pair<Context, C.Pattern> {
        val type = normalizer.force(type)
        types[pattern.id] = type
        return when {
            pattern is S.Pattern.Var -> {
                val context = bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, type, stage))
                context to C.Pattern.Var(pattern.name, pattern.id)
            }
            pattern is S.Pattern.ListOf && type is C.Value.List -> {
                val (context, elements) = pattern.elements.foldMap(this) { context, element -> context.checkPattern(element, type.element.value) }
                context to C.Pattern.ListOf(elements, pattern.id)
            }
            pattern is S.Pattern.CompoundOf && type is C.Value.Compound -> {
                val (context, elements) = (pattern.elements zip type.elements).foldMap(this) { context, element -> context.checkPattern(element.first, context.normalizer.eval(element.second.second)) }
                context to C.Pattern.CompoundOf(elements, pattern.id)
            }
            pattern is S.Pattern.BoxOf && type is C.Value.Box -> {
                val (context1, content) = checkPattern(pattern.content, type.content.value)
                val (context2, tag) = context1.checkPattern(pattern.tag, TYPE)
                context2 to C.Pattern.BoxOf(content, tag, pattern.id)
            }
            pattern is S.Pattern.RefOf && type is C.Value.Ref -> {
                val (context, element) = checkPattern(pattern.element, type.element.value)
                context to C.Pattern.RefOf(element, pattern.id)
            }
            pattern is S.Pattern.Refl && type is C.Value.Eq -> {
                val normalizer = normalizer.match(type.left.value, type.right.value)
                withNormalizer(normalizer) to C.Pattern.Refl(pattern.id)
            }
            else -> {
                val (context, inferred, inferredType) = inferPattern(pattern)
                if (!context.subtype(inferredType, type)) {
                    types[pattern.id] = END
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(context.normalizer.quote(type)), serializeTerm(context.normalizer.quote(inferredType)), pattern.id))
                }
                context to inferred
            }
        }
    }

    private fun elaborateEffect(effect: S.Effect): C.Effect = when (effect) {
        is S.Effect.Name -> C.Effect.Name(effect.name)
    }

    /**
     * Checks if the [value1] and the [value2] can be unified under [this] normalizer.
     */
    private fun Normalizer.unify(value1: C.Value, value2: C.Value): Boolean {
        val value1 = force(value1)
        val value2 = force(value2)
        return when {
            value1.id != null && value2.id != null && value1.id == value2.id -> true
            value1 is C.Value.Meta && value2 is C.Value.Meta && value1.index == value2.index -> true
            value1 is C.Value.Meta -> when (val solved1 = getSolution(value1.index)) {
                null -> {
                    solve(value1.index, value2)
                    true
                }
                else -> unify(solved1, value2)
            }
            value2 is C.Value.Meta -> unify(value2, value1)
            value1 is C.Value.Var && value2 is C.Value.Var -> value1.level == value2.level
            value1 is C.Value.Def && value2 is C.Value.Def && value1.name == value2.name -> true
            value1 is C.Value.Match && value2 is C.Value.Match -> unify(value1.scrutinee, value2.scrutinee) &&
                    (value1.clauses zip value2.clauses).all { (clause1, clause2) -> clause1.first == clause2.first && unify(clause1.second.value, clause2.second.value) }
            value1 is C.Value.BoolOf && value2 is C.Value.BoolOf -> value1.value == value2.value
            value1 is C.Value.ByteOf && value2 is C.Value.ByteOf -> value1.value == value2.value
            value1 is C.Value.ShortOf && value2 is C.Value.ShortOf -> value1.value == value2.value
            value1 is C.Value.IntOf && value2 is C.Value.IntOf -> value1.value == value2.value
            value1 is C.Value.LongOf && value2 is C.Value.LongOf -> value1.value == value2.value
            value1 is C.Value.FloatOf && value2 is C.Value.FloatOf -> value1.value == value2.value
            value1 is C.Value.DoubleOf && value2 is C.Value.DoubleOf -> value1.value == value2.value
            value1 is C.Value.StringOf && value2 is C.Value.StringOf -> value1.value == value2.value
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.BoxOf && value2 is C.Value.BoxOf -> unify(value1.content.value, value2.content.value) && unify(value1.tag.value, value2.tag.value)
            value1 is C.Value.RefOf && value2 is C.Value.RefOf -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Refl && value2 is C.Value.Refl -> true
            value1 is C.Value.FunOf && value2 is C.Value.FunOf -> value1.parameters.size == value2.parameters.size &&
                    value1.parameters.fold(this) { normalizer, parameter -> normalizer.bind(lazyOf(C.Value.Var(parameter, normalizer.size))) }.run { unify(eval(value1.body), eval(value2.body)) }
            value1 is C.Value.Apply && value2 is C.Value.Apply -> unify(value1.function, value2.function) &&
                    (value1.arguments zip value2.arguments).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.CodeOf && value2 is C.Value.CodeOf -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Splice && value2 is C.Value.Splice -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Union && value1.variants.isEmpty() && value2 is C.Value.Union && value2.variants.isEmpty() -> true
            value1 is C.Value.Intersection && value1.variants.isEmpty() && value2 is C.Value.Intersection && value2.variants.isEmpty() -> true
            value1 is C.Value.Bool && value2 is C.Value.Bool -> true
            value1 is C.Value.Byte && value2 is C.Value.Byte -> true
            value1 is C.Value.Short && value2 is C.Value.Short -> true
            value1 is C.Value.Int && value2 is C.Value.Int -> true
            value1 is C.Value.Long && value2 is C.Value.Long -> true
            value1 is C.Value.Float && value2 is C.Value.Float -> true
            value1 is C.Value.Double && value2 is C.Value.Double -> true
            value1 is C.Value.String && value2 is C.Value.String -> true
            value1 is C.Value.ByteArray && value2 is C.Value.ByteArray -> true
            value1 is C.Value.IntArray && value2 is C.Value.IntArray -> true
            value1 is C.Value.LongArray && value2 is C.Value.LongArray -> true
            value1 is C.Value.List && value2 is C.Value.List -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Compound && value2 is C.Value.Compound -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).foldAll(this) { normalizer, (element1, element2) ->
                        normalizer.bind(lazyOf(C.Value.Var("", normalizer.size))) to normalizer.unify(normalizer.eval(element1.second), normalizer.eval(element2.second))
                    }.second
            value1 is C.Value.Box && value2 is C.Value.Box -> unify(value1.content.value, value2.content.value)
            value1 is C.Value.Ref && value2 is C.Value.Ref -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Eq && value2 is C.Value.Eq -> unify(value1.left.value, value2.left.value) && unify(value1.right.value, value2.right.value)
            value1 is C.Value.Fun && value2 is C.Value.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (normalizer, success) = (value1.parameters zip value2.parameters).foldAll(this) { normalizer, (parameter1, parameter2) ->
                    normalizer.bind(lazyOf(C.Value.Var("", normalizer.size))) to normalizer.unify(normalizer.eval(parameter2.type), normalizer.eval(parameter1.type))
                }
                success && normalizer.unify(normalizer.eval(value1.resultant), normalizer.eval(value2.resultant))
            } && value1.effects == value2.effects
            value1 is C.Value.Code && value2 is C.Value.Code -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Type && value2 is C.Value.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [value1] is a subtype of the [value2] under [this] context.
     */
    private fun Context.subtype(value1: C.Value, value2: C.Value): Boolean {
        val value1 = normalizer.force(value1)
        val value2 = normalizer.force(value2)
        return when {
            value1 is C.Value.Var && value2 is C.Value.Var && value1.level == value2.level -> true
            value1 is C.Value.Var && entries[value1.level].upper != null -> subtype(entries[value1.level].upper!!, value2)
            value2 is C.Value.Var && entries[value2.level].lower != null -> subtype(value1, entries[value2.level].lower!!)
            value1 is C.Value.Apply && value2 is C.Value.Apply -> subtype(value1.function, value2.function) && (value1.arguments zip value2.arguments).all { normalizer.unify(it.first.value, it.second.value) /* pointwise subtyping */ }
            value1 is C.Value.Union -> value1.variants.all { subtype(it.value, value2) }
            value2 is C.Value.Union -> value2.variants.any { subtype(value1, it.value) }
            value2 is C.Value.Intersection -> value2.variants.all { subtype(value1, it.value) }
            value1 is C.Value.Intersection -> value1.variants.any { subtype(it.value, value2) }
            value1 is C.Value.List && value2 is C.Value.List -> subtype(value1.element.value, value2.element.value)
            value1 is C.Value.Compound && value2 is C.Value.Compound -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).foldAll(this) { context, (elements1, elements2) ->
                        val upper1 = context.normalizer.eval(elements1.second)
                        context.bindUnchecked(Entry(true /* TODO */, "", null, upper1, TYPE, stage)) to context.subtype(upper1, context.normalizer.eval(elements2.second))
                    }.second
            value1 is C.Value.Box && value2 is C.Value.Box -> subtype(value1.content.value, value2.content.value)
            value1 is C.Value.Ref && value2 is C.Value.Ref -> subtype(value1.element.value, value2.element.value)
            value1 is C.Value.Fun && value2 is C.Value.Fun -> value1.parameters.size == value2.parameters.size && run {
                val (context, success) = (value1.parameters zip value2.parameters).foldAll(this) { context, (parameter1, parameter2) ->
                    context.bindUnchecked(Entry(parameter1.relevant, "", null, null /* TODO */, TYPE, stage)) to (parameter1.relevant == parameter2.relevant && context.subtype(
                        context.normalizer.eval(parameter2.type),
                        context.normalizer.eval(parameter1.type)
                    ))
                }
                success && context.subtype(context.normalizer.eval(value1.resultant), context.normalizer.eval(value2.resultant))
            } && value2.effects.containsAll(value1.effects)
            value1 is C.Value.Code && value2 is C.Value.Code -> subtype(value1.element.value, value2.element.value)
            else -> normalizer.unify(value1, value2)
        }
    }

    /**
     * Matches the [value1] and the [value2].
     */
    private fun Normalizer.match(value1: C.Value, value2: C.Value): Normalizer {
        val value1 = force(value1)
        val value2 = force(value2)
        return when {
            value2 is C.Value.Var -> subst(value2.level, lazyOf(value1))
            value1 is C.Value.Var -> subst(value1.level, lazyOf(value2))
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> (value1.elements zip value2.elements).fold(this) { normalizer, (element1, element2) -> normalizer.match(element1.value, element2.value) }
            value1 is C.Value.BoxOf && value2 is C.Value.BoxOf -> match(value1.content.value, value2.content.value).match(value1.tag.value, value2.tag.value)
            value1 is C.Value.RefOf && value2 is C.Value.RefOf -> match(value1.element.value, value2.element.value)
            else -> this // TODO
        }
    }

    private fun Normalizer.checkRepresentation(id: Id, type: C.Value) {
        fun join(left: C.Value, right: C.Value): Boolean = when (val left = force(left)) {
            is C.Value.Union -> left.variants.all { join(it.value, right) }
            is C.Value.Intersection -> left.variants.any { join(it.value, right) }
            is C.Value.Bool, is C.Value.Byte, is C.Value.Eq, is C.Value.Fun, is C.Value.Type -> when (force(right)) {
                is C.Value.Bool, is C.Value.Byte, is C.Value.Eq, is C.Value.Fun, is C.Value.Type -> true
                else -> false
            }
            is C.Value.Short -> right is C.Value.Short
            is C.Value.Int, is C.Value.Ref -> when (force(right)) {
                is C.Value.Int, is C.Value.Ref -> true
                else -> false
            }
            is C.Value.Long -> right is C.Value.Long
            is C.Value.Float -> right is C.Value.Float
            is C.Value.Double -> right is C.Value.Double
            is C.Value.String -> right is C.Value.String
            is C.Value.ByteArray -> right is C.Value.ByteArray
            is C.Value.IntArray -> right is C.Value.IntArray
            is C.Value.LongArray -> right is C.Value.LongArray
            is C.Value.List -> right is C.Value.List
            is C.Value.Compound, is C.Value.Box -> when (force(right)) {
                is C.Value.Compound, is C.Value.Box -> true
                else -> false
            }
            else -> false
        }

        when (type) {
            is C.Value.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            is C.Value.Union -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolymorphicRepresentation(id))
                }
            }
            is C.Value.Intersection -> if (type.variants.isEmpty()) {
                diagnose(Diagnostic.PolymorphicRepresentation(id))
            }
            else -> {}
        }
    }

    private fun diagnose(diagnostic: Diagnostic): C.Value {
        diagnostics += diagnostic
        return END
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.Value>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>
    )

    private data class Typing(
        val term: C.Term,
        val type: C.Value,
        val effects: Set<C.Effect> = emptySet()
    )

    private data class Effecting(
        val term: C.Term,
        val effects: Set<C.Effect>
    )

    private data class Entry(
        val relevant: Boolean,
        val name: String,
        val lower: C.Value?,
        val upper: C.Value?,
        val type: C.Value,
        val stage: Int
    )

    private inner class Context(
        val entries: PersistentList<Entry>,
        val normalizer: Normalizer,
        val meta: Boolean,
        val stage: Int,
        val relevant: Boolean
    ) {
        val size: Int get() = entries.size

        fun lookup(name: String): Int = entries.indexOfLast { it.name == name }

        fun bind(id: Id, entry: Entry, value: C.Value? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(value ?: C.Value.Var(entry.name, size))), meta, stage, relevant).also { checkPhase(id, entry.type) }

        fun bindUnchecked(entry: Entry, value: C.Value? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(value ?: C.Value.Var(entry.name, size))), meta, stage, relevant)

        fun empty(): Context = Context(persistentListOf(), normalizer.empty(), meta, stage, relevant)

        fun withNormalizer(normalizer: Normalizer): Context = Context(entries, normalizer, meta, stage, relevant)

        fun up(): Context = Context(entries, normalizer, meta, stage + 1, relevant)

        fun down(): Context = Context(entries, normalizer, meta, stage - 1, relevant)

        fun relevant(): Context = Context(entries, normalizer, meta, stage, true)

        fun irrelevant(): Context = Context(entries, normalizer, meta, stage, false)

        fun checkPhase(id: Id, type: C.Value) {
            if (!meta && type is C.Value.Code) diagnose(Diagnostic.PhaseMismatch(id))
        }
    }

    companion object {
        private val BOOL = C.Value.Bool()
        private val BYTE = C.Value.Byte()
        private val SHORT = C.Value.Short()
        private val INT = C.Value.Int()
        private val LONG = C.Value.Long()
        private val FLOAT = C.Value.Float()
        private val DOUBLE = C.Value.Double()
        private val STRING = C.Value.String()
        private val BYTE_ARRAY = C.Value.ByteArray()
        private val INT_ARRAY = C.Value.IntArray()
        private val LONG_ARRAY = C.Value.LongArray()
        private val END = C.Value.Union(emptyList())
        private val ANY = C.Value.Intersection(emptyList())
        private val TYPE = C.Value.Type()

        operator fun invoke(item: S.Item, items: Map<String, C.Item>): Result = Elaborate(items).run {
            val (normalizer, item) = elaborateItem(item)
            Result(item, types, normalizer, diagnostics)
        }
    }
}
