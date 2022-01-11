package mce.phase

import mce.Diagnostic
import mce.graph.Id
import mce.graph.freshId
import mce.graph.Core as C
import mce.graph.Surface as S

private typealias Environment = List<Lazy<C.Value>>

private typealias Level = Int

private typealias Stage = Int

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

    private fun Context.checkTerm(term: S.Term, type: C.Value) {
        val forced = force(type)
        types[term.id] = lazy { quote(forced) }
        when {
            term is S.Term.Hole -> diagnose(Diagnostic.TermExpected(quote(forced), term.id))
            term is S.Term.Let -> bind(term.id, Entry(term.name, end, any, inferTerm(term.init), stage)).checkTerm(term.body, type)
            term is S.Term.ListOf && forced is C.Value.List -> term.elements.forEach { checkTerm(it, forced.element.value) }
            term is S.Term.CompoundOf && forced is C.Value.Compound -> withEnvironment { environment ->
                (term.elements zip forced.elements).forEach {
                    checkTerm(it.first, environment.evaluate(it.second.second))
                    environment += lazy { environment.evaluate(it.first) }
                }
            }
            term is S.Term.FunctionOf && forced is C.Value.Function -> withContext(meta) { environment, context ->
                (term.parameters zip forced.parameters).forEach { (name, parameter) ->
                    context.bind(term.id, Entry(name, environment.evaluate(parameter.lower), environment.evaluate(parameter.upper), environment.evaluate(parameter.type), stage))
                    environment += lazyOf(C.Value.Variable(name, environment.size))
                }
                context.checkTerm(term.body, environment.evaluate(forced.resultant))
            }
            term is S.Term.CodeOf && forced is C.Value.Code -> up().checkTerm(term.element, forced.element.value)
            // generalized bottom type
            term is S.Term.Union && term.variants.isEmpty() -> {}
            // generalized top type
            term is S.Term.Intersection && term.variants.isEmpty() -> {}
            else -> {
                val inferred = inferTerm(term)
                if (!entries.size.subtype(inferred, forced)) {
                    types[term.id] = lazy { quote(end) }
                    diagnose(Diagnostic.TypeMismatch(quote(forced), quote(inferred), term.id))
                }
            }
        }
    }

    private fun Environment.evaluate(term: S.Term): C.Value = when (term) {
        is S.Term.Hole -> C.Value.Hole
        is S.Term.Meta -> metas[term.index] ?: C.Value.Meta(term.index)
        is S.Term.Variable -> this[term.level].value
        is S.Term.Definition -> C.Value.Definition(term.name)
        is S.Term.Let -> (this + lazyOf(evaluate(term.init))).evaluate(term.body)
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
        is S.Term.Function -> C.Value.Function(term.parameters, term.resultant)
        is S.Term.Code -> C.Value.Code(lazy { evaluate(term.element) })
        is S.Term.Type -> C.Value.Type
    }

    private fun quote(value: C.Value): S.Term = when (val forced = force(value)) {
        is C.Value.Hole -> S.Term.Hole(freshId())
        is C.Value.Meta -> metas[forced.index]?.let { quote(it) } ?: S.Term.Meta(forced.index, freshId())
        is C.Value.Variable -> S.Term.Variable(forced.name, forced.level, freshId())
        is C.Value.Definition -> S.Term.Definition(forced.name, freshId())
        is C.Value.BooleanOf -> S.Term.BooleanOf(forced.value, freshId())
        is C.Value.ByteOf -> S.Term.ByteOf(forced.value, freshId())
        is C.Value.ShortOf -> S.Term.ShortOf(forced.value, freshId())
        is C.Value.IntOf -> S.Term.IntOf(forced.value, freshId())
        is C.Value.LongOf -> S.Term.LongOf(forced.value, freshId())
        is C.Value.FloatOf -> S.Term.FloatOf(forced.value, freshId())
        is C.Value.DoubleOf -> S.Term.DoubleOf(forced.value, freshId())
        is C.Value.StringOf -> S.Term.StringOf(forced.value, freshId())
        is C.Value.ByteArrayOf -> S.Term.ByteArrayOf(forced.elements.map { quote(it.value) }, freshId())
        is C.Value.IntArrayOf -> S.Term.IntArrayOf(forced.elements.map { quote(it.value) }, freshId())
        is C.Value.LongArrayOf -> S.Term.LongArrayOf(forced.elements.map { quote(it.value) }, freshId())
        is C.Value.ListOf -> S.Term.ListOf(forced.elements.map { quote(it.value) }, freshId())
        is C.Value.CompoundOf -> S.Term.CompoundOf(forced.elements.map { quote(it.value) }, freshId())
        is C.Value.FunctionOf -> S.Term.FunctionOf(
            forced.parameters,
            quote(
                forced.parameters
                    .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, index)) }
                    .evaluate(forced.body)
            ),
            freshId()
        )
        is C.Value.Apply -> S.Term.Apply(quote(forced.function), forced.arguments.map { quote(it.value) }, freshId())
        is C.Value.CodeOf -> S.Term.CodeOf(quote(forced.element.value), freshId())
        is C.Value.Splice -> S.Term.Splice(quote(forced.element.value), freshId())
        is C.Value.Union -> S.Term.Union(forced.variants.map { quote(it.value) }, freshId())
        is C.Value.Intersection -> S.Term.Intersection(forced.variants.map { quote(it.value) }, freshId())
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
        is C.Value.List -> S.Term.List(quote(forced.element.value), freshId())
        is C.Value.Compound -> S.Term.Compound(forced.elements, freshId())
        is C.Value.Function -> S.Term.Function(
            forced.parameters,
            quote(
                forced.parameters
                    .mapIndexed { index, (name, _) -> lazyOf(C.Value.Variable(name, index)) }
                    .evaluate(forced.resultant)
            ),
            freshId()
        )
        is C.Value.Code -> S.Term.Code(quote(forced.element.value), freshId())
        is C.Value.Type -> S.Term.Type(freshId())
    }

    private fun Level.unify(value1: C.Value, value2: C.Value): Boolean {
        val forced1 = force(value1)
        val forced2 = force(value2)
        return when {
            forced1 is C.Value.Meta -> when (val solved1 = metas[forced1.index]) {
                null -> {
                    metas[forced1.index] = forced2
                    true
                }
                else -> unify(solved1, forced2)
            }
            forced2 is C.Value.Meta -> unify(forced2, forced1)
            forced1 is C.Value.Variable && forced2 is C.Value.Variable -> forced1.level == forced2.level
            forced1 is C.Value.Definition && forced2 is C.Value.Definition -> forced1.name == forced2.name
            forced1 is C.Value.BooleanOf && forced2 is C.Value.BooleanOf -> forced1.value == forced2.value
            forced1 is C.Value.ByteOf && forced2 is C.Value.ByteOf -> forced1.value == forced2.value
            forced1 is C.Value.ShortOf && forced2 is C.Value.ShortOf -> forced1.value == forced2.value
            forced1 is C.Value.IntOf && forced2 is C.Value.IntOf -> forced1.value == forced2.value
            forced1 is C.Value.LongOf && forced2 is C.Value.LongOf -> forced1.value == forced2.value
            forced1 is C.Value.FloatOf && forced2 is C.Value.FloatOf -> forced1.value == forced2.value
            forced1 is C.Value.DoubleOf && forced2 is C.Value.DoubleOf -> forced1.value == forced2.value
            forced1 is C.Value.StringOf && forced2 is C.Value.StringOf -> forced1.value == forced2.value
            forced1 is C.Value.ByteArrayOf && forced2 is C.Value.ByteArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.IntArrayOf && forced2 is C.Value.IntArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.LongArrayOf && forced2 is C.Value.LongArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.ListOf && forced2 is C.Value.ListOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.CompoundOf && forced2 is C.Value.CompoundOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.FunctionOf && forced2 is C.Value.FunctionOf -> forced1.parameters.size == forced2.parameters.size &&
                    forced1.parameters
                        .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, this + index)) }
                        .let { (this + forced1.parameters.size).unify(it.evaluate(forced1.body), it.evaluate(forced2.body)) }
            forced1 is C.Value.Apply && forced2 is C.Value.Apply -> unify(forced1.function, forced2.function) &&
                    (forced1.arguments zip forced2.arguments).all { unify(it.first.value, it.second.value) }
            forced1 is C.Value.CodeOf && forced2 is C.Value.CodeOf -> unify(forced1.element.value, forced2.element.value)
            forced1 is C.Value.Splice && forced2 is C.Value.Splice -> unify(forced1.element.value, forced2.element.value)
            // TODO: unify non-empty unions and intersections?
            forced1 is C.Value.Union && forced1.variants.isEmpty() && forced2 is C.Value.Union && forced2.variants.isEmpty() -> true
            forced1 is C.Value.Intersection && forced1.variants.isEmpty() && forced2 is C.Value.Intersection && forced2.variants.isEmpty() -> true
            forced1 is C.Value.Boolean && forced2 is C.Value.Boolean -> true
            forced1 is C.Value.Byte && forced2 is C.Value.Byte -> true
            forced1 is C.Value.Short && forced2 is C.Value.Short -> true
            forced1 is C.Value.Int && forced2 is C.Value.Int -> true
            forced1 is C.Value.Long && forced2 is C.Value.Long -> true
            forced1 is C.Value.Float && forced2 is C.Value.Float -> true
            forced1 is C.Value.Double && forced2 is C.Value.Double -> true
            forced1 is C.Value.String && forced2 is C.Value.String -> true
            forced1 is C.Value.ByteArray && forced2 is C.Value.ByteArray -> true
            forced1 is C.Value.IntArray && forced2 is C.Value.IntArray -> true
            forced1 is C.Value.LongArray && forced2 is C.Value.LongArray -> true
            forced1 is C.Value.List && forced2 is C.Value.List -> unify(forced1.element.value, forced2.element.value)
            forced1 is C.Value.Compound && forced2 is C.Value.Compound -> forced1.elements.size == forced2.elements.size &&
                    withEnvironment { environment ->
                        (forced1.elements zip forced2.elements)
                            .withIndex()
                            .all { (index, elements) ->
                                val (elements1, elements2) = elements
                                (this + index).unify(environment.evaluate(elements1.second), environment.evaluate(elements2.second))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            }
                    }
            forced1 is C.Value.Function && forced2 is C.Value.Function -> forced1.parameters.size == forced2.parameters.size &&
                    withEnvironment { environment ->
                        (forced1.parameters zip forced2.parameters)
                            .withIndex()
                            .all { (index, parameter) ->
                                val (parameter1, parameter2) = parameter
                                (this + index).unify(environment.evaluate(parameter2.type), environment.evaluate(parameter1.type))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            } &&
                                (this + forced1.parameters.size).unify(environment.evaluate(forced1.resultant), environment.evaluate(forced2.resultant))
                    }
            forced1 is C.Value.Code && forced2 is C.Value.Code -> unify(forced1.element.value, forced2.element.value)
            forced1 is C.Value.Type && forced2 is C.Value.Type -> true
            else -> false
        }
    }

    private fun Level.subtype(value1: C.Value, value2: C.Value): Boolean {
        val forced1 = force(value1)
        val forced2 = force(value2)
        return when {
            forced1 is C.Value.ByteArrayOf && forced2 is C.Value.ByteArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { subtype(it.first.value, it.second.value) }
            forced1 is C.Value.IntArrayOf && forced2 is C.Value.IntArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { subtype(it.first.value, it.second.value) }
            forced1 is C.Value.LongArrayOf && forced2 is C.Value.LongArrayOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { subtype(it.first.value, it.second.value) }
            forced1 is C.Value.ListOf && forced2 is C.Value.ListOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { subtype(it.first.value, it.second.value) }
            forced1 is C.Value.CompoundOf && forced2 is C.Value.CompoundOf -> forced1.elements.size == forced2.elements.size &&
                    (forced1.elements zip forced2.elements).all { subtype(it.first.value, it.second.value) }
            forced1 is C.Value.FunctionOf && forced2 is C.Value.FunctionOf -> forced1.parameters.size == forced2.parameters.size &&
                    forced1.parameters
                        .mapIndexed { index, parameter -> lazyOf(C.Value.Variable(parameter, this + index)) }
                        .let { (this + forced1.parameters.size).subtype(it.evaluate(forced1.body), it.evaluate(forced2.body)) }
            forced1 is C.Value.Apply && forced2 is C.Value.Apply -> subtype(forced1.function, forced2.function) &&
                    (forced1.arguments zip forced2.arguments).all { unify(it.first.value, it.second.value) /* pointwise subtyping */ }
            forced1 is C.Value.Union -> forced1.variants.all { subtype(it.value, forced2) }
            forced2 is C.Value.Union -> forced2.variants.any { subtype(forced1, it.value) }
            forced1 is C.Value.Intersection -> forced1.variants.any { subtype(it.value, forced2) }
            forced2 is C.Value.Intersection -> forced2.variants.all { subtype(forced1, it.value) }
            forced1 is C.Value.List && forced2 is C.Value.List -> subtype(forced1.element.value, forced2.element.value)
            forced1 is C.Value.Compound && forced2 is C.Value.Compound -> forced1.elements.size == forced2.elements.size &&
                    withEnvironment { environment ->
                        (forced1.elements zip forced2.elements)
                            .withIndex()
                            .all { (index, elements) ->
                                val (elements1, elements2) = elements
                                (this + index).subtype(environment.evaluate(elements1.second), environment.evaluate(elements2.second))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            }
                    }
            forced1 is C.Value.Function && forced2 is C.Value.Function -> forced1.parameters.size == forced2.parameters.size &&
                    withEnvironment { environment ->
                        (forced1.parameters zip forced2.parameters)
                            .withIndex()
                            .all { (index, parameter) ->
                                val (parameter1, parameter2) = parameter
                                (this + index).subtype(environment.evaluate(parameter2.type), environment.evaluate(parameter1.type))
                                    .also { environment += lazyOf(C.Value.Variable("", this + index)) }
                            } &&
                                (this + forced1.parameters.size).subtype(environment.evaluate(forced1.resultant), environment.evaluate(forced2.resultant))
                    }
            forced1 is C.Value.Code && forced2 is C.Value.Code -> subtype(forced1.element.value, forced2.element.value)
            else -> unify(forced1, forced2)
        }
    }

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
