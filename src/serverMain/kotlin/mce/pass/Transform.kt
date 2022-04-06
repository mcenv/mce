package mce.pass

import mce.ast.core.*

abstract class Transform {
    open fun transformItem(item: Item): Item = transformItemInternal(item)

    open fun transformParam(param: Param): Param = transformParamInternal(param)

    open fun transformModule(module: Module): Module = transformModuleInternal(module)

    open fun transformSignature(signature: Signature): Signature = transformSignatureInternal(signature)

    open fun transformTerm(term: Term): Term = transformTermInternal(term)

    protected fun transformItemInternal(item: Item): Item = when (item) {
        is Item.Def -> {
            val parameters = item.params.map { transformParam(it) }
            val body = transformTerm(item.body)
            Item.Def(item.modifiers, item.name, parameters, item.resultant, item.effs, body, item.id)
        }
        is Item.Mod -> {
            val body = transformModule(item.body)
            Item.Mod(item.modifiers, item.name, item.type, body, item.id)
        }
        is Item.Test -> {
            val body = transformTerm(item.body)
            Item.Test(item.modifiers, item.name, body, item.id)
        }
        is Item.Pack -> {
            val body = transformTerm(item.body)
            Item.Pack(body, item.id)
        }
    }

    protected fun transformParamInternal(param: Param): Param {
        val lower = param.lower?.let { transformTerm(it) }
        val upper = param.upper?.let { transformTerm(it) }
        val type = transformTerm(param.type)
        return Param(param.termRelevant, param.name, lower, upper, param.typeRelevant, type, param.id)
    }

    protected fun transformModuleInternal(module: Module): Module = when (module) {
        is Module.Var -> module
        is Module.Str -> {
            val items = module.items.map { transformItem(it) }
            Module.Str(items, module.id!!)
        }
        is Module.Sig -> {
            val signatures = module.signatures.map { transformSignature(it) }
            Module.Sig(signatures, module.id!!)
        }
        is Module.Type -> module
    }

    protected fun transformSignatureInternal(signature: Signature): Signature = when (signature) {
        is Signature.Def -> {
            val parameters = signature.params.map { transformParam(it) }
            val resultant = transformTerm(signature.resultant)
            Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is Signature.Mod -> {
            val type = transformModule(signature.type)
            Signature.Mod(signature.name, type, signature.id)
        }
        is Signature.Test -> Signature.Test(signature.name, signature.id)
        is Signature.Pack -> signature
    }

    protected fun transformTermInternal(term: Term): Term = when (term) {
        is Term.Hole -> term
        is Term.Meta -> term
        is Term.Block -> Term.Block(term.elements.map { transformTerm(it) }, term.id)
        is Term.Var -> term
        is Term.Def -> Term.Def(term.name, term.arguments.map { transformTerm(it) }, term.id)
        is Term.Let -> Term.Let(term.name, transformTerm(term.init), term.id)
        is Term.Match -> Term.Match(transformTerm(term.scrutinee), term.clauses.map { it.first to transformTerm(it.second) }, term.id)
        is Term.UnitOf -> term
        is Term.BoolOf -> term
        is Term.ByteOf -> term
        is Term.ShortOf -> term
        is Term.IntOf -> term
        is Term.LongOf -> term
        is Term.FloatOf -> term
        is Term.DoubleOf -> term
        is Term.StringOf -> term
        is Term.ByteArrayOf -> Term.ByteArrayOf(term.elements.map { transformTerm(it) }, term.id)
        is Term.IntArrayOf -> Term.IntArrayOf(term.elements.map { transformTerm(it) }, term.id)
        is Term.LongArrayOf -> Term.LongArrayOf(term.elements.map { transformTerm(it) }, term.id)
        is Term.ListOf -> Term.ListOf(term.elements.map { transformTerm(it) }, term.id)
        is Term.CompoundOf -> Term.CompoundOf(term.elements.map { element -> Term.CompoundOf.Entry(element.name, transformTerm(element.element)) }, term.id)
        is Term.TupleOf -> Term.TupleOf(term.elements.map { transformTerm(it) }, term.id)
        is Term.RefOf -> Term.RefOf(transformTerm(term.element), term.id)
        is Term.Refl -> term
        is Term.FunOf -> Term.FunOf(term.params, transformTerm(term.body), term.id)
        is Term.Apply -> Term.Apply(transformTerm(term.function), term.arguments.map { transformTerm(it) }, term.id)
        is Term.CodeOf -> Term.CodeOf(transformTerm(term.element), term.id)
        is Term.Splice -> Term.Splice(transformTerm(term.element), term.id)
        is Term.Or -> Term.Or(term.variants.map { transformTerm(it) }, term.id)
        is Term.And -> Term.And(term.variants.map { transformTerm(it) }, term.id)
        is Term.Unit -> term
        is Term.Bool -> term
        is Term.Byte -> term
        is Term.Short -> term
        is Term.Int -> term
        is Term.Long -> term
        is Term.Float -> term
        is Term.Double -> term
        is Term.String -> term
        is Term.ByteArray -> term
        is Term.IntArray -> term
        is Term.LongArray -> term
        is Term.List -> Term.List(transformTerm(term.element), transformTerm(term.size), term.id)
        is Term.Compound -> Term.Compound(term.elements.map { element -> Term.Compound.Entry(element.relevant, element.name, transformTerm(element.type), element.id) }, term.id)
        is Term.Tuple -> Term.Tuple(term.elements.map { Term.Tuple.Entry(it.relevant, it.name, transformTerm(it.type), it.id) }, term.id)
        is Term.Ref -> Term.Ref(transformTerm(term.element), term.id)
        is Term.Eq -> Term.Eq(transformTerm(term.left), transformTerm(term.right), term.id)
        is Term.Fun -> Term.Fun(term.params.map { transformParam(it) }, transformTerm(term.resultant), term.effs, term.id)
        is Term.Code -> Term.Code(transformTerm(term.element), term.id)
        is Term.Type -> term
    }
}
