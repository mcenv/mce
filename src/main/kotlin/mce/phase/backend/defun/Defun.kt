package mce.phase.backend.defun

import mce.phase.Id
import mce.phase.backend.stage.Stage
import mce.util.toLinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import mce.phase.backend.defun.Eff as DEff
import mce.phase.backend.defun.Entry as DEntry
import mce.phase.backend.defun.Item as DItem
import mce.phase.backend.defun.Modifier as DModifier
import mce.phase.backend.defun.Module as DModule
import mce.phase.backend.defun.Param as DParam
import mce.phase.backend.defun.Pat as DPat
import mce.phase.backend.defun.Signature as DSignature
import mce.phase.backend.defun.Term as DTerm
import mce.phase.frontend.elab.Eff as CEff
import mce.phase.frontend.elab.Item as CItem
import mce.phase.frontend.elab.Modifier as CModifier
import mce.phase.frontend.elab.Module as CModule
import mce.phase.frontend.elab.Param as CParam
import mce.phase.frontend.elab.Pat as CPat
import mce.phase.frontend.elab.Signature as CSignature
import mce.phase.frontend.elab.Term as CTerm
import mce.phase.frontend.elab.VTerm as CVTerm

@Suppress("NAME_SHADOWING")
class Defun private constructor() {
    private val functions: MutableMap<Int, DTerm> = mutableMapOf()

    private fun defunItem(item: CItem): DItem {
        val modifiers = item.modifiers.map { defunModifier(it) }.toSet()
        return when (item) {
            is CItem.Def -> {
                val params = item.params.map { defunParam(it) }
                val body = defunTerm(item.body)
                DItem.Def(modifiers, item.name, params, body, item.id)
            }
            is CItem.Mod -> {
                val body = defunModule(item.body)
                DItem.Mod(modifiers, item.name, body, item.id)
            }
            is CItem.Test -> {
                val body = defunTerm(item.body)
                DItem.Test(modifiers, item.name, body, item.id)
            }
        }
    }

    private fun defunModifier(modifier: CModifier): DModifier = when (modifier) {
        CModifier.BUILTIN -> DModifier.BUILTIN
        CModifier.META -> throw Error()
    }

    private fun defunParam(param: CParam): DParam {
        val lower = param.lower?.let { defunTerm(it) }
        val upper = param.upper?.let { defunTerm(it) }
        val type = defunTerm(param.type)
        return DParam(param.termRelevant, param.name, lower, upper, param.typeRelevant, type, param.id)
    }

    private fun defunModule(module: CModule): DModule = when (module) {
        is CModule.Var -> DModule.Var(module.name, module.id)
        is CModule.Str -> {
            val items = module.items.map { defunItem(it) }
            DModule.Str(items, module.id!!)
        }
        is CModule.Sig -> {
            val signatures = module.signatures.map { defunSignature(it) }
            DModule.Sig(signatures, module.id!!)
        }
        is CModule.Type -> DModule.Type(module.id)
    }

    private fun defunSignature(signature: CSignature): DSignature = when (signature) {
        is CSignature.Def -> {
            val params = signature.params.map { defunParam(it) }
            val resultant = defunTerm(signature.resultant)
            DSignature.Def(signature.name, params, resultant, signature.id!!)
        }
        is CSignature.Mod -> {
            val type = defunModule(signature.type)
            DSignature.Mod(signature.name, type, signature.id!!)
        }
        is CSignature.Test -> DSignature.Test(signature.name, signature.id!!)
    }

    private fun defunTerm(term: CTerm): DTerm = when (term) {
        is CTerm.Hole -> throw Error()
        is CTerm.Meta -> throw Error()
        is CTerm.Var -> DTerm.Var(term.name, term.level, term.id!!)
        is CTerm.Def -> DTerm.Def(term.name, term.arguments.map { defunTerm(it) }, term.id!!)
        is CTerm.Let -> {
            val init = defunTerm(term.init)
            val body = defunTerm(term.body)
            DTerm.Let(term.name, init, body, term.id!!)
        }
        is CTerm.Match -> {
            val scrutinee = defunTerm(term.scrutinee)
            val clauses = term.clauses.map { defunPat(it.first) to defunTerm(it.second) }
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
            val elements = term.elements.map { defunTerm(it) }
            DTerm.ByteArrayOf(elements, term.id!!)
        }
        is CTerm.IntArrayOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.IntArrayOf(elements, term.id!!)
        }
        is CTerm.LongArrayOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.LongArrayOf(elements, term.id!!)
        }
        is CTerm.ListOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.ListOf(elements, term.id!!)
        }
        is CTerm.CompoundOf -> {
            val elements = term.elements.map { (name, element) -> name to defunTerm(element) }
            DTerm.CompoundOf(elements.toLinkedHashMap(), term.id!!)
        }
        is CTerm.BoxOf -> {
            val content = defunTerm(term.content)
            val tag = defunTerm(term.tag)
            DTerm.BoxOf(content, tag, term.id!!)
        }
        is CTerm.RefOf -> {
            val element = defunTerm(term.element)
            DTerm.RefOf(element, term.id!!)
        }
        is CTerm.Refl -> DTerm.Refl(term.id!!)
        is CTerm.FunOf -> {
            val tag = freshTag()
            DTerm.FunOf(tag, term.id!!).also {
                val body = defunTerm(term.body)
                functions[tag] = body
            }
        }
        is CTerm.Apply -> {
            val function = defunTerm(term.function)
            val arguments = term.arguments.map { defunTerm(it) }
            DTerm.Apply(function, arguments, term.id!!)
        }
        is CTerm.CodeOf -> throw Error()
        is CTerm.Splice -> throw Error()
        is CTerm.Or -> {
            val variants = term.variants.map { defunTerm(it) }
            DTerm.Or(variants, term.id!!)
        }
        is CTerm.And -> {
            val variants = term.variants.map { defunTerm(it) }
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
            val element = defunTerm(term.element)
            val size = defunTerm(term.size)
            DTerm.List(element, size, term.id!!)
        }
        is CTerm.Compound -> {
            val elements = term.elements.map { (name, element) -> name to DEntry(element.relevant, defunTerm(element.type), element.id!!) }
            DTerm.Compound(elements.toLinkedHashMap(), term.id!!)
        }
        is CTerm.Box -> {
            val content = defunTerm(term.content)
            DTerm.Box(content, term.id!!)
        }
        is CTerm.Ref -> {
            val element = defunTerm(term.element)
            DTerm.Ref(element, term.id!!)
        }
        is CTerm.Eq -> {
            val left = defunTerm(term.left)
            val right = defunTerm(term.right)
            DTerm.Eq(left, right, term.id!!)
        }
        is CTerm.Fun -> {
            val parameters = term.params.map { defunParam(it) }
            val resultant = defunTerm(term.resultant)
            val effects = term.effs.map { defunEff(it) }.toSet()
            DTerm.Fun(parameters, resultant, effects, term.id!!)
        }
        is CTerm.Code -> throw Error()
        is CTerm.Type -> DTerm.Type(term.id!!)
    }

    private fun defunPat(pat: CPat): DPat = when (pat) {
        is CPat.Var -> DPat.Var(pat.name, pat.id)
        is CPat.UnitOf -> DPat.UnitOf(pat.id)
        is CPat.BoolOf -> DPat.BoolOf(pat.value, pat.id)
        is CPat.ByteOf -> DPat.ByteOf(pat.value, pat.id)
        is CPat.ShortOf -> DPat.ShortOf(pat.value, pat.id)
        is CPat.IntOf -> DPat.IntOf(pat.value, pat.id)
        is CPat.LongOf -> DPat.LongOf(pat.value, pat.id)
        is CPat.FloatOf -> DPat.FloatOf(pat.value, pat.id)
        is CPat.DoubleOf -> DPat.DoubleOf(pat.value, pat.id)
        is CPat.StringOf -> DPat.StringOf(pat.value, pat.id)
        is CPat.ByteArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.ByteArrayOf(elements, pat.id)
        }
        is CPat.IntArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.IntArrayOf(elements, pat.id)
        }
        is CPat.LongArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.LongArrayOf(elements, pat.id)
        }
        is CPat.ListOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.ListOf(elements, pat.id)
        }
        is CPat.CompoundOf -> {
            val elements = pat.elements.map { (name, element) -> name to defunPat(element) }
            DPat.CompoundOf(elements.toLinkedHashMap(), pat.id)
        }
        is CPat.BoxOf -> {
            val content = defunPat(pat.content)
            val tag = defunPat(pat.tag)
            DPat.BoxOf(content, tag, pat.id)
        }
        is CPat.RefOf -> {
            val element = defunPat(pat.element)
            DPat.RefOf(element, pat.id)
        }
        is CPat.Refl -> DPat.Refl(pat.id)
        is CPat.Unit -> DPat.Unit(pat.id)
        is CPat.Bool -> DPat.Bool(pat.id)
        is CPat.Byte -> DPat.Byte(pat.id)
        is CPat.Short -> DPat.Short(pat.id)
        is CPat.Int -> DPat.Int(pat.id)
        is CPat.Long -> DPat.Long(pat.id)
        is CPat.Float -> DPat.Float(pat.id)
        is CPat.Double -> DPat.Double(pat.id)
        is CPat.String -> DPat.String(pat.id)
        is CPat.ByteArray -> DPat.ByteArray(pat.id)
        is CPat.IntArray -> DPat.IntArray(pat.id)
        is CPat.LongArray -> DPat.LongArray(pat.id)
    }

    private fun defunEff(eff: CEff): DEff = when (eff) {
        is CEff.Name -> DEff.Name(eff.name)
    }

    data class Result(
        val item: DItem,
        val types: Map<Id, CVTerm>,
        val functions: Map<Int, DTerm>,
    )

    companion object {
        private val tag: AtomicInteger = AtomicInteger(0)

        private fun freshTag(): Int = tag.getAndIncrement()

        operator fun invoke(input: Stage.Result): Result = Defun().run {
            Result(defunItem(input.item), input.types, functions)
        }
    }
}
