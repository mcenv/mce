package mce.phase

import mce.Diagnostic
import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C
import mce.graph.Surface as S

private typealias Environment = List<Lazy<C.Value>>

private typealias Level = Int

private typealias Stage = Int

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
    private val items: Map<String, C.Item>
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val types: MutableMap<Id, Lazy<S.Term>> = mutableMapOf()
    private val metas: MutableList<C.Value?> = mutableListOf()

    private val end: C.Value.Union = C.Value.Union(emptyList())
    private val any: C.Value.Intersection = C.Value.Intersection(emptyList())

    private fun elaborateItem(item: S.Item): C.Item = when (item) {
        is S.Item.Definition -> {
            emptyContext(item.meta).checkTerm(item.type, C.Value.Type)
            val type = emptyEnvironment().evaluate(item.type)
            emptyContext(item.meta).checkTerm(item.body, type)
            emptyContext(item.meta).checkPhase(item.id, type)
            C.Item.Definition(item.name, item.imports, item.body, type)
        }
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun Context.inferTerm(term: S.Term): C.Value = when (term) {
        is S.Term.Hole -> diagnose(Diagnostic.TermExpected(quote(end), term.id))
        is S.Term.Meta -> fresh()
        is S.Term.Variable -> when (val level = entries.indexOfLast { it.name == term.name }) {
            -1 -> diagnose(Diagnostic.VariableNotFound(term.name, term.id))
            else -> {
                val entry = entries[level]
                if (stage != entry.stage) diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id)) else entry.type
            }
        }
        is S.Term.Definition -> when (val item = items[term.name]) {
            null -> diagnose(Diagnostic.DefinitionNotFound(term.name, term.id))
            is C.Item.Definition -> item.type
        }
        is S.Term.Let -> bind(term.id, Entry(term.name, end, any, inferTerm(term.init), stage)).inferTerm(term.body)
        is S.Term.Match -> fresh().also { type ->
            val scrutinee = inferTerm(term.scrutinee)
            term.clauses.forEach {
                checkPattern(it.first, scrutinee)
                checkTerm(it.second, type)
            }
        }
        is S.Term.BooleanOf -> C.Value.Boolean
        is S.Term.ByteOf -> C.Value.Byte
        is S.Term.ShortOf -> C.Value.Short
        is S.Term.IntOf -> C.Value.Int
        is S.Term.LongOf -> C.Value.Long
        is S.Term.FloatOf -> C.Value.Float
        is S.Term.DoubleOf -> C.Value.Double
        is S.Term.StringOf -> C.Value.String
        is S.Term.ByteArrayOf -> {
            term.elements.forEach { checkTerm(it, C.Value.Byte) }
            C.Value.ByteArray
        }
        is S.Term.IntArrayOf -> {
            term.elements.forEach { checkTerm(it, C.Value.Int) }
            C.Value.IntArray
        }
        is S.Term.LongArrayOf -> {
            term.elements.forEach { checkTerm(it, C.Value.Long) }
            C.Value.LongArray
        }
        is S.Term.ListOf -> if (term.elements.isEmpty()) end else {
            val first = inferTerm(term.elements.first())
            term.elements.drop(1).forEach { checkTerm(it, first) }
            C.Value.List(lazyOf(first))
        }
        is S.Term.CompoundOf -> C.Value.Compound(term.elements.map { "" to quote(inferTerm(it)) })
        is S.Term.ReferenceOf -> C.Value.Reference(lazyOf(inferTerm(term.element)))
        is S.Term.FunctionOf -> {
            val types = term.parameters.map { fresh() }
            val body = Context((term.parameters zip types).map { Entry(it.first, end, any, it.second, stage) }.toMutableList(), meta, stage).inferTerm(term.body)
            C.Value.Function((term.parameters zip types).map { S.Parameter(it.first, quote(end), quote(any), quote(it.second)) }, quote(body))
        }
        is S.Term.Apply -> {
            when (val function = force(inferTerm(term.function))) {
                is C.Value.Function -> withEnvironment { environment ->
                    (term.arguments zip function.parameters).forEach { (argument, parameter) ->
                        checkTerm(argument, environment.evaluate(parameter.type))
                        val argument1 = environment.evaluate(argument)
                        environment.evaluate(parameter.lower).let { lower ->
                            if (!entries.size.subtype(lower, argument1)) diagnose(Diagnostic.TypeMismatch(quote(argument1), quote(lower), argument.id))
                        }
                        environment.evaluate(parameter.upper).let { upper ->
                            if (!entries.size.subtype(argument1, upper)) diagnose(Diagnostic.TypeMismatch(quote(upper), quote(argument1), argument.id))
                        }
                        environment += lazyOf(argument1)
                    }
                    environment.evaluate(function.resultant)
                }
                else -> {
                    term.arguments.forEach { checkTerm(it, any) }
                    diagnose(Diagnostic.FunctionExpected(term.function.id))
                }
            }
        }
        is S.Term.CodeOf -> C.Value.Code(lazyOf(up().inferTerm(term.element)))
        is S.Term.Splice -> when (val element = force(down().inferTerm(term.element))) {
            is C.Value.Code -> element.element.value
            else -> diagnose(Diagnostic.CodeExpected(term.id))
        }
        is S.Term.Union -> {
            term.variants.forEach { checkTerm(it, C.Value.Type) }
            C.Value.Type
        }
        is S.Term.Intersection -> {
            term.variants.forEach { checkTerm(it, C.Value.Type) }
            C.Value.Type
        }
        is S.Term.Boolean -> C.Value.Type
        is S.Term.Byte -> C.Value.Type
        is S.Term.Short -> C.Value.Type
        is S.Term.Int -> C.Value.Type
        is S.Term.Long -> C.Value.Type
        is S.Term.Float -> C.Value.Type
        is S.Term.Double -> C.Value.Type
        is S.Term.String -> C.Value.Type
        is S.Term.ByteArray -> C.Value.Type
        is S.Term.IntArray -> C.Value.Type
        is S.Term.LongArray -> C.Value.Type
        is S.Term.List -> {
            checkTerm(term.element, C.Value.Type)
            C.Value.Type
        }
        is S.Term.Compound -> {
            withContext(meta) { environment, context ->
                term.elements.forEach { (name, element) ->
                    name to context.checkTerm(element, C.Value.Type)
                    context.bind(term.id, Entry(name, end, any, environment.evaluate(element), stage))
                    environment += lazyOf(C.Value.Variable(name, environment.size))
                }
            }
            C.Value.Type
        }
        is S.Term.Reference -> {
            checkTerm(term.element, C.Value.Type)
            C.Value.Type
        }
        is S.Term.Function -> {
            withContext(meta) { environment, context ->
                term.parameters.forEach { (name, lower, upper, parameter) ->
                    context.checkTerm(parameter, C.Value.Type)
                    val parameter1 = environment.evaluate(parameter)
                    context.checkTerm(lower, parameter1)
                    context.checkTerm(upper, parameter1)
                    context.bind(term.id, Entry(name, environment.evaluate(lower), environment.evaluate(upper), parameter1, stage))
                    environment += lazyOf(C.Value.Variable(name, environment.size))
                }
                context.checkTerm(term.resultant, C.Value.Type)
            }
            C.Value.Type
        }
        is S.Term.Code -> {
            checkTerm(term.element, C.Value.Type)
            C.Value.Type
        }
        is S.Term.Type -> C.Value.Type
    }.also { types[term.id] = lazy { quote(it) } }

    /**
     * Checks the [term] against the [type] under this context.
     */
    private fun Context.checkTerm(term: S.Term, type: C.Value) {
        val type = force(type)
        types[term.id] = lazy { quote(type) }
        when {
            term is S.Term.Hole -> diagnose(Diagnostic.TermExpected(quote(type), term.id))
            term is S.Term.Let -> bind(term.id, Entry(term.name, end, any, inferTerm(term.init), stage)).checkTerm(term.body, type)
            term is S.Term.Match -> {
                val scrutinee = inferTerm(term.scrutinee)
                term.clauses.forEach {
                    checkPattern(it.first, scrutinee)
                    checkTerm(it.second, type)
                }
            }
            term is S.Term.ListOf && type is C.Value.List -> term.elements.forEach { checkTerm(it, type.element.value) }
            term is S.Term.CompoundOf && type is C.Value.Compound -> withEnvironment { environment ->
                (term.elements zip type.elements).forEach {
                    checkTerm(it.first, environment.evaluate(it.second.second))
                    environment += lazy { environment.evaluate(it.first) }
                }
            }
            term is S.Term.ReferenceOf && type is C.Value.Reference -> checkTerm(term.element, type.element.value)
            term is S.Term.FunctionOf && type is C.Value.Function -> withContext(meta) { environment, context ->
                (term.parameters zip type.parameters).forEach { (name, parameter) ->
                    context.bind(term.id, Entry(name, environment.evaluate(parameter.lower), environment.evaluate(parameter.upper), environment.evaluate(parameter.type), stage))
                    environment += lazyOf(C.Value.Variable(name, environment.size))
                }
                context.checkTerm(term.body, environment.evaluate(type.resultant))
            }
            term is S.Term.CodeOf && type is C.Value.Code -> up().checkTerm(term.element, type.element.value)
            // generalized bottom type
            term is S.Term.Union && term.variants.isEmpty() -> {}
            // generalized top type
            term is S.Term.Intersection && term.variants.isEmpty() -> {}
            else -> {
                val inferred = inferTerm(term)
                if (!entries.size.subtype(inferred, type)) {
                    types[term.id] = lazy { quote(end) }
                    diagnose(Diagnostic.TypeMismatch(quote(type), quote(inferred), term.id))
                }
            }
        }
    }

    /**
     * Infers the type of the [pattern] under this context.
     */
    private fun Context.inferPattern(pattern: S.Pattern): C.Value = when (pattern) {
        is S.Pattern.Variable -> fresh().also { bind(pattern.id, Entry(pattern.name, end, any, it, stage)) }
        is S.Pattern.BooleanOf -> C.Value.Boolean
        is S.Pattern.ByteOf -> C.Value.Byte
        is S.Pattern.ShortOf -> C.Value.Short
        is S.Pattern.IntOf -> C.Value.Int
        is S.Pattern.LongOf -> C.Value.Long
        is S.Pattern.FloatOf -> C.Value.Float
        is S.Pattern.DoubleOf -> C.Value.Double
        is S.Pattern.StringOf -> C.Value.String
        is S.Pattern.ByteArrayOf -> C.Value.ByteArray
        is S.Pattern.IntArrayOf -> C.Value.IntArray
        is S.Pattern.LongArrayOf -> C.Value.LongArray
        is S.Pattern.ListOf -> if (pattern.elements.isEmpty()) end else {
            val first = inferPattern(pattern.elements.first())
            pattern.elements.drop(1).forEach { checkPattern(it, first) }
            C.Value.List(lazyOf(first))
        }
        is S.Pattern.CompoundOf -> C.Value.Compound(pattern.elements.map { "" to quote(inferPattern(it)) })
        is S.Pattern.ReferenceOf -> C.Value.Reference(lazyOf(inferPattern(pattern.element)))
    }.also { types[pattern.id] = lazy { quote(it) } }

    /**
     * Checks the [pattern] against the [type] under this context.
     */
    private fun Context.checkPattern(pattern: S.Pattern, type: C.Value) {
        val type = force(type)
        types[pattern.id] = lazy { quote(type) }
        when {
            pattern is S.Pattern.Variable -> bind(pattern.id, Entry(pattern.name, end, any, type, stage))
            pattern is S.Pattern.ListOf && type is C.Value.List -> pattern.elements.forEach { checkPattern(it, type.element.value) }
            pattern is S.Pattern.CompoundOf && type is C.Value.Compound -> (pattern.elements zip type.elements).forEach { checkPattern(it.first, emptyEnvironment().evaluate(it.second.second)) }
            pattern is S.Pattern.ReferenceOf && type is C.Value.Reference -> checkPattern(pattern.element, type.element.value)
            else -> {
                val inferred = inferPattern(pattern)
                if (!0.subtype(inferred, type)) {
                    types[pattern.id] = lazy { quote(end) }
                    diagnose(Diagnostic.TypeMismatch(quote(type), quote(inferred), pattern.id))
                }
            }
        }
    }

    /**
     * Evaluates the [term] to a value under this environment.
     */
    private fun Environment.evaluate(term: S.Term): C.Value = when (term) {
        is S.Term.Hole -> C.Value.Hole
        is S.Term.Meta -> metas[term.index] ?: C.Value.Meta(term.index)
        is S.Term.Variable -> this[term.level].value
        is S.Term.Definition -> C.Value.Definition(term.name)
        is S.Term.Let -> (this + lazyOf(evaluate(term.init))).evaluate(term.body)
        is S.Term.Match -> C.Value.Match(evaluate(term.scrutinee), term.clauses.map { it.first to lazy { evaluate(it.second) /* TODO: collect variables */ } })
        is S.Term.BooleanOf -> C.Value.BooleanOf(term.value)
        is S.Term.ByteOf -> C.Value.ByteOf(term.value)
        is S.Term.ShortOf -> C.Value.ShortOf(term.value)
        is S.Term.IntOf -> C.Value.IntOf(term.value)
        is S.Term.LongOf -> C.Value.LongOf(term.value)
        is S.Term.FloatOf -> C.Value.FloatOf(term.value)
        is S.Term.DoubleOf -> C.Value.DoubleOf(term.value)
        is S.Term.StringOf -> C.Value.StringOf(term.value)
        is S.Term.ByteArrayOf -> C.Value.ByteArrayOf(term.elements.map { lazy { evaluate(it) } })
        is S.Term.IntArrayOf -> C.Value.IntArrayOf(term.elements.map { lazy { evaluate(it) } })
        is S.Term.LongArrayOf -> C.Value.LongArrayOf(term.elements.map { lazy { evaluate(it) } })
        is S.Term.ListOf -> C.Value.ListOf(term.elements.map { lazy { evaluate(it) } })
        is S.Term.CompoundOf -> C.Value.CompoundOf(term.elements.map { lazy { evaluate(it) } })
        is S.Term.ReferenceOf -> C.Value.ReferenceOf(lazy { evaluate(term.element) })
        is S.Term.FunctionOf -> C.Value.FunctionOf(term.parameters, term.body)
        is S.Term.Apply -> when (val function = evaluate(term.function)) {
            is C.Value.FunctionOf -> term.arguments.map { lazy { evaluate(it) } }.evaluate(function.body)
            else -> C.Value.Apply(function, term.arguments.map { lazy { evaluate(it) } })
        }
        is S.Term.CodeOf -> C.Value.CodeOf(lazy { evaluate(term.element) })
        is S.Term.Splice -> when (val element = evaluate(term.element)) {
            is C.Value.CodeOf -> element.element.value
            else -> C.Value.Splice(lazyOf(element))
        }
        is S.Term.Union -> C.Value.Union(term.variants.map { lazy { evaluate(it) } })
        is S.Term.Intersection -> C.Value.Intersection(term.variants.map { lazy { evaluate(it) } })
        is S.Term.Boolean -> C.Value.Boolean
        is S.Term.Byte -> C.Value.Byte
        is S.Term.Short -> C.Value.Short
        is S.Term.Int -> C.Value.Int
        is S.Term.Long -> C.Value.Long
        is S.Term.Float -> C.Value.Float
        is S.Term.Double -> C.Value.Double
        is S.Term.String -> C.Value.String
        is S.Term.ByteArray -> C.Value.ByteArray
        is S.Term.IntArray -> C.Value.IntArray
        is S.Term.LongArray -> C.Value.LongArray
        is S.Term.List -> C.Value.List(lazy { evaluate(term.element) })
        is S.Term.Compound -> C.Value.Compound(term.elements)
        is S.Term.Reference -> C.Value.Reference(lazy { evaluate(term.element) })
        is S.Term.Function -> C.Value.Function(term.parameters, term.resultant)
        is S.Term.Code -> C.Value.Code(lazy { evaluate(term.element) })
        is S.Term.Type -> C.Value.Type
    }

    /**
     * Quotes the [value] to a term.
     */
    private fun quote(value: C.Value): S.Term = when (val value = force(value)) {
        is C.Value.Hole -> S.Term.Hole(freshId())
        is C.Value.Meta -> metas[value.index]?.let { quote(it) } ?: S.Term.Meta(value.index, freshId())
        is C.Value.Variable -> S.Term.Variable(value.name, value.level, freshId())
        is C.Value.Definition -> S.Term.Definition(value.name, freshId())
        is C.Value.Match -> S.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) }, freshId())
        is C.Value.BooleanOf -> S.Term.BooleanOf(value.value, freshId())
        is C.Value.ByteOf -> S.Term.ByteOf(value.value, freshId())
        is C.Value.ShortOf -> S.Term.ShortOf(value.value, freshId())
        is C.Value.IntOf -> S.Term.IntOf(value.value, freshId())
        is C.Value.LongOf -> S.Term.LongOf(value.value, freshId())
        is C.Value.FloatOf -> S.Term.FloatOf(value.value, freshId())
        is C.Value.DoubleOf -> S.Term.DoubleOf(value.value, freshId())
        is C.Value.StringOf -> S.Term.StringOf(value.value, freshId())
        is C.Value.ByteArrayOf -> S.Term.ByteArrayOf(value.elements.map { quote(it.value) }, freshId())
        is C.Value.IntArrayOf -> S.Term.IntArrayOf(value.elements.map { quote(it.value) }, freshId())
        is C.Value.LongArrayOf -> S.Term.LongArrayOf(value.elements.map { quote(it.value) }, freshId())
        is C.Value.ListOf -> S.Term.ListOf(value.elements.map { quote(it.value) }, freshId())
        is C.Value.CompoundOf -> S.Term.CompoundOf(value.elements.map { quote(it.value) }, freshId())
        is C.Value.ReferenceOf -> S.Term.ReferenceOf(quote(value.element.value), freshId())
        is C.Value.FunctionOf -> S.Term.FunctionOf(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, index)) }
                    .evaluate(value.body)
            ),
            freshId()
        )
        is C.Value.Apply -> S.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) }, freshId())
        is C.Value.CodeOf -> S.Term.CodeOf(quote(value.element.value), freshId())
        is C.Value.Splice -> S.Term.Splice(quote(value.element.value), freshId())
        is C.Value.Union -> S.Term.Union(value.variants.map { quote(it.value) }, freshId())
        is C.Value.Intersection -> S.Term.Intersection(value.variants.map { quote(it.value) }, freshId())
        is C.Value.Boolean -> S.Term.Boolean(freshId())
        is C.Value.Byte -> S.Term.Byte(freshId())
        is C.Value.Short -> S.Term.Short(freshId())
        is C.Value.Int -> S.Term.Int(freshId())
        is C.Value.Long -> S.Term.Long(freshId())
        is C.Value.Float -> S.Term.Float(freshId())
        is C.Value.Double -> S.Term.Double(freshId())
        is C.Value.String -> S.Term.String(freshId())
        is C.Value.ByteArray -> S.Term.ByteArray(freshId())
        is C.Value.IntArray -> S.Term.IntArray(freshId())
        is C.Value.LongArray -> S.Term.LongArray(freshId())
        is C.Value.List -> S.Term.List(quote(value.element.value), freshId())
        is C.Value.Compound -> S.Term.Compound(value.elements, freshId())
        is C.Value.Reference -> S.Term.Reference(quote(value.element.value), freshId())
        is C.Value.Function -> S.Term.Function(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, (name, _) -> lazyOf(C.Value.Variable(name, index)) }
                    .evaluate(value.resultant)
            ),
            freshId()
        )
        is C.Value.Code -> S.Term.Code(quote(value.element.value), freshId())
        is C.Value.Type -> S.Term.Type(freshId())
    }

    /**
     * Checks if the [value1] and the [value2] can be unified.
     */
    private fun Level.unify(value1: C.Value, value2: C.Value): Boolean {
        val value1 = force(value1)
        val value2 = force(value2)
        return when {
            value1 is C.Value.Meta -> when (val solved1 = metas[value1.index]) {
                null -> {
                    metas[value1.index] = value2
                    true
                }
                else -> unify(solved1, value2)
            }
            value2 is C.Value.Meta -> unify(value2, value1)
            value1 is C.Value.Variable && value2 is C.Value.Variable -> value1.level == value2.level
            value1 is C.Value.Definition && value2 is C.Value.Definition -> value1.name == value2.name
            value1 is C.Value.Match && value2 is C.Value.Match -> unify(value1.scrutinee, value2.scrutinee) &&
                    (value1.clauses zip value2.clauses).all { (clause1, clause2) -> clause1.first == clause2.first && unify(clause1.second.value, clause2.second.value) }
            value1 is C.Value.BooleanOf && value2 is C.Value.BooleanOf -> value1.value == value2.value
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
            value1 is C.Value.FunctionOf && value2 is C.Value.FunctionOf -> value1.parameters.size == value2.parameters.size &&
                    value1.parameters
                        .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, this + index)) }
                        .let { (this + value1.parameters.size).unify(it.evaluate(value1.body), it.evaluate(value2.body)) }
            value1 is C.Value.Apply && value2 is C.Value.Apply -> unify(value1.function, value2.function) &&
                    (value1.arguments zip value2.arguments).all { unify(it.first.value, it.second.value) }
            value1 is C.Value.CodeOf && value2 is C.Value.CodeOf -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Splice && value2 is C.Value.Splice -> unify(value1.element.value, value2.element.value)
            // TODO: unify non-empty unions and intersections?
            value1 is C.Value.Union && value1.variants.isEmpty() && value2 is C.Value.Union && value2.variants.isEmpty() -> true
            value1 is C.Value.Intersection && value1.variants.isEmpty() && value2 is C.Value.Intersection && value2.variants.isEmpty() -> true
            value1 is C.Value.Boolean && value2 is C.Value.Boolean -> true
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
                    withEnvironment { environment ->
                        (value1.elements zip value2.elements)
                            .withIndex()
                            .all { (index, elements) ->
                                val (elements1, elements2) = elements
                                (this + index).unify(environment.evaluate(elements1.second), environment.evaluate(elements2.second))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            }
                    }
            value1 is C.Value.Function && value2 is C.Value.Function -> value1.parameters.size == value2.parameters.size &&
                    withEnvironment { environment ->
                        (value1.parameters zip value2.parameters)
                            .withIndex()
                            .all { (index, parameter) ->
                                val (parameter1, parameter2) = parameter
                                (this + index).unify(environment.evaluate(parameter2.type), environment.evaluate(parameter1.type))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            } &&
                                (this + value1.parameters.size).unify(environment.evaluate(value1.resultant), environment.evaluate(value2.resultant))
                    }
            value1 is C.Value.Code && value2 is C.Value.Code -> unify(value1.element.value, value2.element.value)
            value1 is C.Value.Type && value2 is C.Value.Type -> true
            else -> false
        }
    }

    /**
     * Checks if the [value1] is a subtype of the [value2] under this level.
     */
    private fun Level.subtype(value1: C.Value, value2: C.Value): Boolean {
        val value1 = force(value1)
        val value2 = force(value2)
        return when {
            value1 is C.Value.ByteArrayOf && value2 is C.Value.ByteArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { subtype(it.first.value, it.second.value) }
            value1 is C.Value.IntArrayOf && value2 is C.Value.IntArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { subtype(it.first.value, it.second.value) }
            value1 is C.Value.LongArrayOf && value2 is C.Value.LongArrayOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { subtype(it.first.value, it.second.value) }
            value1 is C.Value.ListOf && value2 is C.Value.ListOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { subtype(it.first.value, it.second.value) }
            value1 is C.Value.CompoundOf && value2 is C.Value.CompoundOf -> value1.elements.size == value2.elements.size &&
                    (value1.elements zip value2.elements).all { subtype(it.first.value, it.second.value) }
            value1 is C.Value.FunctionOf && value2 is C.Value.FunctionOf -> value1.parameters.size == value2.parameters.size &&
                    value1.parameters
                        .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, this + index)) }
                        .let { (this + value1.parameters.size).subtype(it.evaluate(value1.body), it.evaluate(value2.body)) }
            value1 is C.Value.Apply && value2 is C.Value.Apply -> subtype(value1.function, value2.function) &&
                    (value1.arguments zip value2.arguments).all { unify(it.first.value, it.second.value) /* pointwise subtyping */ }
            value1 is C.Value.Union -> value1.variants.all { subtype(it.value, value2) }
            value2 is C.Value.Union -> value2.variants.any { subtype(value1, it.value) }
            value1 is C.Value.Intersection -> value1.variants.any { subtype(it.value, value2) }
            value2 is C.Value.Intersection -> value2.variants.all { subtype(value1, it.value) }
            value1 is C.Value.List && value2 is C.Value.List -> subtype(value1.element.value, value2.element.value)
            value1 is C.Value.Compound && value2 is C.Value.Compound -> value1.elements.size == value2.elements.size &&
                    withEnvironment { environment ->
                        (value1.elements zip value2.elements)
                            .withIndex()
                            .all { (index, elements) ->
                                val (elements1, elements2) = elements
                                (this + index).subtype(environment.evaluate(elements1.second), environment.evaluate(elements2.second))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            }
                    }
            value1 is C.Value.Function && value2 is C.Value.Function -> value1.parameters.size == value2.parameters.size &&
                    withEnvironment { environment ->
                        (value1.parameters zip value2.parameters)
                            .withIndex()
                            .all { (index, parameter) ->
                                val (parameter1, parameter2) = parameter
                                (this + index).subtype(environment.evaluate(parameter2.type), environment.evaluate(parameter1.type))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            } &&
                                (this + value1.parameters.size).subtype(environment.evaluate(value1.resultant), environment.evaluate(value2.resultant))
                    }
            value1 is C.Value.Code && value2 is C.Value.Code -> subtype(value1.element.value, value2.element.value)
            else -> unify(value1, value2)
        }
    }

    /**
     * Recursively unfolds meta-variables of the [value].
     */
    private tailrec fun force(value: C.Value): C.Value = when (value) {
        is C.Value.Meta -> when (metas[value.index]) {
            null -> value
            else -> force(value)
        }
        else -> value
    }

    private fun fresh(): C.Value = C.Value.Meta(metas.size).also { metas += null }

    private fun emptyContext(meta: Boolean): Context = Context(mutableListOf(), meta, 0)

    private fun emptyEnvironment(): Environment = mutableListOf()

    private inline fun <R> withContext(meta: Boolean, block: (MutableList<Lazy<C.Value>>, Context) -> R): R = block(mutableListOf(), emptyContext(meta))

    private inline fun <R> withEnvironment(block: (MutableList<Lazy<C.Value>>) -> R): R = block(mutableListOf())

    private fun diagnose(diagnostic: Diagnostic): C.Value {
        diagnostics += diagnostic
        return end
    }

    data class Output(
        val item: C.Item,
        val diagnostics: List<Diagnostic>,
        val types: Map<Id, Lazy<S.Term>>
    )

    private data class Entry(
        val name: String,
        val lower: C.Value,
        val upper: C.Value,
        val type: C.Value,
        val stage: Stage
    )

    private inner class Context(
        val entries: MutableList<Entry>,
        val meta: Boolean,
        var stage: Stage
    ) {
        fun checkPhase(id: Id, type: C.Value) {
            if (!meta && type is C.Value.Code) diagnose(Diagnostic.PhaseMismatch(id))
        }

        fun bind(id: Id, entry: Entry): Context = apply {
            checkPhase(id, entry.type)
            entries += entry
        }

        fun up(): Context = apply { ++stage }

        fun down(): Context = apply { --stage }
    }

    companion object {
        operator fun invoke(items: Map<String, C.Item>, item: S.Item): Output = Elaborate(items).run {
            Output(elaborateItem(item), diagnostics, types)
        }
    }
}
