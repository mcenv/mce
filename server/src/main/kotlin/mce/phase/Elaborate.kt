package mce.phase

import mce.Diagnostic
import mce.Diagnostic.Companion.serializeTerm
import mce.graph.Id
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

    private fun elaborateItem(item: S.Item): C.Item = when (item) {
        is S.Item.Definition -> {
            val meta = item.modifiers.contains(S.Modifier.META)
            val type = emptyEnvironment().evaluate(emptyContext(meta).checkTerm(item.type, C.Value.Type))
            val body = emptyContext(meta).checkTerm(item.body, type)
            emptyContext(meta).checkPhase(item.id, type)
            C.Item.Definition(item.imports, item.name, body, type)
        }
    }

    /**
     * Infers the type of the [term] under this context.
     */
    private fun Context.inferTerm(term: S.Term): Typing<C.Term> = when (term) {
        is S.Term.Hole -> Typing(C.Term.Hole, diagnose(Diagnostic.TermExpected(serializeTerm(quote(END)), term.id)))
        is S.Term.Meta -> Typing(C.Term.Meta(term.index), fresh())
        is S.Term.Variable -> {
            val level = entries.indexOfLast { it.name == term.name }
            val type = when (level) {
                -1 -> diagnose(Diagnostic.VariableNotFound(term.name, term.id))
                else -> {
                    val entry = entries[level]
                    if (stage != entry.stage) diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id)) else entry.type
                }
            }
            Typing(C.Term.Variable(term.name, level), type)
        }
        is S.Term.Definition -> {
            val type = when (val item = items[term.name]) {
                null -> diagnose(Diagnostic.DefinitionNotFound(term.name, term.id))
                is C.Item.Definition -> item.type
            }
            Typing(C.Term.Definition(term.name), type)
        }
        is S.Term.Let -> {
            val init = inferTerm(term.init)
            val body = bind(term.id, Entry(term.name, END, ANY, init.type, stage)).inferTerm(term.body)
            Typing(C.Term.Let(term.name, init.element, body.element), body.type)
        }
        is S.Term.Match -> {
            val type = fresh() // TODO: use union of element types
            val scrutinee = inferTerm(term.scrutinee)
            val clauses = term.clauses.map { checkPattern(it.first, scrutinee.type) to checkTerm(it.second, type) }
            Typing(C.Term.Match(scrutinee.element, clauses), type)
        }
        is S.Term.BooleanOf -> Typing(C.Term.BooleanOf(term.value), C.Value.Boolean)
        is S.Term.ByteOf -> Typing(C.Term.ByteOf(term.value), C.Value.Byte)
        is S.Term.ShortOf -> Typing(C.Term.ShortOf(term.value), C.Value.Short)
        is S.Term.IntOf -> Typing(C.Term.IntOf(term.value), C.Value.Int)
        is S.Term.LongOf -> Typing(C.Term.LongOf(term.value), C.Value.Long)
        is S.Term.FloatOf -> Typing(C.Term.FloatOf(term.value), C.Value.Float)
        is S.Term.DoubleOf -> Typing(C.Term.DoubleOf(term.value), C.Value.Double)
        is S.Term.StringOf -> Typing(C.Term.StringOf(term.value), C.Value.String)
        is S.Term.ByteArrayOf -> {
            val elements = term.elements.map { checkTerm(it, C.Value.Byte) }
            Typing(C.Term.ByteArrayOf(elements), C.Value.ByteArray)
        }
        is S.Term.IntArrayOf -> {
            val elements = term.elements.map { checkTerm(it, C.Value.Int) }
            Typing(C.Term.IntArrayOf(elements), C.Value.IntArray)
        }
        is S.Term.LongArrayOf -> {
            val elements = term.elements.map { checkTerm(it, C.Value.Long) }
            Typing(C.Term.LongArrayOf(elements), C.Value.LongArray)
        }
        is S.Term.ListOf -> if (term.elements.isEmpty()) Typing(C.Term.ListOf(emptyList()), END) else { // TODO: use union of element types
            val first = inferTerm(term.elements.first())
            Typing(C.Term.ListOf(listOf(first.element) + term.elements.drop(1).map { checkTerm(it, first.type) }), C.Value.List(lazyOf(first.type)))
        }
        is S.Term.CompoundOf -> {
            val elements = term.elements.map { "" to inferTerm(it) }
            Typing(C.Term.CompoundOf(elements.map { it.second.element }), C.Value.Compound(elements.map { it.first to quote(it.second.type) }))
        }
        is S.Term.ReferenceOf -> {
            val element = inferTerm(term.element)
            Typing(C.Term.ReferenceOf(element.element), C.Value.Reference(lazyOf(element.type)))
        }
        is S.Term.FunctionOf -> {
            val types = term.parameters.map { fresh() }
            val body = Context((term.parameters zip types).map { Entry(it.first, END, ANY, it.second, stage) }.toMutableList(), meta, stage).inferTerm(term.body)
            val parameters = (term.parameters zip types).map { C.Parameter(it.first, quote(END), quote(ANY), quote(it.second)) }
            Typing(C.Term.FunctionOf(term.parameters, body.element), C.Value.Function(parameters, quote(body.type)))
        }
        is S.Term.Apply -> {
            val function = inferTerm(term.function)
            when (val type = force(function.type)) {
                is C.Value.Function -> withEnvironment { environment ->
                    val arguments = (term.arguments zip type.parameters).map { (argument, parameter) ->
                        checkTerm(argument, environment.evaluate(parameter.type)).also {
                            val id = argument.id
                            val argument = environment.evaluate(it)
                            environment.evaluate(parameter.lower).let { lower ->
                                if (!entries.size.subtype(lower, argument)) diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(argument)), serializeTerm(quote(lower)), id))
                            }
                            environment.evaluate(parameter.upper).let { upper ->
                                if (!entries.size.subtype(argument, upper)) diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(upper)), serializeTerm(quote(argument)), id))
                            }
                            environment += lazyOf(argument)
                        }
                    }
                    val resultant = environment.evaluate(type.resultant)
                    Typing(C.Term.Apply(function.element, arguments), resultant)
                }
                else -> Typing(C.Term.Apply(function.element, term.arguments.map { checkTerm(it, ANY) }), diagnose(Diagnostic.FunctionExpected(term.function.id)))
            }
        }
        is S.Term.CodeOf -> {
            val element = up().inferTerm(term.element)
            Typing(C.Term.CodeOf(element.element), C.Value.Code(lazyOf(element.type)))
        }
        is S.Term.Splice -> {
            val element = down().inferTerm(term.element)
            val type = when (val type = force(element.type)) {
                is C.Value.Code -> type.element.value
                else -> diagnose(Diagnostic.CodeExpected(term.id))
            }
            Typing(C.Term.Splice(element.element), type)
        }
        is S.Term.Union -> {
            val variants = term.variants.map { checkTerm(it, C.Value.Type) }
            Typing(C.Term.Union(variants), C.Value.Type)
        }
        is S.Term.Intersection -> {
            val variants = term.variants.map { checkTerm(it, C.Value.Type) }
            Typing(C.Term.Intersection(variants), C.Value.Type)
        }
        is S.Term.Boolean -> Typing(C.Term.Boolean, C.Value.Type)
        is S.Term.Byte -> Typing(C.Term.Byte, C.Value.Type)
        is S.Term.Short -> Typing(C.Term.Short, C.Value.Type)
        is S.Term.Int -> Typing(C.Term.Int, C.Value.Type)
        is S.Term.Long -> Typing(C.Term.Long, C.Value.Type)
        is S.Term.Float -> Typing(C.Term.Float, C.Value.Type)
        is S.Term.Double -> Typing(C.Term.Double, C.Value.Type)
        is S.Term.String -> Typing(C.Term.String, C.Value.Type)
        is S.Term.ByteArray -> Typing(C.Term.ByteArray, C.Value.Type)
        is S.Term.IntArray -> Typing(C.Term.IntArray, C.Value.Type)
        is S.Term.LongArray -> Typing(C.Term.LongArray, C.Value.Type)
        is S.Term.List -> {
            val element = checkTerm(term.element, C.Value.Type)
            Typing(element, C.Value.Type)
        }
        is S.Term.Compound -> {
            val elements = withContext(meta) { environment, context ->
                term.elements.map { (name, element) ->
                    (name to context.checkTerm(element, C.Value.Type)).also { (name, element) ->
                        context.bind(term.id, Entry(name, END, ANY, environment.evaluate(element), stage))
                        environment += lazyOf(C.Value.Variable(name, environment.size))
                    }
                }
            }
            Typing(C.Term.Compound(elements), C.Value.Type)
        }
        is S.Term.Reference -> {
            val element = checkTerm(term.element, C.Value.Type)
            Typing(element, C.Value.Type)
        }
        is S.Term.Function -> Typing(
            withContext(meta) { environment, context ->
                val parameters = term.parameters.map { (name, lower, upper, parameter) ->
                    val parameter = context.checkTerm(parameter, C.Value.Type)
                    val type = environment.evaluate(parameter)
                    val lower = context.checkTerm(lower, type)
                    val upper = context.checkTerm(upper, type)
                    C.Parameter(name, lower, upper, parameter).also {
                        context.bind(term.id, Entry(name, environment.evaluate(lower), environment.evaluate(upper), type, stage))
                        environment += lazyOf(C.Value.Variable(name, environment.size))
                    }
                }
                val resultant = context.checkTerm(term.resultant, C.Value.Type)
                C.Term.Function(parameters, resultant)
            },
            C.Value.Type
        )
        is S.Term.Code -> Typing(C.Term.Code(checkTerm(term.element, C.Value.Type)), C.Value.Type)
        is S.Term.Type -> Typing(C.Term.Type, C.Value.Type)
    }.also { types[term.id] = lazy { serializeTerm(quote(it.type)) } }

    /**
     * Checks the [term] against the [type] under this context.
     */
    private fun Context.checkTerm(term: S.Term, type: C.Value): C.Term {
        val type = force(type)
        types[term.id] = lazy { serializeTerm(quote(type)) }
        return when {
            term is S.Term.Hole -> {
                diagnose(Diagnostic.TermExpected(serializeTerm(quote(type)), term.id))
                C.Term.Hole
            }
            term is S.Term.Let -> {
                val init = inferTerm(term.init)
                val body = bind(term.id, Entry(term.name, END, ANY, init.type, stage)).checkTerm(term.body, type)
                C.Term.Let(term.name, init.element, body)
            }
            term is S.Term.Match -> {
                val scrutinee = inferTerm(term.scrutinee)
                val clauses = term.clauses.map { checkPattern(it.first, scrutinee.type) to checkTerm(it.second, type) }
                C.Term.Match(scrutinee.element, clauses)
            }
            term is S.Term.ListOf && type is C.Value.List -> {
                val elements = term.elements.map { checkTerm(it, type.element.value) }
                C.Term.ListOf(elements)
            }
            term is S.Term.CompoundOf && type is C.Value.Compound -> withEnvironment { environment ->
                val elements = (term.elements zip type.elements).map { (term, type) ->
                    checkTerm(term, environment.evaluate(type.second)).also {
                        environment += lazy { environment.evaluate(it) }
                    }
                }
                C.Term.CompoundOf(elements)
            }
            term is S.Term.ReferenceOf && type is C.Value.Reference -> {
                val element = checkTerm(term.element, type.element.value)
                C.Term.ReferenceOf(element)
            }
            term is S.Term.FunctionOf && type is C.Value.Function -> withContext(meta) { environment, context ->
                (term.parameters zip type.parameters).forEach { (name, parameter) ->
                    context.bind(term.id, Entry(name, environment.evaluate(parameter.lower), environment.evaluate(parameter.upper), environment.evaluate(parameter.type), stage))
                    environment += lazyOf(C.Value.Variable(name, environment.size))
                }
                val resultant = context.checkTerm(term.body, environment.evaluate(type.resultant))
                C.Term.FunctionOf(term.parameters, resultant)
            }
            term is S.Term.CodeOf && type is C.Value.Code -> {
                val element = up().checkTerm(term.element, type.element.value)
                C.Term.CodeOf(element)
            }
            term is S.Term.Union && term.variants.isEmpty() -> C.Term.Union(emptyList()) // generalized bottom type
            term is S.Term.Intersection && term.variants.isEmpty() -> C.Term.Intersection(emptyList()) // generalized top type
            else -> {
                val inferred = inferTerm(term)
                if (!entries.size.subtype(inferred.type, type)) {
                    types[term.id] = lazy { serializeTerm(quote(END)) }
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(type)), serializeTerm(quote(inferred.type)), term.id))
                }
                inferred.element
            }
        }
    }

    /**
     * Infers the type of the [pattern] under this context.
     */
    private fun Context.inferPattern(pattern: S.Pattern): Typing<C.Pattern> = when (pattern) {
        is S.Pattern.Variable -> {
            val type = fresh()
            bind(pattern.id, Entry(pattern.name, END, ANY, type, stage))
            Typing(C.Pattern.Variable(pattern.name), type)
        }
        is S.Pattern.BooleanOf -> Typing(C.Pattern.BooleanOf(pattern.value), C.Value.Boolean)
        is S.Pattern.ByteOf -> Typing(C.Pattern.ByteOf(pattern.value), C.Value.Byte)
        is S.Pattern.ShortOf -> Typing(C.Pattern.ShortOf(pattern.value), C.Value.Short)
        is S.Pattern.IntOf -> Typing(C.Pattern.IntOf(pattern.value), C.Value.Int)
        is S.Pattern.LongOf -> Typing(C.Pattern.LongOf(pattern.value), C.Value.Long)
        is S.Pattern.FloatOf -> Typing(C.Pattern.FloatOf(pattern.value), C.Value.Float)
        is S.Pattern.DoubleOf -> Typing(C.Pattern.DoubleOf(pattern.value), C.Value.Double)
        is S.Pattern.StringOf -> Typing(C.Pattern.StringOf(pattern.value), C.Value.String)
        is S.Pattern.ByteArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, C.Value.Byte) }
            Typing(C.Pattern.ByteArrayOf(elements), C.Value.ByteArray)
        }
        is S.Pattern.IntArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, C.Value.Int) }
            Typing(C.Pattern.IntArrayOf(elements), C.Value.IntArray)
        }
        is S.Pattern.LongArrayOf -> {
            val elements = pattern.elements.map { checkPattern(it, C.Value.Long) }
            Typing(C.Pattern.LongArrayOf(elements), C.Value.LongArray)
        }
        is S.Pattern.ListOf -> if (pattern.elements.isEmpty()) Typing(C.Pattern.ListOf(emptyList()), END) else { // TODO: use union of element types
            val first = inferPattern(pattern.elements.first())
            val elements = pattern.elements.drop(1).map { checkPattern(it, first.type) }
            Typing(C.Pattern.ListOf(elements), C.Value.List(lazyOf(first.type)))
        }
        is S.Pattern.CompoundOf -> {
            val elements = pattern.elements.map { "" to inferPattern(it) }
            Typing(C.Pattern.CompoundOf(elements.map { it.second.element }), C.Value.Compound(elements.map { it.first to quote(it.second.type) }))
        }
        is S.Pattern.ReferenceOf -> {
            val element = inferPattern(pattern.element)
            Typing(C.Pattern.ReferenceOf(element.element), C.Value.Reference(lazyOf(element.type)))
        }
    }.also { types[pattern.id] = lazy { serializeTerm(quote(it.type)) } }

    /**
     * Checks the [pattern] against the [type] under this context.
     */
    private fun Context.checkPattern(pattern: S.Pattern, type: C.Value): C.Pattern {
        val type = force(type)
        types[pattern.id] = lazy { serializeTerm(quote(type)) }
        return when {
            pattern is S.Pattern.Variable -> {
                bind(pattern.id, Entry(pattern.name, END, ANY, type, stage))
                C.Pattern.Variable(pattern.name)
            }
            pattern is S.Pattern.ListOf && type is C.Value.List -> {
                val elements = pattern.elements.map { checkPattern(it, type.element.value) }
                C.Pattern.ListOf(elements)
            }
            pattern is S.Pattern.CompoundOf && type is C.Value.Compound -> {
                val elements = (pattern.elements zip type.elements).map { checkPattern(it.first, emptyEnvironment().evaluate(it.second.second)) }
                C.Pattern.CompoundOf(elements)
            }
            pattern is S.Pattern.ReferenceOf && type is C.Value.Reference -> {
                val element = checkPattern(pattern.element, type.element.value)
                C.Pattern.ReferenceOf(element)
            }
            else -> {
                val inferred = inferPattern(pattern)
                if (!0.subtype(inferred.type, type)) {
                    types[pattern.id] = lazy { serializeTerm(quote(END)) }
                    diagnose(Diagnostic.TypeMismatch(serializeTerm(quote(type)), serializeTerm(quote(inferred.type)), pattern.id))
                }
                inferred.element
            }
        }
    }

    /**
     * Evaluates the [term] to a value under this environment.
     */
    private fun Environment.evaluate(term: C.Term): C.Value = when (term) {
        is C.Term.Hole -> C.Value.Hole
        is C.Term.Meta -> metas[term.index] ?: C.Value.Meta(term.index)
        is C.Term.Variable -> this[term.level].value
        is C.Term.Definition -> C.Value.Definition(term.name)
        is C.Term.Let -> (this + lazyOf(evaluate(term.init))).evaluate(term.body)
        is C.Term.Match -> C.Value.Match(evaluate(term.scrutinee), term.clauses.map { it.first to lazy { evaluate(it.second) /* TODO: collect variables */ } })
        is C.Term.BooleanOf -> C.Value.BooleanOf(term.value)
        is C.Term.ByteOf -> C.Value.ByteOf(term.value)
        is C.Term.ShortOf -> C.Value.ShortOf(term.value)
        is C.Term.IntOf -> C.Value.IntOf(term.value)
        is C.Term.LongOf -> C.Value.LongOf(term.value)
        is C.Term.FloatOf -> C.Value.FloatOf(term.value)
        is C.Term.DoubleOf -> C.Value.DoubleOf(term.value)
        is C.Term.StringOf -> C.Value.StringOf(term.value)
        is C.Term.ByteArrayOf -> C.Value.ByteArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.IntArrayOf -> C.Value.IntArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.LongArrayOf -> C.Value.LongArrayOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.ListOf -> C.Value.ListOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.CompoundOf -> C.Value.CompoundOf(term.elements.map { lazy { evaluate(it) } })
        is C.Term.ReferenceOf -> C.Value.ReferenceOf(lazy { evaluate(term.element) })
        is C.Term.FunctionOf -> C.Value.FunctionOf(term.parameters, term.body)
        is C.Term.Apply -> when (val function = evaluate(term.function)) {
            is C.Value.FunctionOf -> term.arguments.map { lazy { evaluate(it) } }.evaluate(function.body)
            else -> C.Value.Apply(function, term.arguments.map { lazy { evaluate(it) } })
        }
        is C.Term.CodeOf -> C.Value.CodeOf(lazy { evaluate(term.element) })
        is C.Term.Splice -> when (val element = evaluate(term.element)) {
            is C.Value.CodeOf -> element.element.value
            else -> C.Value.Splice(lazyOf(element))
        }
        is C.Term.Union -> C.Value.Union(term.variants.map { lazy { evaluate(it) } })
        is C.Term.Intersection -> C.Value.Intersection(term.variants.map { lazy { evaluate(it) } })
        is C.Term.Boolean -> C.Value.Boolean
        is C.Term.Byte -> C.Value.Byte
        is C.Term.Short -> C.Value.Short
        is C.Term.Int -> C.Value.Int
        is C.Term.Long -> C.Value.Long
        is C.Term.Float -> C.Value.Float
        is C.Term.Double -> C.Value.Double
        is C.Term.String -> C.Value.String
        is C.Term.ByteArray -> C.Value.ByteArray
        is C.Term.IntArray -> C.Value.IntArray
        is C.Term.LongArray -> C.Value.LongArray
        is C.Term.List -> C.Value.List(lazy { evaluate(term.element) })
        is C.Term.Compound -> C.Value.Compound(term.elements)
        is C.Term.Reference -> C.Value.Reference(lazy { evaluate(term.element) })
        is C.Term.Function -> C.Value.Function(term.parameters, term.resultant)
        is C.Term.Code -> C.Value.Code(lazy { evaluate(term.element) })
        is C.Term.Type -> C.Value.Type
    }

    /**
     * Quotes the [value] to a term.
     */
    private fun quote(value: C.Value): C.Term = when (val value = force(value)) {
        is C.Value.Hole -> C.Term.Hole
        is C.Value.Meta -> metas[value.index]?.let { quote(it) } ?: C.Term.Meta(value.index)
        is C.Value.Variable -> C.Term.Variable(value.name, value.level)
        is C.Value.Definition -> C.Term.Definition(value.name)
        is C.Value.Match -> C.Term.Match(quote(value.scrutinee), value.clauses.map { it.first to quote(it.second.value) })
        is C.Value.BooleanOf -> C.Term.BooleanOf(value.value)
        is C.Value.ByteOf -> C.Term.ByteOf(value.value)
        is C.Value.ShortOf -> C.Term.ShortOf(value.value)
        is C.Value.IntOf -> C.Term.IntOf(value.value)
        is C.Value.LongOf -> C.Term.LongOf(value.value)
        is C.Value.FloatOf -> C.Term.FloatOf(value.value)
        is C.Value.DoubleOf -> C.Term.DoubleOf(value.value)
        is C.Value.StringOf -> C.Term.StringOf(value.value)
        is C.Value.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quote(it.value) })
        is C.Value.IntArrayOf -> C.Term.IntArrayOf(value.elements.map { quote(it.value) })
        is C.Value.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quote(it.value) })
        is C.Value.ListOf -> C.Term.ListOf(value.elements.map { quote(it.value) })
        is C.Value.CompoundOf -> C.Term.CompoundOf(value.elements.map { quote(it.value) })
        is C.Value.ReferenceOf -> C.Term.ReferenceOf(quote(value.element.value))
        is C.Value.FunctionOf -> C.Term.FunctionOf(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, index)) }
                    .evaluate(value.body)
            )
        )
        is C.Value.Apply -> C.Term.Apply(quote(value.function), value.arguments.map { quote(it.value) })
        is C.Value.CodeOf -> C.Term.CodeOf(quote(value.element.value))
        is C.Value.Splice -> C.Term.Splice(quote(value.element.value))
        is C.Value.Union -> C.Term.Union(value.variants.map { quote(it.value) })
        is C.Value.Intersection -> C.Term.Intersection(value.variants.map { quote(it.value) })
        is C.Value.Boolean -> C.Term.Boolean
        is C.Value.Byte -> C.Term.Byte
        is C.Value.Short -> C.Term.Short
        is C.Value.Int -> C.Term.Int
        is C.Value.Long -> C.Term.Long
        is C.Value.Float -> C.Term.Float
        is C.Value.Double -> C.Term.Double
        is C.Value.String -> C.Term.String
        is C.Value.ByteArray -> C.Term.ByteArray
        is C.Value.IntArray -> C.Term.IntArray
        is C.Value.LongArray -> C.Term.LongArray
        is C.Value.List -> C.Term.List(quote(value.element.value))
        is C.Value.Compound -> C.Term.Compound(value.elements)
        is C.Value.Reference -> C.Term.Reference(quote(value.element.value))
        is C.Value.Function -> C.Term.Function(
            value.parameters,
            quote(
                value.parameters
                    .mapIndexed { index, (name, _) -> lazyOf(C.Value.Variable(name, index)) }
                    .evaluate(value.resultant)
            )
        )
        is C.Value.Code -> C.Term.Code(quote(value.element.value))
        is C.Value.Type -> C.Term.Type
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
        return END
    }

    data class Output(
        val item: C.Item,
        val diagnostics: List<Diagnostic>,
        val types: Map<Id, Lazy<S.Term>>
    )

    data class Typing<out E>(
        val element: E,
        val type: C.Value
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
        private val END: C.Value.Union = C.Value.Union(emptyList())
        private val ANY: C.Value.Intersection = C.Value.Intersection(emptyList())

        operator fun invoke(items: Map<String, C.Item>, item: S.Item): Output = Elaborate(items).run {
            Output(elaborateItem(item), diagnostics, types)
        }
    }
}
