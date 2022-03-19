package mce.phase

import mce.ast.core.*
import mce.util.toLinkedHashMap

abstract class Map {
    open fun mapItem(item: Item): Item = mapItemInternal(item)

    open fun mapParameter(parameter: Parameter): Parameter = mapParameterInternal(parameter)

    open fun mapModule(module: Module): Module = mapModuleInternal(module)

    open fun mapSignature(signature: Signature): Signature = mapSignatureInternal(signature)

    open fun mapTerm(term: Term): Term = mapTermInternal(term)

    protected fun mapItemInternal(item: Item): Item = when (item) {
        is Item.Def -> {
            val parameters = item.parameters.map { mapParameter(it) }
            val body = mapTerm(item.body)
            Item.Def(item.imports, item.exports, item.modifiers, item.name, parameters, item.resultant, item.effects, body, item.id)
        }
        is Item.Mod -> {
            val body = mapModule(item.body)
            Item.Mod(item.imports, item.exports, item.modifiers, item.name, item.type, body, item.id)
        }
        is Item.Test -> {
            val body = mapTerm(item.body)
            Item.Test(item.imports, item.exports, item.modifiers, item.name, body, item.id)
        }
    }

    protected fun mapParameterInternal(parameter: Parameter): Parameter {
        val lower = parameter.lower?.let { mapTerm(it) }
        val upper = parameter.upper?.let { mapTerm(it) }
        val type = mapTerm(parameter.type)
        return Parameter(parameter.termRelevant, parameter.name, lower, upper, parameter.typeRelevant, type, parameter.id)
    }

    protected fun mapModuleInternal(module: Module): Module = when (module) {
        is Module.Var -> module
        is Module.Str -> {
            val items = module.items.map { mapItem(it) }
            Module.Str(items, module.id!!)
        }
        is Module.Sig -> {
            val signatures = module.signatures.map { mapSignature(it) }
            Module.Sig(signatures, module.id!!)
        }
        is Module.Type -> module
    }

    protected fun mapSignatureInternal(signature: Signature): Signature = when (signature) {
        is Signature.Def -> {
            val parameters = signature.parameters.map { mapParameter(it) }
            val resultant = mapTerm(signature.resultant)
            Signature.Def(signature.name, parameters, resultant, signature.id)
        }
        is Signature.Mod -> {
            val type = mapModule(signature.type)
            Signature.Mod(signature.name, type, signature.id)
        }
        is Signature.Test -> Signature.Test(signature.name, signature.id)
    }

    protected fun mapTermInternal(term: Term): Term = when (term) {
        is Term.Hole -> Term.Hole(term.id)
        is Term.Meta -> Term.Meta(term.index, term.id)
        is Term.Var -> Term.Var(term.name, term.level, term.id)
        is Term.Def -> Term.Def(term.name, term.arguments.map { mapTerm(it) }, term.id)
        is Term.Let -> Term.Let(term.name, mapTerm(term.init), mapTerm(term.body), term.id)
        is Term.Match -> Term.Match(mapTerm(term.scrutinee), term.clauses.map { it.first to mapTerm(it.second) }, term.id)
        is Term.UnitOf -> term
        is Term.BoolOf -> term
        is Term.ByteOf -> term
        is Term.ShortOf -> term
        is Term.IntOf -> term
        is Term.LongOf -> term
        is Term.FloatOf -> term
        is Term.DoubleOf -> term
        is Term.StringOf -> term
        is Term.ByteArrayOf -> Term.ByteArrayOf(term.elements.map { mapTerm(it) }, term.id)
        is Term.IntArrayOf -> Term.IntArrayOf(term.elements.map { mapTerm(it) }, term.id)
        is Term.LongArrayOf -> Term.LongArrayOf(term.elements.map { mapTerm(it) }, term.id)
        is Term.ListOf -> Term.ListOf(term.elements.map { mapTerm(it) }, term.id)
        is Term.CompoundOf -> Term.CompoundOf(term.elements.map { (name, element) -> name to mapTerm(element) }.toLinkedHashMap(), term.id)
        is Term.BoxOf -> Term.BoxOf(mapTerm(term.content), mapTerm(term.tag), term.id)
        is Term.RefOf -> Term.RefOf(mapTerm(term.element), term.id)
        is Term.Refl -> term
        is Term.FunOf -> Term.FunOf(term.parameters, mapTerm(term.body), term.id)
        is Term.Apply -> Term.Apply(mapTerm(term.function), term.arguments.map { mapTerm(it) }, term.id)
        is Term.CodeOf -> Term.CodeOf(mapTerm(term.element), term.id)
        is Term.Splice -> Term.Splice(mapTerm(term.element), term.id)
        is Term.Or -> Term.Or(term.variants.map { mapTerm(it) }, term.id)
        is Term.And -> Term.And(term.variants.map { mapTerm(it) }, term.id)
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
        is Term.List -> Term.List(mapTerm(term.element), mapTerm(term.size), term.id)
        is Term.Compound -> Term.Compound(term.elements.map { (name, element) -> name to Entry(element.relevant, mapTerm(element.type), element.id) }.toLinkedHashMap(), term.id)
        is Term.Box -> Term.Box(mapTerm(term.content), term.id)
        is Term.Ref -> Term.Ref(mapTerm(term.element), term.id)
        is Term.Eq -> Term.Eq(mapTerm(term.left), mapTerm(term.right), term.id)
        is Term.Fun -> Term.Fun(term.parameters.map { mapParameter(it) }, mapTerm(term.resultant), term.effects, term.id)
        is Term.Code -> Term.Code(mapTerm(term.element), term.id)
        is Term.Type -> term
    }
}
