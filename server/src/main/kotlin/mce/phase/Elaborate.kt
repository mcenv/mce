package mce.phase

import mce.Diagnostic
import mce.Diagnostic.Companion.serializeEffect
import mce.Diagnostic.Companion.serializeTerm
import mce.graph.Id
import mce.graph.Core as C
import mce.graph.Surface as S

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
    private val items: Map<String, C.Item>
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val types: MutableMap<Id, C.Value> = mutableMapOf()
    private val normalizer: Normalizer = Normalizer(mutableListOf(), items, mutableListOf())
    private val entries: MutableList<Entry> = mutableListOf()
    private val size: Int get() = entries.size
    private var wavefront: Int = 0
    private var meta: Boolean = false
    private var stage: Int = 0

    private fun elaborateItem(item: S.Item): C.Item = when (item) {
        is S.Item.Def -> {
            meta = item.modifiers.contains(S.Modifier.META)
            val modifiers = item.modifiers.mapTo(mutableSetOf()) { elaborateModifier(it) }
            val type = normalizer.eval(checkTerm(item.type, TYPE))
            val body = if (item.modifiers.contains(S.Modifier.BUILTIN)) C.Term.Hole(item.body.id) else checkTerm(item.body, type)
            checkPhase(item.body.id, type)
            C.Item.Def(item.imports, item.exports, modifiers, item.name, type, body)
        }
    }

    private fun elaborateModifier(modifier: S.Modifier): C.Modifier = when (modifier) {
        S.Modifier.BUILTIN -> C.Modifier.BUILTIN
        S.Modifier.META -> C.Modifier.META
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun inferTerm(term: S.Term): Typing<C.Term> = when (term) {
        is S.Term.Hole -> Typing(C.Term.Hole(term.id), diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quote(END)), term.id)))
        is S.Term.Meta -> {
            val type = normalizer.fresh(term.id)
            Typing(checkTerm(term, type), type)
        }
        is S.Term.Name -> when (val level = entries.subList(wavefront, size).indexOfLast { it.name == term.name }) {
            -1 -> {
                val type = when (val item = items[term.name]) {
                    null -> diagnose(Diagnostic.NameNotFound(term.name, term.id))
                    is C.Item.Def -> item.type
                }
                Typing(C.Term.Def(term.name, term.id), type)
            }
            else -> {
                val level = wavefront + level
                val entry = entries[level]
                val type = if (stage != entry.stage) diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id)) else entry.type
                Typing(C.Term.Var(term.name, level, term.id), type)
            }
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
            checkRepresentation(term.elements.first().id, first.type)
            val elements = listOf(first.element) + term.elements.drop(1).map { checkTerm(it, first.type) }
            Typing(C.Term.ListOf(elements, term.id), C.Value.List(lazyOf(first.type)))
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { "" to inferTerm(it) }
            Typing(C.Term.CompoundOf(elements.map { it.second.element }, term.id), C.Value.Compound(elements.map { it.first to normalizer.quote(it.second.type) }))
        }
        is S.Term.RefOf -> {
            val element = inferTerm(term.element)
            Typing(C.Term.RefOf(element.element, term.id), C.Value.Ref(lazyOf(element.type)))
        }
        is S.Term.Refl -> {
            val left = lazy { normalizer.fresh(term.id) }
            Typing(C.Term.Refl(term.id), C.Value.Eq(left, left))
        }
        is S.Term.CodeOf -> {
            val element = up { inferTerm(term.element) }
            Typing(C.Term.CodeOf(element.element, term.id), C.Value.Code(lazyOf(element.type)))
        }
        is S.Term.Splice -> {
            val element = down { inferTerm(term.element) }
            val type = when (val type = normalizer.force(element.type)) {
                is C.Value.Code -> type.element.value
                else -> diagnose(Diagnostic.CodeExpected(term.id))
            }
            Typing(C.Term.Splice(element.element, term.id), type)
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
            checkRepresentation(term.element.id, normalizer.eval(element))
            Typing(C.Term.List(element, term.id), TYPE)
        }
        is S.Term.Compound -> {
            val elements = scope {
                term.elements.map { (name, element) ->
                    (name to checkTerm(element, TYPE)).also { (name, element) ->
                        bind(term.id, Entry(name, END, ANY, eval(element), stage))
                    }
                }
            }
            Typing(C.Term.Compound(elements, term.id), TYPE)
        }
        is S.Term.Ref -> {
            val element = checkTerm(term.element, TYPE)
            Typing(C.Term.Ref(element, term.id), TYPE)
        }
        is S.Term.Eq -> {
            val left = inferTerm(term.left)
            val right = checkTerm(term.right, left.type)
            Typing(C.Term.Eq(left.element, right, term.id), TYPE)
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
    private fun inferComputation(computation: S.Term): Typing<C.Term> = when (computation) {
        is S.Term.Let -> {
            val init = inferComputation(computation.init)
            val body = scope {
                bind(computation.init.id, Entry(computation.name, END, ANY, init.type, stage))
                inferComputation(computation.body)
            }
            Typing(C.Term.Let(computation.name, init.element, body.element, computation.id), body.type, init.effects + body.effects)
        }
        is S.Term.Match -> {
            val type = normalizer.fresh(computation.id) // TODO: use union of element types
            val scrutinee = inferTerm(computation.scrutinee)
            val clauses = computation.clauses.map { (pattern, body) ->
                scope {
                    val pattern = checkPattern(pattern, scrutinee.type)
                    val body = checkTerm(body, type) // TODO
                    (pattern to body)
                }
            }
            Typing(C.Term.Match(scrutinee.element, clauses, computation.id), type)
        }
        is S.Term.FunOf -> {
            val types = computation.parameters.map { normalizer.fresh(computation.id) }
            val body = scope(true) {
                (computation.parameters zip types).forEach { bind(computation.id, Entry(it.first, END, ANY, it.second, stage)) }
                inferComputation(computation.body)
            }
            val parameters = (computation.parameters zip types).map { C.Parameter(it.first, normalizer.quote(END), normalizer.quote(ANY), normalizer.quote(it.second)) }
            Typing(C.Term.FunOf(computation.parameters, body.element, computation.id), C.Value.Fun(parameters, normalizer.quote(body.type), body.effects))
        }
        is S.Term.Apply -> {
            val function = inferComputation(computation.function)
            when (val type = normalizer.force(function.type)) {
                is C.Value.Fun -> {
                    if (type.parameters.size != computation.arguments.size) {
                        diagnose(Diagnostic.ArityMismatch(type.parameters.size, computation.arguments.size, computation.id))
                    }
                    normalizer.scope {
                        val arguments = (computation.arguments zip type.parameters).map { (argument, parameter) ->
                            checkTerm(argument, eval(parameter.type)).also {
                                val id = argument.id
                                val argument = eval(it)
                                parameter.lower?.let { lower ->
                                    eval(lower).let { lower ->
                                        if (!(lower subtype argument)) diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(argument)), serializeTerm(quote(lower)), id))
                                    }
                                }
                                parameter.upper?.let { upper ->
                                    eval(upper).let { upper ->
                                        if (!(argument subtype upper)) diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(upper)), serializeTerm(quote(argument)), id))
                                    }
                                }
                                bind(lazyOf(argument)) // TODO: entry bind?
                            }
                        }
                        val resultant = eval(type.resultant)
                        Typing(C.Term.Apply(function.element, arguments, computation.id), resultant, type.effects)
                    }
                }
                else -> Typing(C.Term.Apply(function.element, computation.arguments.map { checkTerm(it, ANY) }, computation.id), diagnose(Diagnostic.FunctionExpected(computation.function.id)), function.effects)
            }
        }
        is S.Term.Fun -> Typing(
            scope {
                val parameters = computation.parameters.map { (name, lower, upper, parameter) ->
                    val parameter = checkTerm(parameter, TYPE)
                    val type = eval(parameter)
                    val lower = lower?.let { checkTerm(it, type) }
                    val upper = upper?.let { checkTerm(it, type) }
                    C.Parameter(name, lower, upper, parameter).also {
                        val lower = lower?.let { eval(it) }
                        val upper = upper?.let { eval(it) }
                        bind(computation.id, Entry(name, lower, upper, type, stage))
                    }
                }
                val resultant = checkTerm(computation.resultant, TYPE) /* TODO: check effects */
                val effects = computation.effects.map { elaborateEffect(it) }.toSet()
                C.Term.Fun(parameters, resultant, effects, computation.id)
            },
            TYPE
        )
        else -> inferTerm(computation)
    }

    /**
     * Checks the [term] against the [type] under this context.
     */
    private fun checkTerm(term: S.Term, type: C.Value): C.Term {
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
            term is S.Term.CompoundOf && type is C.Value.Compound -> normalizer.scope {
                val elements = (term.elements zip type.elements).map { (term, type) ->
                    checkTerm(term, eval(type.second)).also {
                        bind(lazy { eval(it) })
                    }
                }
                C.Term.CompoundOf(elements, term.id)
            }
            term is S.Term.RefOf && type is C.Value.Ref -> {
                val element = checkTerm(term.element, type.element.value)
                C.Term.RefOf(element, term.id)
            }
            term is S.Term.CodeOf && type is C.Value.Code -> {
                val element = up { checkTerm(term.element, type.element.value) }
                C.Term.CodeOf(element, term.id)
            }
            else -> checkComputation(term, type, emptySet()).term
        }
    }

    /**
     * Checks the [computation] against the [type] and the [effects] under this context.
     */
    private fun checkComputation(computation: S.Term, type: C.Value, effects: Set<C.Effect>): Effecting {
        val type = normalizer.force(type)
        types[computation.id] = type
        return when {
            computation is S.Term.Let -> {
                val init = inferComputation(computation.init)
                val body = scope {
                    bind(computation.init.id, Entry(computation.name, END, ANY, init.type, stage))
                    checkComputation(computation.body, type, effects)
                }
                val effects = init.effects + body.effects
                Effecting(C.Term.Let(computation.name, init.element, body.term, computation.id), effects)
            }
            computation is S.Term.Match -> {
                val scrutinee = inferTerm(computation.scrutinee)
                val clauses = computation.clauses.map { (pattern, body) ->
                    scope {
                        val pattern = checkPattern(pattern, scrutinee.type)
                        val body = checkComputation(body, type, effects)
                        pattern to body
                    }
                }
                val effects = clauses.flatMap { (_, body) -> body.effects }.toSet()
                Effecting(C.Term.Match(scrutinee.element, clauses.map { (pattern, body) -> pattern to body.term }, computation.id), effects)
            }
            computation is S.Term.FunOf && type is C.Value.Fun -> {
                if (type.parameters.size != computation.parameters.size) {
                    diagnose(Diagnostic.ArityMismatch(type.parameters.size, computation.parameters.size, computation.id))
                }
                scope(true) {
                    (computation.parameters zip type.parameters).forEach { (name, parameter) ->
                        val lower = parameter.lower?.let { eval(it) }
                        val upper = parameter.upper?.let { eval(it) }
                        val type = eval(parameter.type)
                        bind(computation.id, Entry(name, lower, upper, type, stage))
                    }
                    val resultant = checkComputation(computation.body, eval(type.resultant), effects)
                    Effecting(C.Term.FunOf(computation.parameters, resultant.term, computation.id), emptySet())
                }
            }
            else -> {
                val id = computation.id
                val computation = inferComputation(computation)
                // reevaluate types under updated environment
                val expected = normalizer.eval(normalizer.quote(type))
                val actual = normalizer.eval(normalizer.quote(computation.type))
                if (!(actual subtype expected)) {
                    types[id] = END
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(normalizer.quote(expected)), serializeTerm(normalizer.quote(actual)), id))
                }
                if (!(effects.containsAll(computation.effects))) {
                    diagnose(Diagnostic.EffectMismatch(effects.map { serializeEffect(it) }, computation.effects.map { serializeEffect(it) }, id))
                }
                Effecting(computation.element, computation.effects)
            }
        }
    }

    /**
     * Infers the type of the [pattern] under this context.
     */
    private fun inferPattern(pattern: S.Pattern): Typing<C.Pattern> = when (pattern) {
        is S.Pattern.Variable -> {
            val type = normalizer.fresh(pattern.id)
            bind(pattern.id, Entry(pattern.name, END, ANY, type, stage))
            Typing(C.Pattern.Var(pattern.name, pattern.id), type)
        }
        is S.Pattern.BoolOf -> Typing(C.Pattern.BoolOf(pattern.value, pattern.id), BOOL)
        is S.Pattern.ByteOf -> Typing(C.Pattern.ByteOf(pattern.value, pattern.id), BYTE)
        is S.Pattern.ShortOf -> Typing(C.Pattern.ShortOf(pattern.value, pattern.id), SHORT)
        is S.Pattern.IntOf -> Typing(C.Pattern.IntOf(pattern.value, pattern.id), INT)
        is S.Pattern.LongOf -> Typing(C.Pattern.LongOf(pattern.value, pattern.id), LONG)
        is S.Pattern.FloatOf -> Typing(C.Pattern.FloatOf(pattern.value, pattern.id), FLOAT)
        is S.Pattern.DoubleOf -> Typing(C.Pattern.DoubleOf(pattern.value, pattern.id), DOUBLE)
        is S.Pattern.StringOf -> Typing(C.Pattern.StringOf(pattern.value, pattern.id), STRING)
        is S.Pattern.ByteArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, BYTE) }
            Typing(C.Pattern.ByteArrayOf(elements, pattern.id), BYTE_ARRAY)
        }
        is S.Pattern.IntArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, INT) }
            Typing(C.Pattern.IntArrayOf(elements, pattern.id), INT_ARRAY)
        }
        is S.Pattern.LongArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, LONG) }
            Typing(C.Pattern.LongArrayOf(elements, pattern.id), LONG_ARRAY)
        }
        is S.Pattern.ListOf -> if (pattern.elements.isEmpty()) {
            Typing(C.Pattern.ListOf(emptyList(), pattern.id), END)
        } else { // TODO: use union of element types
            val first = inferPattern(pattern.elements.first())
            val elements = pattern.elements.drop(1).map { checkPattern(it, first.type) }
            Typing(C.Pattern.ListOf(elements, pattern.id), C.Value.List(lazyOf(first.type)))
        }
        is S.Pattern.CompoundOf -> {
            val elements = pattern.elements.map { "" to inferPattern(it) }
            Typing(C.Pattern.CompoundOf(elements.map { it.second.element }, pattern.id), C.Value.Compound(elements.map { it.first to normalizer.quote(it.second.type) }))
        }
        is S.Pattern.RefOf -> {
            val element = inferPattern(pattern.element)
            Typing(C.Pattern.RefOf(element.element, pattern.id), C.Value.Ref(lazyOf(element.type)))
        }
        is S.Pattern.Refl -> {
            val left = lazy { normalizer.fresh(pattern.id) }
            Typing(C.Pattern.Refl(pattern.id), C.Value.Eq(left, left))
        }
    }.also {
        types[pattern.id] = it.type
    }

    /**
     * Checks the [pattern] against the [type] under this context.
     */
    private fun checkPattern(pattern: S.Pattern, type: C.Value): C.Pattern {
        val type = normalizer.force(type)
        types[pattern.id] = type
        return when {
            pattern is S.Pattern.Variable -> {
                bind(pattern.id, Entry(pattern.name, END, ANY, type, stage))
                C.Pattern.Var(pattern.name, pattern.id)
            }
            pattern is S.Pattern.ListOf && type is C.Value.List -> {
                val elements = pattern.elements.map { checkPattern(it, type.element.value) }
                C.Pattern.ListOf(elements, pattern.id)
            }
            pattern is S.Pattern.CompoundOf && type is C.Value.Compound -> {
                val elements = (pattern.elements zip type.elements).map { checkPattern(it.first, normalizer.eval(it.second.second)) }
                C.Pattern.CompoundOf(elements, pattern.id)
            }
            pattern is S.Pattern.RefOf && type is C.Value.Ref -> {
                val element = checkPattern(pattern.element, type.element.value)
                C.Pattern.RefOf(element, pattern.id)
            }
            pattern is S.Pattern.Refl && type is C.Value.Eq -> {
                type.left.value match type.right.value
                C.Pattern.Refl(pattern.id)
            }
            else -> {
                val inferred = inferPattern(pattern)
                if (!(inferred.type subtype type)) {
                    types[pattern.id] = END
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(normalizer.quote(type)), serializeTerm(normalizer.quote(inferred.type)), pattern.id))
                }
                inferred.element
            }
        }
    }

    private fun elaborateEffect(effect: S.Effect): C.Effect = when (effect) {
        is S.Effect.Name -> C.Effect.Name(effect.name)
    }

    /**
     * Checks if the [this] value and the [that] value can be unified.
     */
    private infix fun C.Value.unify(that: C.Value): Boolean {
        val value1 = normalizer.force(this)
        val value2 = normalizer.force(that)
        return when {
            value1.id != null && value2.id != null && value1.id == value2.id -> true
            value1 is C.Value.Meta && value2 is C.Value.Meta && value1.index == value2.index -> true
            value1 is C.Value.Meta -> when (val solved1 = normalizer.getSolution(value1.index)) {
                null -> {
                    normalizer.solve(value1.index, value2)
                    true
                }
                else -> solved1 unify value2
            }
            value2 is C.Value.Meta -> value2 unify value1
            value1 is C.Value.Var && value2 is C.Value.Var -> value1.level == value2.level
            value1 is C.Value.Def && value2 is C.Value.Def && value1.name == value2.name -> true
            value1 is C.Value.Match && value2 is C.Value.Match -> value1.scrutinee unify value2.scrutinee &&
                    (value1.clauses zip value2.clauses).all { (clause1, clause2) -> clause1.first == clause2.first && clause1.second.value unify clause2.second.value }
            value1 is C.Value.BoolOf && value2 is C.Value.BoolOf -> value1.value == value2.value
            value1 is C.Value.ByteOf && value2 is C.Value.ByteOf -> value1.value == value2.value
            value1 is C.Value.ShortOf && value2 is C.Value.ShortOf -> value1.value == value2.value
            value1 is C.Value.IntOf && value2 is C.Value.IntOf -> value1.value == value2.value
            value1 is C.Value.LongOf && value2 is C.Value.LongOf -> value1.value == value2.value
            value1 is C.Value.FloatOf && value2 is C.Value.FloatOf -> value1.value == value2.value
            value1 is C.Value.DoubleOf && value2 is C.Value.DoubleOf -> value1.value == value2.value
            value1 is C.Value.StringOf && value2 is C.Value.StringOf -> value1.value == value2.value
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value unify it.second.value }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value unify it.second.value }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value unify it.second.value }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value unify it.second.value }
            value1 is C.Value.RefOf && value2 is C.Value.RefOf -> value1.element.value unify value2.element.value
            value1 is C.Value.Refl && value2 is C.Value.Refl -> true
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value unify it.second.value }
            value1 is C.Value.FunOf && value2 is C.Value.FunOf -> value1.parameters.size == value2.parameters.size && normalizer.scope {
                value1.parameters.forEach { bind(lazyOf(C.Value.Var(it, size))) }
                eval(value1.body) unify eval(value2.body)
            }
            value1 is C.Value.Apply && value2 is C.Value.Apply -> value1.function unify value2.function &&
                    (value1.arguments zip value2.arguments).all { it.first.value unify it.second.value }
            value1 is C.Value.CodeOf && value2 is C.Value.CodeOf -> value1.element.value unify value2.element.value
            value1 is C.Value.Splice && value2 is C.Value.Splice -> value1.element.value unify value2.element.value
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
            value1 is C.Value.List && value2 is C.Value.List -> value1.element.value unify value2.element.value
            value1 is C.Value.Compound && value2 is C.Value.Compound -> value1.elements.size == value2.elements.size && scope {
                (value1.elements zip value2.elements).all { (element1, element2) ->
                    (eval(element1.second) unify eval(element2.second)).also { bind(lazyOf(C.Value.Var("", size))) }
                }
            }
            value1 is C.Value.Ref && value2 is C.Value.Ref -> value1.element.value unify value2.element.value
            value1 is C.Value.Eq && value2 is C.Value.Eq -> value1.left.value unify value2.left.value && value1.right.value unify value2.right.value
            value1 is C.Value.Fun && value2 is C.Value.Fun -> value1.parameters.size == value2.parameters.size && scope {
                (value1.parameters zip value2.parameters).all { (parameter1, parameter2) ->
                    (eval(parameter2.type) unify eval(parameter1.type)).also { bind(lazyOf(C.Value.Var("", size))) }
                } && eval(value1.resultant) unify eval(value2.resultant)
            } && value1.effects == value2.effects
            value1 is C.Value.Code && value2 is C.Value.Code -> value1.element.value unify value2.element.value
            value1 is C.Value.Type && value2 is C.Value.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [this] value is a subtype of the [that] value under this level.
     */
    private infix fun C.Value.subtype(that: C.Value): Boolean {
        val value1 = normalizer.force(this)
        val value2 = normalizer.force(that)
        return when {
            value1 is C.Value.Var && entries[value1.level].upper != null -> entries[value1.level].upper!! subtype value2
            value2 is C.Value.Var && entries[value2.level].lower != null -> value1 subtype entries[value2.level].lower!!
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value subtype it.second.value }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value subtype it.second.value }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value subtype it.second.value }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value subtype it.second.value }
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { it.first.value subtype it.second.value }
            value1 is C.Value.RefOf && value2 is C.Value.RefOf -> value1.element.value subtype value2.element.value
            value1 is C.Value.FunOf && value2 is C.Value.FunOf -> value1.parameters.size == value2.parameters.size && scope {
                value1.parameters.forEach { parameter -> bind(lazyOf(C.Value.Var(parameter, size))) }
                eval(value1.body) subtype eval(value2.body)
            }
            value1 is C.Value.Apply && value2 is C.Value.Apply -> value1.function subtype value2.function &&
                    (value1.arguments zip value2.arguments).all { it.first.value unify it.second.value /* pointwise subtyping */ }
            value1 is C.Value.Union -> value1.variants.all { it.value subtype value2 }
            value2 is C.Value.Union -> value2.variants.any { value1 subtype it.value }
            value1 is C.Value.Intersection -> value1.variants.any { it.value subtype value2 }
            value2 is C.Value.Intersection -> value2.variants.all { value1 subtype it.value }
            value1 is C.Value.List && value2 is C.Value.List -> value1.element.value subtype value2.element.value
            value1 is C.Value.Compound && value2 is C.Value.Compound -> value1.elements.size == value2.elements.size && scope {
                (value1.elements zip value2.elements).all { (elements1, elements2) ->
                    (eval(elements1.second) subtype eval(elements2.second)).also { bind(lazyOf(C.Value.Var("", size))) }
                }
            }
            value1 is C.Value.Ref && value2 is C.Value.Ref -> value1.element.value subtype value2.element.value
            value1 is C.Value.Fun && value2 is C.Value.Fun -> value1.parameters.size == value2.parameters.size && scope {
                (value1.parameters zip value2.parameters).all { (parameter1, parameter2) ->
                    (eval(parameter2.type) subtype eval(parameter1.type)).also { bind(lazyOf(C.Value.Var("", size))) }
                } && eval(value1.resultant) subtype eval(value2.resultant)
            } && value2.effects.containsAll(value1.effects)
            value1 is C.Value.Code && value2 is C.Value.Code -> value1.element.value subtype value2.element.value
            else -> value1 unify value2
        }
    }

    /**
     * Matches the [this] value and the [that] value.
     */
    private infix fun C.Value.match(that: C.Value) {
        val value1 = normalizer.force(this)
        val value2 = normalizer.force(that)
        when {
            value1 is C.Value.Var -> normalizer.substitute(value1.level, lazyOf(value2))
            value2 is C.Value.Var -> normalizer.substitute(value2.level, lazyOf(value1))
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> (value1.elements zip value2.elements).forEach { it.first.value match it.second.value }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> (value1.elements zip value2.elements).forEach { it.first.value match it.second.value }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> (value1.elements zip value2.elements).forEach { it.first.value match it.second.value }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> (value1.elements zip value2.elements).forEach { it.first.value match it.second.value }
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> (value1.elements zip value2.elements).forEach { it.first.value match it.second.value }
            value1 is C.Value.RefOf && value2 is C.Value.RefOf -> value1.element.value match value2.element.value
            else -> {} // TODO
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

    private data class Typing<out E>(
        val element: E,
        val type: C.Value,
        val effects: Set<C.Effect> = emptySet()
    )

    private data class Effecting(
        val term: C.Term,
        val effects: Set<C.Effect>
    )

    private data class Entry(
        val name: String,
        val lower: C.Value?,
        val upper: C.Value?,
        val type: C.Value,
        val stage: Int
    )

    private fun checkRepresentation(id: Id, value: C.Value) {
        when (value) {
            is C.Value.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            else -> {}
        }
    }

    private fun checkPhase(id: Id, type: C.Value) {
        if (!meta && type is C.Value.Code) diagnose(Diagnostic.PhaseMismatch(id))
    }

    private inline fun <R> up(block: () -> R): R {
        ++stage
        return block().also { --stage }
    }

    private inline fun <R> down(block: () -> R): R {
        --stage
        return block().also { ++stage }
    }

    private inline fun <R> scope(closed: Boolean = false, crossinline block: Normalizer.() -> R): R {
        val size = this.size
        val wavefront = this.wavefront
        if (closed) this.wavefront = size
        return normalizer.scope { block(normalizer) }.also {
            repeat(this.size - size) {
                this.entries.removeLast()
            }
            this.wavefront = wavefront
        }
    }

    private fun bind(id: Id, entry: Entry, value: C.Value? = null) {
        checkPhase(id, entry.type)
        entries += entry
        normalizer.bind(lazyOf(value ?: C.Value.Var(entry.name, normalizer.size)))
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
            Result(elaborateItem(item), types, normalizer, diagnostics)
        }
    }
}
