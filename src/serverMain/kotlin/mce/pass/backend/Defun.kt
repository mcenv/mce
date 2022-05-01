package mce.pass.backend

import mce.Id
import mce.pass.Config
import mce.pass.Pass
import java.util.concurrent.atomic.AtomicInteger
import mce.ast.core.Eff as CEff
import mce.ast.core.Item as CItem
import mce.ast.core.Module as CModule
import mce.ast.core.Param as CParam
import mce.ast.core.Pat as CPat
import mce.ast.core.Signature as CSignature
import mce.ast.core.Term as CTerm
import mce.ast.core.VTerm as CVTerm
import mce.ast.defun.Eff as DEff
import mce.ast.defun.Item as DItem
import mce.ast.defun.Module as DModule
import mce.ast.defun.Param as DParam
import mce.ast.defun.Pat as DPat
import mce.ast.defun.Signature as DSignature
import mce.ast.defun.Term as DTerm

@Suppress("NAME_SHADOWING")
class Defun private constructor(
    private val types: Map<Id, CVTerm>,
) {
    private val defunctions: MutableList<Defunction> = mutableListOf()

    private fun defunItem(item: CItem): DItem =
        when (item) {
            is CItem.Def -> {
                val params = item.params.map { defunParam(it) }
                val body = defunTerm(item.body)
                DItem.Def(item.modifiers, item.name, params, body)
            }
            is CItem.Mod -> {
                val body = defunModule(item.body)
                DItem.Mod(item.modifiers, item.name, body)
            }
            is CItem.Test -> {
                val body = defunTerm(item.body)
                DItem.Test(item.modifiers, item.name, body)
            }
            is CItem.Advancement -> {
                val body = defunTerm(item.body)
                DItem.Advancement(item.modifiers, item.name, body)
            }
            is CItem.Pack -> throw Error()
        }

    private fun defunParam(param: CParam): DParam {
        val lower = param.lower?.let { defunTerm(it) }
        val upper = param.upper?.let { defunTerm(it) }
        val type = defunTerm(param.type)
        return DParam(param.termRelevant, param.name, lower, upper, param.typeRelevant, type)
    }

    private fun defunModule(module: CModule): DModule = when (module) {
        is CModule.Var -> DModule.Var(module.name)
        is CModule.Str -> {
            val items = module.items.map { defunItem(it) }
            DModule.Str(items)
        }
        is CModule.Sig -> {
            val signatures = module.signatures.map { defunSignature(it) }
            DModule.Sig(signatures)
        }
        is CModule.Type -> DModule.Type
    }

    private fun defunSignature(signature: CSignature): DSignature = when (signature) {
        is CSignature.Def -> {
            val params = signature.params.map { defunParam(it) }
            val resultant = defunTerm(signature.resultant)
            DSignature.Def(signature.name, params, resultant)
        }
        is CSignature.Mod -> {
            val type = defunModule(signature.type)
            DSignature.Mod(signature.name, type)
        }
        is CSignature.Test -> DSignature.Test(signature.name)
        is CSignature.Advancement -> DSignature.Advancement(signature.name)
        is CSignature.Pack -> throw Error()
    }

    private fun defunTerm(term: CTerm): DTerm = when (term) {
        is CTerm.Builtin -> DTerm.Builtin(getType(term.id!!))
        is CTerm.Hole -> throw Error()
        is CTerm.Meta -> throw Error()
        is CTerm.Command -> {
            val body = defunTerm(term.body)
            DTerm.Command(body, CVTerm.Or(emptyList()))
        }
        is CTerm.Block -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.Block(elements, getType(term.id!!))
        }
        is CTerm.Var -> DTerm.Var(term.name, term.level, getType(term.id!!))
        is CTerm.Def -> DTerm.Def(term.name, term.arguments.map { defunTerm(it) }, getType(term.id!!))
        is CTerm.Let -> {
            val init = defunTerm(term.init)
            DTerm.Let(term.name, init, getType(term.id!!))
        }
        is CTerm.Match -> {
            val scrutinee = defunTerm(term.scrutinee)
            val clauses = term.clauses.map { defunPat(it.first) to defunTerm(it.second) }
            DTerm.Match(scrutinee, clauses, getType(term.id!!))
        }
        is CTerm.UnitOf -> DTerm.UnitOf(CVTerm.Unit())
        is CTerm.BoolOf -> DTerm.BoolOf(term.value, CVTerm.Bool())
        is CTerm.ByteOf -> DTerm.ByteOf(term.value, CVTerm.Byte())
        is CTerm.ShortOf -> DTerm.ShortOf(term.value, CVTerm.Short())
        is CTerm.IntOf -> DTerm.IntOf(term.value, CVTerm.Int())
        is CTerm.LongOf -> DTerm.LongOf(term.value, CVTerm.Long())
        is CTerm.FloatOf -> DTerm.FloatOf(term.value, CVTerm.Float())
        is CTerm.DoubleOf -> DTerm.DoubleOf(term.value, CVTerm.Double())
        is CTerm.StringOf -> DTerm.StringOf(term.value, CVTerm.String())
        is CTerm.ByteArrayOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.ByteArrayOf(elements, CVTerm.ByteArray())
        }
        is CTerm.IntArrayOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.IntArrayOf(elements, CVTerm.IntArray())
        }
        is CTerm.LongArrayOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.LongArrayOf(elements, CVTerm.LongArray())
        }
        is CTerm.ListOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.ListOf(elements, getType(term.id!!))
        }
        is CTerm.CompoundOf -> {
            val elements = term.elements.map { element -> DTerm.CompoundOf.Entry(element.name, defunTerm(element.element)) }
            DTerm.CompoundOf(elements, getType(term.id!!))
        }
        is CTerm.TupleOf -> {
            val elements = term.elements.map { defunTerm(it) }
            DTerm.TupleOf(elements, getType(term.id!!))
        }
        is CTerm.RefOf -> {
            val element = defunTerm(term.element)
            DTerm.RefOf(element, getType(term.id!!))
        }
        is CTerm.Refl -> DTerm.Refl(getType(term.id!!))
        is CTerm.FunOf -> {
            val tag = freshTag()
            val type = getType(term.id!!) as CVTerm.Fun
            DTerm.FunOf(tag, type).also {
                val body = defunTerm(term.body)
                defunctions += Defunction(tag, (term.params zip type.params).map { (name, param) -> name.text to getType(param.id) }, body)
            }
        }
        is CTerm.Apply -> {
            val function = defunTerm(term.function)
            val arguments = term.arguments.map { defunTerm(it) }
            DTerm.Apply(function, arguments, getType(term.id!!))
        }
        is CTerm.CodeOf -> throw Error()
        is CTerm.Splice -> throw Error()
        is CTerm.Or -> {
            val variants = term.variants.map { defunTerm(it) }
            DTerm.Or(variants, CVTerm.Type())
        }
        is CTerm.And -> {
            val variants = term.variants.map { defunTerm(it) }
            DTerm.And(variants, CVTerm.Type())
        }
        is CTerm.Unit -> DTerm.Unit(CVTerm.Type())
        is CTerm.Bool -> DTerm.Bool(CVTerm.Type())
        is CTerm.Byte -> DTerm.Byte(CVTerm.Type())
        is CTerm.Short -> DTerm.Short(CVTerm.Type())
        is CTerm.Int -> DTerm.Int(CVTerm.Type())
        is CTerm.Long -> DTerm.Long(CVTerm.Type())
        is CTerm.Float -> DTerm.Float(CVTerm.Type())
        is CTerm.Double -> DTerm.Double(CVTerm.Type())
        is CTerm.String -> DTerm.String(CVTerm.Type())
        is CTerm.ByteArray -> DTerm.ByteArray(CVTerm.Type())
        is CTerm.IntArray -> DTerm.IntArray(CVTerm.Type())
        is CTerm.LongArray -> DTerm.LongArray(CVTerm.Type())
        is CTerm.List -> {
            val element = defunTerm(term.element)
            val size = defunTerm(term.size)
            DTerm.List(element, size, CVTerm.Type())
        }
        is CTerm.Compound -> {
            val elements = term.elements.map { element -> DTerm.Compound.Entry(element.relevant, element.name, defunTerm(element.type)) }
            DTerm.Compound(elements, CVTerm.Type())
        }
        is CTerm.Tuple -> {
            val elements = term.elements.map { DTerm.Tuple.Entry(it.relevant, defunTerm(it.type)) }
            DTerm.Tuple(elements, CVTerm.Type())
        }
        is CTerm.Ref -> {
            val element = defunTerm(term.element)
            DTerm.Ref(element, CVTerm.Type())
        }
        is CTerm.Eq -> {
            val left = defunTerm(term.left)
            val right = defunTerm(term.right)
            DTerm.Eq(left, right, CVTerm.Type())
        }
        is CTerm.Fun -> {
            val parameters = term.params.map { defunParam(it) }
            val resultant = defunTerm(term.resultant)
            val effects = term.effs.map { defunEff(it) }.toSet()
            DTerm.Fun(parameters, resultant, effects, CVTerm.Type())
        }
        is CTerm.Code -> throw Error()
        is CTerm.Type -> DTerm.Type(CVTerm.Type())
    }

    private fun defunPat(pat: CPat): DPat = when (pat) {
        is CPat.Var -> DPat.Var(pat.name, getType(pat.id))
        is CPat.UnitOf -> DPat.UnitOf(getType(pat.id))
        is CPat.BoolOf -> DPat.BoolOf(pat.value, getType(pat.id))
        is CPat.ByteOf -> DPat.ByteOf(pat.value, getType(pat.id))
        is CPat.ShortOf -> DPat.ShortOf(pat.value, getType(pat.id))
        is CPat.IntOf -> DPat.IntOf(pat.value, getType(pat.id))
        is CPat.LongOf -> DPat.LongOf(pat.value, getType(pat.id))
        is CPat.FloatOf -> DPat.FloatOf(pat.value, getType(pat.id))
        is CPat.DoubleOf -> DPat.DoubleOf(pat.value, getType(pat.id))
        is CPat.StringOf -> DPat.StringOf(pat.value, getType(pat.id))
        is CPat.ByteArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.ByteArrayOf(elements, getType(pat.id))
        }
        is CPat.IntArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.IntArrayOf(elements, getType(pat.id))
        }
        is CPat.LongArrayOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.LongArrayOf(elements, getType(pat.id))
        }
        is CPat.ListOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.ListOf(elements, getType(pat.id))
        }
        is CPat.CompoundOf -> {
            val elements = pat.elements.map { (name, element) -> name to defunPat(element) }
            DPat.CompoundOf(elements, getType(pat.id))
        }
        is CPat.TupleOf -> {
            val elements = pat.elements.map { defunPat(it) }
            DPat.TupleOf(elements, getType(pat.id))
        }
        is CPat.RefOf -> {
            val element = defunPat(pat.element)
            DPat.RefOf(element, getType(pat.id))
        }
        is CPat.Refl -> DPat.Refl(getType(pat.id))
    }

    private fun defunEff(eff: CEff): DEff = when (eff) {
        is CEff.Name -> DEff.Name(eff.name)
    }

    private fun getType(id: Id): CVTerm = types[id]!!

    data class Defunction(
        val tag: Int,
        val params: List<Pair<String, CVTerm>>,
        val body: DTerm,
    )

    data class Result(
        val item: DItem,
        val defunctions: List<Defunction>,
    )

    companion object : Pass<Stage.Result, Result> {
        private val tag: AtomicInteger = AtomicInteger(0)

        private fun freshTag(): Int = tag.getAndIncrement()

        override operator fun invoke(config: Config, input: Stage.Result): Result = Defun(input.types).run {
            Result(defunItem(input.item), defunctions)
        }
    }
}
