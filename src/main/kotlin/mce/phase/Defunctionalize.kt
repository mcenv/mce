package mce.phase

import mce.graph.Id
import java.util.concurrent.atomic.AtomicInteger
import mce.graph.Core as C
import mce.graph.Defunctionalized as D

@Suppress("NAME_SHADOWING")
class Defunctionalize private constructor() {
    private val functions: MutableMap<Int, D.Term> = mutableMapOf()

    private fun defunctionalizeItem(item: C.Item): D.Item = when (item) {
        is C.Item.Def -> {
            val parameters = item.parameters.map { defunctionalizeParameter(it) }
            val body = defunctionalizeTerm(item.body)
            D.Item.Def(item.imports, item.exports, item.name, parameters, body)
        }
        is C.Item.Mod -> {
            val body = defunctionalizeModule(item.body)
            D.Item.Mod(item.imports, item.exports, item.name, body)
        }
    }

    private fun defunctionalizeParameter(parameter: C.Parameter): D.Parameter {
        val lower = parameter.lower?.let { defunctionalizeTerm(it) }
        val upper = parameter.upper?.let { defunctionalizeTerm(it) }
        val type = defunctionalizeTerm(parameter.type)
        return D.Parameter(parameter.relevant, parameter.name, lower, upper, type)
    }

    private fun defunctionalizeModule(module: C.Module): D.Module = when (module) {
        is C.Module.Var -> D.Module.Var(module.name, module.id)
        is C.Module.Str -> {
            val items = module.items.map { defunctionalizeItem(it) }
            D.Module.Str(items, module.id!!)
        }
        is C.Module.Sig -> {
            val signatures = module.signatures.map { defunctionalizeSignature(it) }
            D.Module.Sig(signatures, module.id!!)
        }
        is C.Module.Type -> D.Module.Type(module.id)
    }

    private fun defunctionalizeSignature(signature: C.Signature): D.Signature = when (signature) {
        is C.Signature.Def -> {
            val parameters = signature.parameters.map { defunctionalizeParameter(it) }
            val resultant = defunctionalizeTerm(signature.resultant)
            D.Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is C.Signature.Mod -> {
            val type = defunctionalizeModule(signature.type)
            D.Signature.Mod(signature.name, type, signature.id)
        }
    }

    private fun defunctionalizeTerm(term: C.Term): D.Term = when (term) {
        is C.Term.Hole -> throw Error()
        is C.Term.Meta -> throw Error()
        is C.Term.Var -> D.Term.Var(term.name, term.level, term.id!!)
        is C.Term.Def -> D.Term.Def(term.name, term.arguments.map { defunctionalizeTerm(it) }, term.id!!)
        is C.Term.Let -> {
            val init = defunctionalizeTerm(term.init)
            val body = defunctionalizeTerm(term.body)
            D.Term.Let(term.name, init, body, term.id!!)
        }
        is C.Term.Match -> {
            val scrutinee = defunctionalizeTerm(term.scrutinee)
            val clauses = term.clauses.map { defunctionalizePattern(it.first) to defunctionalizeTerm(it.second) }
            D.Term.Match(scrutinee, clauses, term.id!!)
        }
        is C.Term.BoolOf -> D.Term.BoolOf(term.value, term.id!!)
        is C.Term.ByteOf -> D.Term.ByteOf(term.value, term.id!!)
        is C.Term.ShortOf -> D.Term.ShortOf(term.value, term.id!!)
        is C.Term.IntOf -> D.Term.IntOf(term.value, term.id!!)
        is C.Term.LongOf -> D.Term.LongOf(term.value, term.id!!)
        is C.Term.FloatOf -> D.Term.FloatOf(term.value, term.id!!)
        is C.Term.DoubleOf -> D.Term.DoubleOf(term.value, term.id!!)
        is C.Term.StringOf -> D.Term.StringOf(term.value, term.id!!)
        is C.Term.ByteArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.ByteArrayOf(elements, term.id!!)
        }
        is C.Term.IntArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.IntArrayOf(elements, term.id!!)
        }
        is C.Term.LongArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.LongArrayOf(elements, term.id!!)
        }
        is C.Term.ListOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.ListOf(elements, term.id!!)
        }
        is C.Term.CompoundOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            D.Term.CompoundOf(elements, term.id!!)
        }
        is C.Term.BoxOf -> {
            val content = defunctionalizeTerm(term.content)
            val tag = defunctionalizeTerm(term.tag)
            D.Term.BoxOf(content, tag, term.id!!)
        }
        is C.Term.RefOf -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.RefOf(element, term.id!!)
        }
        is C.Term.Refl -> D.Term.Refl(term.id!!)
        is C.Term.FunOf -> {
            val tag = freshTag()
            D.Term.FunOf(tag, term.id!!).also {
                val body = defunctionalizeTerm(term.body)
                functions[tag] = body
            }
        }
        is C.Term.Apply -> {
            val function = defunctionalizeTerm(term.function)
            val arguments = term.arguments.map { defunctionalizeTerm(it) }
            D.Term.Apply(function, arguments, term.id!!)
        }
        is C.Term.CodeOf -> throw Error()
        is C.Term.Splice -> throw Error()
        is C.Term.Union -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            D.Term.Union(variants, term.id!!)
        }
        is C.Term.Intersection -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            D.Term.Intersection(variants, term.id!!)
        }
        is C.Term.Bool -> D.Term.Bool(term.id!!)
        is C.Term.Byte -> D.Term.Byte(term.id!!)
        is C.Term.Short -> D.Term.Short(term.id!!)
        is C.Term.Int -> D.Term.Int(term.id!!)
        is C.Term.Long -> D.Term.Long(term.id!!)
        is C.Term.Float -> D.Term.Float(term.id!!)
        is C.Term.Double -> D.Term.Double(term.id!!)
        is C.Term.String -> D.Term.String(term.id!!)
        is C.Term.ByteArray -> D.Term.ByteArray(term.id!!)
        is C.Term.IntArray -> D.Term.IntArray(term.id!!)
        is C.Term.LongArray -> D.Term.LongArray(term.id!!)
        is C.Term.List -> {
            val element = defunctionalizeTerm(term.element)
            val size = defunctionalizeTerm(term.size)
            D.Term.List(element, size, term.id!!)
        }
        is C.Term.Compound -> {
            val elements = term.elements.map { it.first to defunctionalizeTerm(it.second) }
            D.Term.Compound(elements, term.id!!)
        }
        is C.Term.Box -> {
            val content = defunctionalizeTerm(term.content)
            D.Term.Box(content, term.id!!)
        }
        is C.Term.Ref -> {
            val element = defunctionalizeTerm(term.element)
            D.Term.Ref(element, term.id!!)
        }
        is C.Term.Eq -> {
            val left = defunctionalizeTerm(term.left)
            val right = defunctionalizeTerm(term.right)
            D.Term.Eq(left, right, term.id!!)
        }
        is C.Term.Fun -> {
            val parameters = term.parameters.map { defunctionalizeParameter(it) }
            val resultant = defunctionalizeTerm(term.resultant)
            val effects = term.effects.map { defunctionalizeEffect(it) }.toSet()
            D.Term.Fun(parameters, resultant, effects, term.id!!)
        }
        is C.Term.Code -> throw Error()
        is C.Term.Type -> D.Term.Type(term.id!!)
    }

    private fun defunctionalizePattern(pattern: C.Pattern): D.Pattern = when (pattern) {
        is C.Pattern.Var -> D.Pattern.Var(pattern.name, pattern.id)
        is C.Pattern.BoolOf -> D.Pattern.BoolOf(pattern.value, pattern.id)
        is C.Pattern.ByteOf -> D.Pattern.ByteOf(pattern.value, pattern.id)
        is C.Pattern.ShortOf -> D.Pattern.ShortOf(pattern.value, pattern.id)
        is C.Pattern.IntOf -> D.Pattern.IntOf(pattern.value, pattern.id)
        is C.Pattern.LongOf -> D.Pattern.LongOf(pattern.value, pattern.id)
        is C.Pattern.FloatOf -> D.Pattern.FloatOf(pattern.value, pattern.id)
        is C.Pattern.DoubleOf -> D.Pattern.DoubleOf(pattern.value, pattern.id)
        is C.Pattern.StringOf -> D.Pattern.StringOf(pattern.value, pattern.id)
        is C.Pattern.ByteArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.ByteArrayOf(elements, pattern.id)
        }
        is C.Pattern.IntArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.IntArrayOf(elements, pattern.id)
        }
        is C.Pattern.LongArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.LongArrayOf(elements, pattern.id)
        }
        is C.Pattern.ListOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.ListOf(elements, pattern.id)
        }
        is C.Pattern.CompoundOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            D.Pattern.CompoundOf(elements, pattern.id)
        }
        is C.Pattern.BoxOf -> {
            val content = defunctionalizePattern(pattern.content)
            val tag = defunctionalizePattern(pattern.tag)
            D.Pattern.BoxOf(content, tag, pattern.id)
        }
        is C.Pattern.RefOf -> {
            val element = defunctionalizePattern(pattern.element)
            D.Pattern.RefOf(element, pattern.id)
        }
        is C.Pattern.Refl -> D.Pattern.Refl(pattern.id)
        is C.Pattern.Bool -> D.Pattern.Bool(pattern.id)
        is C.Pattern.Byte -> D.Pattern.Byte(pattern.id)
        is C.Pattern.Short -> D.Pattern.Short(pattern.id)
        is C.Pattern.Int -> D.Pattern.Int(pattern.id)
        is C.Pattern.Long -> D.Pattern.Long(pattern.id)
        is C.Pattern.Float -> D.Pattern.Float(pattern.id)
        is C.Pattern.Double -> D.Pattern.Double(pattern.id)
        is C.Pattern.String -> D.Pattern.String(pattern.id)
        is C.Pattern.ByteArray -> D.Pattern.ByteArray(pattern.id)
        is C.Pattern.IntArray -> D.Pattern.IntArray(pattern.id)
        is C.Pattern.LongArray -> D.Pattern.LongArray(pattern.id)
    }

    private fun defunctionalizeEffect(effect: C.Effect): D.Effect = when (effect) {
        is C.Effect.Name -> D.Effect.Name(effect.name)
    }

    data class Result(
        val item: D.Item,
        val types: Map<Id, C.Value>,
        val functions: Map<Int, D.Term>
    )

    companion object {
        private val tag: AtomicInteger = AtomicInteger(0)

        private fun freshTag(): Int = tag.getAndIncrement()

        operator fun invoke(input: Stage.Result): Result = Defunctionalize().run {
            Result(defunctionalizeItem(input.item), input.types, functions)
        }
    }
}
