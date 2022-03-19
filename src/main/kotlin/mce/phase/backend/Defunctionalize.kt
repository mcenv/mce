package mce.phase.backend

import mce.ast.Id
import mce.util.toLinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import mce.ast.core.Effect as CEffect
import mce.ast.core.Item as CItem
import mce.ast.core.Module as CModule
import mce.ast.core.Parameter as CParameter
import mce.ast.core.Pattern as CPattern
import mce.ast.core.Signature as CSignature
import mce.ast.core.Term as CTerm
import mce.ast.core.VTerm as CVTerm
import mce.ast.defun.Effect as DEffect
import mce.ast.defun.Entry as DEntry
import mce.ast.defun.Item as DItem
import mce.ast.defun.Module as DModule
import mce.ast.defun.Parameter as DParameter
import mce.ast.defun.Pattern as DPattern
import mce.ast.defun.Signature as DSignature
import mce.ast.defun.Term as DTerm

@Suppress("NAME_SHADOWING")
class Defunctionalize private constructor() {
    private val functions: MutableMap<Int, DTerm> = mutableMapOf()

    private fun defunctionalizeItem(item: CItem): DItem = when (item) {
        is CItem.Def -> {
            val parameters = item.parameters.map { defunctionalizeParameter(it) }
            val body = defunctionalizeTerm(item.body)
            DItem.Def(item.imports, item.exports, item.name, parameters, body, item.id)
        }
        is CItem.Mod -> {
            val body = defunctionalizeModule(item.body)
            DItem.Mod(item.imports, item.exports, item.name, body, item.id)
        }
        is CItem.Test -> {
            val body = defunctionalizeTerm(item.body)
            DItem.Test(item.imports, item.exports, item.name, body, item.id)
        }
    }

    private fun defunctionalizeParameter(parameter: CParameter): DParameter {
        val lower = parameter.lower?.let { defunctionalizeTerm(it) }
        val upper = parameter.upper?.let { defunctionalizeTerm(it) }
        val type = defunctionalizeTerm(parameter.type)
        return DParameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
    }

    private fun defunctionalizeModule(module: CModule): DModule = when (module) {
        is CModule.Var -> DModule.Var(module.name, module.id)
        is CModule.Str -> {
            val items = module.items.map { defunctionalizeItem(it) }
            DModule.Str(items, module.id!!)
        }
        is CModule.Sig -> {
            val signatures = module.signatures.map { defunctionalizeSignature(it) }
            DModule.Sig(signatures, module.id!!)
        }
        is CModule.Type -> DModule.Type(module.id)
    }

    private fun defunctionalizeSignature(signature: CSignature): DSignature = when (signature) {
        is CSignature.Def -> {
            val parameters = signature.parameters.map { defunctionalizeParameter(it) }
            val resultant = defunctionalizeTerm(signature.resultant)
            DSignature.Def(signature.name, parameters, resultant, signature.id!!)
        }
        is CSignature.Mod -> {
            val type = defunctionalizeModule(signature.type)
            DSignature.Mod(signature.name, type, signature.id!!)
        }
        is CSignature.Test -> DSignature.Test(signature.name, signature.id!!)
    }

    private fun defunctionalizeTerm(term: CTerm): DTerm = when (term) {
        is CTerm.Hole -> throw Error()
        is CTerm.Meta -> throw Error()
        is CTerm.Var -> DTerm.Var(term.name, term.level, term.id!!)
        is CTerm.Def -> DTerm.Def(term.name, term.arguments.map { defunctionalizeTerm(it) }, term.id!!)
        is CTerm.Let -> {
            val init = defunctionalizeTerm(term.init)
            val body = defunctionalizeTerm(term.body)
            DTerm.Let(term.name, init, body, term.id!!)
        }
        is CTerm.Match -> {
            val scrutinee = defunctionalizeTerm(term.scrutinee)
            val clauses = term.clauses.map { defunctionalizePattern(it.first) to defunctionalizeTerm(it.second) }
            DTerm.Match(scrutinee, clauses, term.id!!)
        }
        is CTerm.UnitOf -> DTerm.UnitOf(term.id!!)
        is CTerm.BoolOf -> DTerm.BoolOf(term.value, term.id!!)
        is CTerm.ByteOf -> DTerm.ByteOf(term.value, term.id!!)
        is CTerm.ShortOf -> DTerm.ShortOf(term.value, term.id!!)
        is CTerm.IntOf -> DTerm.IntOf(term.value, term.id!!)
        is CTerm.LongOf -> DTerm.LongOf(term.value, term.id!!)
        is CTerm.FloatOf -> DTerm.FloatOf(term.value, term.id!!)
        is CTerm.DoubleOf -> DTerm.DoubleOf(term.value, term.id!!)
        is CTerm.StringOf -> DTerm.StringOf(term.value, term.id!!)
        is CTerm.ByteArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            DTerm.ByteArrayOf(elements, term.id!!)
        }
        is CTerm.IntArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            DTerm.IntArrayOf(elements, term.id!!)
        }
        is CTerm.LongArrayOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            DTerm.LongArrayOf(elements, term.id!!)
        }
        is CTerm.ListOf -> {
            val elements = term.elements.map { defunctionalizeTerm(it) }
            DTerm.ListOf(elements, term.id!!)
        }
        is CTerm.CompoundOf -> {
            val elements = term.elements.map { (name, element) -> name to defunctionalizeTerm(element) }
            DTerm.CompoundOf(elements.toLinkedHashMap(), term.id!!)
        }
        is CTerm.BoxOf -> {
            val content = defunctionalizeTerm(term.content)
            val tag = defunctionalizeTerm(term.tag)
            DTerm.BoxOf(content, tag, term.id!!)
        }
        is CTerm.RefOf -> {
            val element = defunctionalizeTerm(term.element)
            DTerm.RefOf(element, term.id!!)
        }
        is CTerm.Refl -> DTerm.Refl(term.id!!)
        is CTerm.FunOf -> {
            val tag = freshTag()
            DTerm.FunOf(tag, term.id!!).also {
                val body = defunctionalizeTerm(term.body)
                functions[tag] = body
            }
        }
        is CTerm.Apply -> {
            val function = defunctionalizeTerm(term.function)
            val arguments = term.arguments.map { defunctionalizeTerm(it) }
            DTerm.Apply(function, arguments, term.id!!)
        }
        is CTerm.CodeOf -> throw Error()
        is CTerm.Splice -> throw Error()
        is CTerm.Or -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            DTerm.Or(variants, term.id!!)
        }
        is CTerm.And -> {
            val variants = term.variants.map { defunctionalizeTerm(it) }
            DTerm.And(variants, term.id!!)
        }
        is CTerm.Unit -> DTerm.Unit(term.id!!)
        is CTerm.Bool -> DTerm.Bool(term.id!!)
        is CTerm.Byte -> DTerm.Byte(term.id!!)
        is CTerm.Short -> DTerm.Short(term.id!!)
        is CTerm.Int -> DTerm.Int(term.id!!)
        is CTerm.Long -> DTerm.Long(term.id!!)
        is CTerm.Float -> DTerm.Float(term.id!!)
        is CTerm.Double -> DTerm.Double(term.id!!)
        is CTerm.String -> DTerm.String(term.id!!)
        is CTerm.ByteArray -> DTerm.ByteArray(term.id!!)
        is CTerm.IntArray -> DTerm.IntArray(term.id!!)
        is CTerm.LongArray -> DTerm.LongArray(term.id!!)
        is CTerm.List -> {
            val element = defunctionalizeTerm(term.element)
            val size = defunctionalizeTerm(term.size)
            DTerm.List(element, size, term.id!!)
        }
        is CTerm.Compound -> {
            val elements = term.elements.map { (name, element) -> name to DEntry(element.relevant, defunctionalizeTerm(element.type), element.id!!) }
            DTerm.Compound(elements.toLinkedHashMap(), term.id!!)
        }
        is CTerm.Box -> {
            val content = defunctionalizeTerm(term.content)
            DTerm.Box(content, term.id!!)
        }
        is CTerm.Ref -> {
            val element = defunctionalizeTerm(term.element)
            DTerm.Ref(element, term.id!!)
        }
        is CTerm.Eq -> {
            val left = defunctionalizeTerm(term.left)
            val right = defunctionalizeTerm(term.right)
            DTerm.Eq(left, right, term.id!!)
        }
        is CTerm.Fun -> {
            val parameters = term.parameters.map { defunctionalizeParameter(it) }
            val resultant = defunctionalizeTerm(term.resultant)
            val effects = term.effects.map { defunctionalizeEffect(it) }.toSet()
            DTerm.Fun(parameters, resultant, effects, term.id!!)
        }
        is CTerm.Code -> throw Error()
        is CTerm.Type -> DTerm.Type(term.id!!)
    }

    private fun defunctionalizePattern(pattern: CPattern): DPattern = when (pattern) {
        is CPattern.Var -> DPattern.Var(pattern.name, pattern.id)
        is CPattern.UnitOf -> DPattern.UnitOf(pattern.id)
        is CPattern.BoolOf -> DPattern.BoolOf(pattern.value, pattern.id)
        is CPattern.ByteOf -> DPattern.ByteOf(pattern.value, pattern.id)
        is CPattern.ShortOf -> DPattern.ShortOf(pattern.value, pattern.id)
        is CPattern.IntOf -> DPattern.IntOf(pattern.value, pattern.id)
        is CPattern.LongOf -> DPattern.LongOf(pattern.value, pattern.id)
        is CPattern.FloatOf -> DPattern.FloatOf(pattern.value, pattern.id)
        is CPattern.DoubleOf -> DPattern.DoubleOf(pattern.value, pattern.id)
        is CPattern.StringOf -> DPattern.StringOf(pattern.value, pattern.id)
        is CPattern.ByteArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            DPattern.ByteArrayOf(elements, pattern.id)
        }
        is CPattern.IntArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            DPattern.IntArrayOf(elements, pattern.id)
        }
        is CPattern.LongArrayOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            DPattern.LongArrayOf(elements, pattern.id)
        }
        is CPattern.ListOf -> {
            val elements = pattern.elements.map { defunctionalizePattern(it) }
            DPattern.ListOf(elements, pattern.id)
        }
        is CPattern.CompoundOf -> {
            val elements = pattern.elements.map { (name, element) -> name to defunctionalizePattern(element) }
            DPattern.CompoundOf(elements.toLinkedHashMap(), pattern.id)
        }
        is CPattern.BoxOf -> {
            val content = defunctionalizePattern(pattern.content)
            val tag = defunctionalizePattern(pattern.tag)
            DPattern.BoxOf(content, tag, pattern.id)
        }
        is CPattern.RefOf -> {
            val element = defunctionalizePattern(pattern.element)
            DPattern.RefOf(element, pattern.id)
        }
        is CPattern.Refl -> DPattern.Refl(pattern.id)
        is CPattern.Unit -> DPattern.Unit(pattern.id)
        is CPattern.Bool -> DPattern.Bool(pattern.id)
        is CPattern.Byte -> DPattern.Byte(pattern.id)
        is CPattern.Short -> DPattern.Short(pattern.id)
        is CPattern.Int -> DPattern.Int(pattern.id)
        is CPattern.Long -> DPattern.Long(pattern.id)
        is CPattern.Float -> DPattern.Float(pattern.id)
        is CPattern.Double -> DPattern.Double(pattern.id)
        is CPattern.String -> DPattern.String(pattern.id)
        is CPattern.ByteArray -> DPattern.ByteArray(pattern.id)
        is CPattern.IntArray -> DPattern.IntArray(pattern.id)
        is CPattern.LongArray -> DPattern.LongArray(pattern.id)
    }

    private fun defunctionalizeEffect(effect: CEffect): DEffect = when (effect) {
        is CEffect.Name -> DEffect.Name(effect.name)
    }

    data class Result(
        val item: DItem,
        val types: Map<Id, CVTerm>,
        val functions: Map<Int, DTerm>
    )

    companion object {
        private val tag: AtomicInteger = AtomicInteger(0)

        private fun freshTag(): Int = tag.getAndIncrement()

        operator fun invoke(input: Stage.Result): Result = Defunctionalize().run {
            Result(defunctionalizeItem(input.item), input.types, functions)
        }
    }
}
