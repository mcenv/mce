package mce.phase.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mce.ast.Id
import mce.ast.Name
import mce.ast.freshId
import mce.phase.Normalizer
import mce.phase.frontend.Diagnostic.Companion.serializeEffect
import mce.phase.frontend.Diagnostic.Companion.serializeTerm
import mce.util.*
import mce.ast.Core as C
import mce.ast.Surface as S

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
    private val items: Map<String, C.Item>,
) {
    private val diagnostics: MutableList<Diagnostic> = mutableListOf()
    private val completions: MutableMap<Id, List<Pair<String, C.VTerm>>> = mutableMapOf()
    private val types: MutableMap<Id, C.VTerm> = mutableMapOf()
    private val solutions: MutableList<C.VTerm?> = mutableListOf()

    /**
     * Infers the signature of the [item].
     */
    private fun inferItem(item: S.Item): Pair<C.Item, C.VSignature> =
        (run {
            val modifiers = item.modifiers.mapTo(mutableSetOf()) { elaborateModifier(it) }
            when (item) {
                is S.Item.Def ->
                    modify<Context> { meta(item.modifiers.contains(S.Modifier.META)).irrelevant() } % {
                        bindParameters(item.parameters) % { parameters ->
                            checkTerm(item.resultant, TYPE) % { resultant ->
                                val vResultant = normalizer.evalTerm(resultant)
                                val effects = item.effects.map { elaborateEffect(it) }.toSet()
                                (if (item.modifiers.contains(S.Modifier.BUILTIN)) {
                                    pure(C.Term.Hole(item.body.id))
                                } else {
                                    put(relevant()) % {
                                        checkTerm(item.body, vResultant)
                                    }
                                }) / { body ->
                                    checkPhase(item.body.id, vResultant)
                                    C.Item.Def(item.imports, item.exports, modifiers, item.name, parameters, resultant, effects, body, item.id) to C.VSignature.Def(item.name, parameters, resultant, null)
                                }
                            }
                        }
                    }
                is S.Item.Mod ->
                    checkModule(item.type, C.VModule.Type(null)) % { type ->
                        val vType = normalizer.evalModule(type)
                        checkModule(item.body, vType) / { body ->
                            C.Item.Mod(item.imports, item.exports, modifiers, item.name, vType, body, item.id) to C.VSignature.Mod(item.name, vType, null)
                        }
                    }
                is S.Item.Test ->
                    checkTerm(item.body, BOOL) / { body ->
                        C.Item.Test(item.imports, item.exports, modifiers, item.name, body, item.id) to C.VSignature.Test(item.name, null)
                    }
            }
        } with Context(persistentListOf(), Normalizer(persistentListOf(), items, solutions), false, 0, true)).second

    /**
     * Checks the [item] against the [signature] under this context.
     */
    private fun checkItem(item: S.Item, signature: C.VSignature): C.Item {
        val (inferred, inferredSignature) = inferItem(item)
        if (!Normalizer(persistentListOf(), items, solutions).unifySignatures(inferredSignature, signature)) {
            diagnose(Diagnostic.SignatureMismatch(item.id)) // TODO: serialize signatures
        }
        return inferred
    }

    /**
     * Elaborates the [modifier].
     */
    private fun elaborateModifier(modifier: S.Modifier): C.Modifier = when (modifier) {
        S.Modifier.BUILTIN -> C.Modifier.BUILTIN
        S.Modifier.META -> C.Modifier.META
    }

    /**
     * Binds the [parameters] to the context.
     */
    private fun bindParameters(parameters: List<S.Parameter>): State<Context, List<C.Parameter>> =
        parameters.mapM { parameter ->
            checkTerm(parameter.type, TYPE) % { type ->
                val vType = normalizer.evalTerm(type)
                val lower = parameter.lower?.let { (checkTerm(it, vType) with this).second }
                val vLower = lower?.let { normalizer.evalTerm(it) }
                val upper = parameter.upper?.let { (checkTerm(it, vType) with this).second }
                val vUpper = upper?.let { normalizer.evalTerm(it) }
                put(bind(parameter.id, Entry(parameter.relevant, parameter.name, vLower, vUpper, vType, stage))) / {
                    C.Parameter(parameter.relevant, parameter.name, lower, upper, type, parameter.id)
                }
            }
        }

    /**
     * Infers the type of the [module] under the context.
     */
    private fun inferModule(module: S.Module): State<Context, Pair<C.Module, C.VModule>> = when (module) {
        is S.Module.Var -> when (val item = items[module.name]) {
            is C.Item.Mod -> pure(C.Module.Var(module.name, module.id) to item.type)
            else -> {
                diagnose(Diagnostic.ModNotFound(module.name, module.id))
                pure(C.Module.Var(module.name, module.id) to TODO())
            }
        }
        is S.Module.Str -> {
            val (items, signatures) = module.items.map { inferItem(it) }.unzip()
            pure(C.Module.Str(items, module.id) to C.VModule.Sig(signatures, null))
        }
        is S.Module.Sig -> module.signatures.mapM<Context, S.Signature, C.Signature> { elaborateSignature(it) } / { signatures ->
            C.Module.Sig(signatures, module.id) to C.VModule.Type(null)
        }
        is S.Module.Type -> pure(C.Module.Type(module.id) to C.VModule.Type(null))
    }

    /**
     * Checks the [module] against the [type] under the context.
     */
    private fun checkModule(module: S.Module, type: C.VModule): State<Context, C.Module> = when {
        module is S.Module.Str && type is C.VModule.Sig -> {
            val items = (module.items zip type.signatures).map { (item, signature) -> checkItem(item, signature) }
            pure(C.Module.Str(items, module.id))
        }
        else -> inferModule(module) / { (inferred, inferredType) ->
            if (!normalizer.unifyModules(inferredType, type)) {
                diagnose(Diagnostic.ModuleMismatch(module.id))
            }
            inferred
        }
    }

    /**
     * Checks if the [module1] and the [module2] can be unified under this normalizer.
     */
    private fun Normalizer.unifyModules(module1: C.VModule, module2: C.VModule): Boolean = when (module1) {
        is C.VModule.Var -> module2 is C.VModule.Var && module1.name == module2.name
        is C.VModule.Sig -> module2 is C.VModule.Sig && (module1.signatures zip module2.signatures).all { (signature1, signature2) -> unifySignatures(signature1, signature2) }
        is C.VModule.Str -> false // TODO
        is C.VModule.Type -> module2 is C.VModule.Type
    }

    /**
     * Checks if the [signature1] and the [signature2] can be unified under this normalizer.
     */
    private fun Normalizer.unifySignatures(signature1: C.VSignature, signature2: C.VSignature): Boolean = when (signature1) {
        is C.VSignature.Def -> signature2 is C.VSignature.Def && signature1.name == signature2.name && signature1.parameters.size == signature2.parameters.size && run {
            val (normalizer, success) = (signature1.parameters zip signature2.parameters).foldAll(this) { normalizer, (parameter1, parameter2) ->
                normalizer.bind(lazyOf(C.VTerm.Var("", normalizer.size))) to (unifyTerms(normalizer.evalTerm(parameter2.type), normalizer.evalTerm(parameter1.type)) with normalizer).second
            }
            success && (unifyTerms(normalizer.evalTerm(signature1.resultant), normalizer.evalTerm(signature2.resultant)) with normalizer).second
        }
        is C.VSignature.Mod -> signature2 is C.VSignature.Mod && signature1.name == signature2.name && unifyModules(signature1.type, signature2.type)
        is C.VSignature.Test -> signature2 is C.VSignature.Test && signature1.name == signature2.name
    }

    /**
     * Elaborates the [signature] under the context.
     */
    private fun elaborateSignature(signature: S.Signature): State<Context, C.Signature> = when (signature) {
        is S.Signature.Def ->
            scope {
                modify<Context> { irrelevant() } % {
                    bindParameters(signature.parameters) % { parameters ->
                        checkTerm(signature.resultant, TYPE) / { resultant ->
                            C.Signature.Def(signature.name, parameters, resultant, signature.id)
                        }
                    }
                }
            }
        is S.Signature.Mod -> checkModule(signature.type, C.VModule.Type(null)) / { type ->
            C.Signature.Mod(signature.name, type, signature.id)
        }
        is S.Signature.Test -> pure(C.Signature.Test(signature.name, signature.id))
    }

    /**
     * Infers the type of the [term] under the context.
     */
    private fun inferTerm(term: S.Term): State<Context, Typing> = when (term) {
        is S.Term.Hole ->
            gets {
                completions[term.id] = entries.map { it.name to it.type }
                Typing(C.Term.Hole(term.id), diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quoteTerm(END)), term.id)))
            }
        is S.Term.Meta ->
            gets<Context, C.VTerm> {
                normalizer.fresh(term.id)
            } % { type ->
                checkTerm(term, type) / { term ->
                    Typing(term, type)
                }
            }
        is S.Term.Anno ->
            modify<Context> {
                irrelevant()
            } % {
                checkTerm(term.type, TYPE) % { type ->
                    val type = normalizer.evalTerm(type)
                    checkTerm(term.element, type) / { term ->
                        Typing(term, type)
                    }
                }
            }
        is S.Term.Var ->
            gets {
                val level = lookup(term.name)
                val type = when (level) {
                    -1 -> diagnose(Diagnostic.VarNotFound(term.name, term.id))
                    else -> {
                        val entry = entries[level]
                        var type = entry.type
                        if (stage != entry.stage) type = diagnose(Diagnostic.StageMismatch(stage, entry.stage, term.id))
                        if (relevant && !entry.relevant) type = diagnose(Diagnostic.RelevanceMismatch(term.id))
                        normalizer.checkRepresentation(term.id, type)
                        type
                    }
                }
                Typing(C.Term.Var(term.name, level, term.id), type)
            }
        is S.Term.UnitOf -> pure(Typing(C.Term.UnitOf(term.id), UNIT))
        is S.Term.BoolOf -> pure(Typing(C.Term.BoolOf(term.value, term.id), BOOL))
        is S.Term.ByteOf -> pure(Typing(C.Term.ByteOf(term.value, term.id), BYTE))
        is S.Term.ShortOf -> pure(Typing(C.Term.ShortOf(term.value, term.id), SHORT))
        is S.Term.IntOf -> pure(Typing(C.Term.IntOf(term.value, term.id), INT))
        is S.Term.LongOf -> pure(Typing(C.Term.LongOf(term.value, term.id), LONG))
        is S.Term.FloatOf -> pure(Typing(C.Term.FloatOf(term.value, term.id), FLOAT))
        is S.Term.DoubleOf -> pure(Typing(C.Term.DoubleOf(term.value, term.id), DOUBLE))
        is S.Term.StringOf -> pure(Typing(C.Term.StringOf(term.value, term.id), STRING))
        is S.Term.ByteArrayOf ->
            term.elements.mapM<Context, S.Term, C.Term> { checkTerm(it, BYTE) } / { elements ->
                Typing(C.Term.ByteArrayOf(elements, term.id), BYTE_ARRAY)
            }
        is S.Term.IntArrayOf ->
            term.elements.mapM<Context, S.Term, C.Term> { checkTerm(it, INT) } / { elements ->
                Typing(C.Term.IntArrayOf(elements, term.id), INT_ARRAY)
            }
        is S.Term.LongArrayOf ->
            term.elements.mapM<Context, S.Term, C.Term> { checkTerm(it, LONG) } / { elements ->
                Typing(C.Term.LongArrayOf(elements, term.id), LONG_ARRAY)
            }
        is S.Term.ListOf -> if (term.elements.isEmpty()) {
            pure(Typing(C.Term.ListOf(emptyList(), term.id), C.VTerm.List(lazyOf(END), lazyOf(C.VTerm.IntOf(0)))))
        } else { // TODO: use union of element types
            inferTerm(term.elements.first()) % { head ->
                term.elements.drop(1).mapM<Context, S.Term, C.Term> { checkTerm(it, head.type) } / { tail ->
                    val elements = listOf(head.term) + tail
                    val size = C.VTerm.IntOf(elements.size)
                    Typing(C.Term.ListOf(elements, term.id), C.VTerm.List(lazyOf(head.type), lazyOf(size)))
                }
            }
        }
        is S.Term.CompoundOf ->
            term.elements.mapM<Context, Pair<Name, S.Term>, Pair<Name, Typing>> { (name, element) ->
                inferTerm(element) / { element ->
                    name to element
                }
            } / { elements ->
                Typing(
                    C.Term.CompoundOf(elements.map { (name, element) -> name to element.term }.toLinkedHashMap(), term.id),
                    C.VTerm.Compound(elements.map { (name, element) -> name to C.Entry(true, normalizer.quoteTerm(element.type), null) }.toLinkedHashMap())
                )
            }
        is S.Term.BoxOf ->
            checkTerm(term.tag, TYPE) % { tTag ->
                val vTag = normalizer.evalTerm(tTag)
                checkTerm(term.content, vTag) / { content ->
                    Typing(C.Term.BoxOf(content, tTag, term.id), C.VTerm.Box(lazyOf(vTag)))
                }
            }
        is S.Term.RefOf ->
            inferTerm(term.element) / { element ->
                Typing(C.Term.RefOf(element.term, term.id), C.VTerm.Ref(lazyOf(element.type)))
            }
        is S.Term.Refl ->
            gets {
                val left = lazy { normalizer.fresh(term.id) }
                Typing(C.Term.Refl(term.id), C.VTerm.Eq(left, left))
            }
        is S.Term.CodeOf ->
            modify<Context> { up() } % {
                inferTerm(term.element) / { element ->
                    Typing(C.Term.CodeOf(element.term, term.id), C.VTerm.Code(lazyOf(element.type)))
                }
            }
        is S.Term.Splice ->
            modify<Context> { down() } % {
                inferTerm(term.element) / { element ->
                    val type = when (val type = normalizer.force(element.type)) {
                        is C.VTerm.Code -> type.element.value
                        else -> type.also {
                            unifyTerms(it, C.VTerm.Code(lazyOf(normalizer.fresh(freshId())))) with normalizer
                        }
                    }
                    Typing(C.Term.Splice(element.term, term.id), type)
                }
            }
        is S.Term.Or ->
            term.variants.mapM<Context, S.Term, C.Term> { checkTerm(it, TYPE) } / { variants ->
                Typing(C.Term.Or(variants, term.id), TYPE)
            }
        is S.Term.And ->
            term.variants.mapM<Context, S.Term, C.Term> { checkTerm(it, TYPE) } / { variants ->
                Typing(C.Term.And(variants, term.id), TYPE)
            }
        is S.Term.Unit -> pure(Typing(C.Term.Unit(term.id), TYPE))
        is S.Term.Bool -> pure(Typing(C.Term.Bool(term.id), TYPE))
        is S.Term.Byte -> pure(Typing(C.Term.Byte(term.id), TYPE))
        is S.Term.Short -> pure(Typing(C.Term.Short(term.id), TYPE))
        is S.Term.Int -> pure(Typing(C.Term.Int(term.id), TYPE))
        is S.Term.Long -> pure(Typing(C.Term.Long(term.id), TYPE))
        is S.Term.Float -> pure(Typing(C.Term.Float(term.id), TYPE))
        is S.Term.Double -> pure(Typing(C.Term.Double(term.id), TYPE))
        is S.Term.String -> pure(Typing(C.Term.String(term.id), TYPE))
        is S.Term.ByteArray -> pure(Typing(C.Term.ByteArray(term.id), TYPE))
        is S.Term.IntArray -> pure(Typing(C.Term.IntArray(term.id), TYPE))
        is S.Term.LongArray -> pure(Typing(C.Term.LongArray(term.id), TYPE))
        is S.Term.List ->
            checkTerm(term.element, TYPE) % { element ->
                put(irrelevant()) % {
                    checkTerm(term.size, INT) / { size ->
                        Typing(C.Term.List(element, size, term.id), TYPE)
                    }
                }
            }
        is S.Term.Compound ->
            scope {
                term.elements.mapM<Context, S.Entry, Pair<Name, C.Entry>> { entry ->
                    checkTerm(entry.type, TYPE) % { element ->
                        put(bind(entry.id, Entry(false, entry.name.text, END, ANY, normalizer.evalTerm(element), stage))) / {
                            entry.name to C.Entry(entry.relevant, element, entry.id)
                        }
                    }
                } / { elements ->
                    Typing(C.Term.Compound(elements.toLinkedHashMap(), term.id), TYPE)
                }
            }
        is S.Term.Box ->
            checkTerm(term.content, TYPE) / { content ->
                Typing(C.Term.Box(content, term.id), TYPE)
            }
        is S.Term.Ref ->
            checkTerm(term.element, TYPE) / { element ->
                Typing(C.Term.Ref(element, term.id), TYPE)
            }
        is S.Term.Eq ->
            inferTerm(term.left) % { left ->
                checkTerm(term.right, left.type) / { right ->
                    Typing(C.Term.Eq(left.term, right, term.id), TYPE)
                }
            }
        is S.Term.Code ->
            checkTerm(term.element, TYPE) / { element ->
                Typing(C.Term.Code(element, term.id), TYPE)
            }
        is S.Term.Type -> pure(Typing(C.Term.Type(term.id), TYPE))
        else ->
            inferComputation(term) / { computation ->
                if (computation.effects.isNotEmpty()) {
                    diagnose(Diagnostic.EffectMismatch(emptyList(), computation.effects.map { serializeEffect(it) }, term.id))
                }
                computation
            }
    } / {
        types[term.id] = it.type
        it
    }

    /**
     * Infers the type of the [computation] under the context.
     */
    private fun inferComputation(computation: S.Term): State<Context, Typing> = when (computation) {
        is S.Term.Def -> when (val item = items[computation.name]) {
            is C.Item.Def -> {
                if (item.parameters.size != computation.arguments.size) {
                    diagnose(Diagnostic.SizeMismatch(item.parameters.size, computation.arguments.size, computation.id))
                }
                scope s@{
                    (computation.arguments zip item.parameters).mapM<Context, Pair<S.Term, C.Parameter>, C.Term> { (argument, parameter) ->
                        val (_, tArgument) = checkTerm(argument, normalizer.evalTerm(parameter.type)) with this@s
                        val vArgument = normalizer.evalTerm(tArgument)
                        val lower = parameter.lower?.let { normalizer.evalTerm(it) }?.also { lower ->
                            if (!(subtypeTerms(lower, vArgument) with this).second) diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(vArgument)), serializeTerm(normalizer.quoteTerm(lower)), argument.id))
                        }
                        val upper = parameter.upper?.let { normalizer.evalTerm(it) }?.also { upper ->
                            if (!(subtypeTerms(vArgument, upper) with this).second) diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(upper)), serializeTerm(normalizer.quoteTerm(vArgument)), argument.id))
                        }
                        val type = normalizer.evalTerm(parameter.type)
                        put(bind(argument.id, Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument)) / {
                            tArgument
                        }
                    } / { arguments ->
                        val resultant = normalizer.evalTerm(item.resultant)
                        Typing(C.Term.Def(computation.name, arguments, computation.id), resultant, item.effects)
                    }
                }
            }
            else -> pure(Typing(C.Term.Def(computation.name, emptyList() /* TODO? */, computation.id), diagnose(Diagnostic.DefNotFound(computation.name, computation.id))))
        }
        is S.Term.Let ->
            scope {
                inferComputation(computation.init) % { init ->
                    put(bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, init.type, stage))) % {
                        inferComputation(computation.body) / { body ->
                            Typing(C.Term.Let(computation.name, init.term, body.term, computation.id), body.type, init.effects + body.effects)
                        }
                    }
                }
            }
        is S.Term.Match ->
            gets<Context, C.VTerm> {
                normalizer.fresh(computation.id) // TODO: use union of element types
            } % { type ->
                inferTerm(computation.scrutinee) % { scrutinee ->
                    computation.clauses.mapM<Context, Pair<S.Pattern, S.Term>, Pair<C.Pattern, C.Term>> { (pattern, body) ->
                        scope {
                            checkPattern(pattern, scrutinee.type) % { pattern ->
                                checkTerm(body, type) /* TODO: effect */ / { body ->
                                    pattern to body
                                }
                            }
                        }
                    } / { clauses ->
                        Typing(C.Term.Match(scrutinee.term, clauses, computation.id), type)
                    }
                }
            }
        is S.Term.FunOf ->
            scope {
                gets<Context, List<C.VTerm>> {
                    computation.parameters.map { normalizer.fresh(computation.id) }
                } % { types ->
                    (computation.parameters zip types).forEachM<Context, Pair<Name, C.VTerm>> { (parameter, type) ->
                        put(bind(parameter.id, Entry(true /* TODO */, parameter.text, END, ANY, type, stage)))
                    } % {
                        inferComputation(computation.body) / { body ->
                            val parameters =
                                (computation.parameters zip types).map { (parameter, type) -> C.Parameter(true /* TODO */, parameter.text, normalizer.quoteTerm(END), normalizer.quoteTerm(ANY), normalizer.quoteTerm(type), parameter.id) }
                            Typing(C.Term.FunOf(computation.parameters, body.term, computation.id), C.VTerm.Fun(parameters, normalizer.quoteTerm(body.type), body.effects))
                        }
                    }
                }
            }
        is S.Term.Apply ->
            scope {
                inferComputation(computation.function) % { function ->
                    val type = when (val type = normalizer.force(function.type)) {
                        is C.VTerm.Fun -> type
                        else -> {
                            val parameters = computation.arguments.map {
                                C.Parameter(true, "", null, null, normalizer.quoteTerm(normalizer.fresh(freshId())), freshId())
                            }
                            val vResultant = normalizer.fresh(freshId())
                            val resultant = normalizer.quoteTerm(vResultant)
                            val effects = emptySet<C.Effect>() // TODO
                            unifyTerms(type, C.VTerm.Fun(parameters, resultant, effects, null)) with normalizer
                            C.VTerm.Fun(parameters, resultant, effects, type.id)
                        }
                    }
                    if (type.parameters.size != computation.arguments.size) {
                        diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.arguments.size, computation.id))
                    }
                    (computation.arguments zip type.parameters).mapM<Context, Pair<S.Term, C.Parameter>, C.Term> { (argument, parameter) ->
                        checkTerm(argument, normalizer.evalTerm(parameter.type)) % { tArgument ->
                            val vArgument = normalizer.evalTerm(tArgument)
                            val lower = parameter.lower?.let { normalizer.evalTerm(it) }?.also { lower ->
                                if (!(subtypeTerms(lower, vArgument) with this).second) diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(vArgument)), serializeTerm(normalizer.quoteTerm(lower)), argument.id))
                            }
                            val upper = parameter.upper?.let { normalizer.evalTerm(it) }?.also { upper ->
                                if (!(subtypeTerms(vArgument, upper) with this).second) diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(upper)), serializeTerm(normalizer.quoteTerm(vArgument)), argument.id))
                            }
                            val type = normalizer.evalTerm(parameter.type)
                            put(bind(argument.id, Entry(parameter.relevant, parameter.name, lower, upper, type, stage), vArgument)) / {
                                tArgument
                            }
                        }
                    } / { arguments ->
                        val resultant = normalizer.evalTerm(type.resultant)
                        Typing(C.Term.Apply(function.term, arguments, computation.id), resultant, type.effects)
                    }
                }
            }
        is S.Term.Fun ->
            scope {
                bindParameters(computation.parameters) % { parameters ->
                    checkTerm(computation.resultant, TYPE) /* TODO: check effects */ / { resultant ->
                        val effects = computation.effects.map { elaborateEffect(it) }.toSet()
                        Typing(C.Term.Fun(parameters, resultant, effects, computation.id), TYPE)
                    }
                }
            }
        else -> inferTerm(computation)
    }

    /**
     * Checks the [term] against the [type] under the context.
     */
    private fun checkTerm(term: S.Term, type: C.VTerm): State<Context, C.Term> =
        gets<Context, C.VTerm> {
            normalizer.force(type).also {
                types[term.id] = it
            }
        } % { type ->
            when {
                term is S.Term.Hole -> {
                    completions[term.id] = entries.map { it.name to it.type }
                    diagnose(Diagnostic.TermExpected(serializeTerm(normalizer.quoteTerm(type)), term.id))
                    pure(C.Term.Hole(term.id))
                }
                term is S.Term.Meta -> pure(normalizer.quoteTerm(normalizer.fresh(term.id)))
                term is S.Term.ListOf && type is C.VTerm.List ->
                    term.elements.mapM<Context, S.Term, C.Term> { checkTerm(it, type.element.value) } / { elements ->
                        when (val size = normalizer.force(type.size.value)) {
                            is C.VTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, term.id))
                            else -> {}
                        }
                        C.Term.ListOf(elements, term.id)
                    }
                term is S.Term.CompoundOf && type is C.VTerm.Compound ->
                    scope {
                        (term.elements zip type.elements.values).mapM<Context, Pair<Pair<Name, S.Term>, C.Entry>, Pair<Name, C.Term>> { (element, entry) ->
                            val vType = normalizer.evalTerm(entry.type)
                            checkTerm(element.second, vType) % { tElement ->
                                val vElement = normalizer.evalTerm(tElement)
                                put(bind(element.first.id, Entry(entry.relevant, element.first.text, END, ANY, vType, stage), vElement)) / {
                                    element.first to tElement
                                }
                            }
                        } / { elements ->
                            C.Term.CompoundOf(elements.toLinkedHashMap(), term.id)
                        }
                    }
                term is S.Term.RefOf && type is C.VTerm.Ref ->
                    checkTerm(term.element, type.element.value) / { element ->
                        C.Term.RefOf(element, term.id)
                    }
                term is S.Term.CodeOf && type is C.VTerm.Code ->
                    put(up()) % {
                        checkTerm(term.element, type.element.value) / { element ->
                            C.Term.CodeOf(element, term.id)
                        }
                    }
                else ->
                    checkComputation(term, type, emptySet()) / {
                        it.term
                    }
            }
        }

    /**
     * Checks the [computation] against the [type] and the [effects] under the context.
     */
    private fun checkComputation(computation: S.Term, type: C.VTerm, effects: Set<C.Effect>): State<Context, Effecting> =
        gets<Context, C.VTerm> {
            normalizer.force(type).also {
                types[computation.id] = it
            }
        } % { type ->
            when {
                computation is S.Term.Let ->
                    scope {
                        inferComputation(computation.init) % { init ->
                            put(bind(computation.init.id, Entry(true /* TODO */, computation.name, END, ANY, init.type, stage))) % {
                                checkComputation(computation.body, type, effects) / { body ->
                                    val effects = init.effects + body.effects
                                    Effecting(C.Term.Let(computation.name, init.term, body.term, computation.id), effects)
                                }
                            }
                        }
                    }
                computation is S.Term.Match ->
                    inferTerm(computation.scrutinee) % { scrutinee ->
                        computation.clauses.mapM<Context, Pair<S.Pattern, S.Term>, Pair<C.Pattern, Effecting>> { (pattern, body) ->
                            scope {
                                checkPattern(pattern, scrutinee.type) % { pattern ->
                                    checkComputation(body, type, effects) / { body ->
                                        pattern to body
                                    }
                                }
                            }
                        } / { clauses ->
                            val effects = clauses.flatMap { (_, body) -> body.effects }.toSet()
                            Effecting(C.Term.Match(scrutinee.term, clauses.map { (pattern, body) -> pattern to body.term }, computation.id), effects)
                        }
                    }
                computation is S.Term.FunOf && type is C.VTerm.Fun -> {
                    if (type.parameters.size != computation.parameters.size) {
                        diagnose(Diagnostic.SizeMismatch(type.parameters.size, computation.parameters.size, computation.id))
                    }
                    scope {
                        put(empty()) % {
                            (computation.parameters zip type.parameters).forEachM<Context, Pair<Name, C.Parameter>> { (name, parameter) ->
                                val lower = parameter.lower?.let { normalizer.evalTerm(it) }
                                val upper = parameter.upper?.let { normalizer.evalTerm(it) }
                                val type = normalizer.evalTerm(parameter.type)
                                put(bind(name.id, Entry(parameter.relevant, name.text, lower, upper, type, stage)))
                            } % {
                                checkComputation(computation.body, normalizer.evalTerm(type.resultant), effects) / { resultant ->
                                    Effecting(C.Term.FunOf(computation.parameters, resultant.term, computation.id), emptySet())
                                }
                            }
                        }
                    }
                }
                else -> {
                    val id = computation.id
                    inferComputation(computation) % { computation ->
                        subtypeTerms(computation.type, type) / { isSubtype ->
                            if (!isSubtype) {
                                types[id] = END
                                diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(type)), serializeTerm(normalizer.quoteTerm(computation.type)), id))
                            }
                            if (!(effects.containsAll(computation.effects))) {
                                diagnose(Diagnostic.EffectMismatch(effects.map { serializeEffect(it) }, computation.effects.map { serializeEffect(it) }, id))
                            }
                            Effecting(computation.term, computation.effects)
                        }
                    }
                }
            }
        }

    /**
     * Checks if the [term1] and the [term2] can be unified under the normalizer.
     */
    private fun unifyTerms(term1: C.VTerm, term2: C.VTerm): State<Normalizer, Boolean> =
        scope {
            gets<Normalizer, Pair<C.VTerm, C.VTerm>> {
                force(term1) to force(term2)
            } % { (value1, value2) ->
                when {
                    value1.id != null && value2.id != null && value1.id == value2.id -> pure(true)
                    value1 is C.VTerm.Meta && value2 is C.VTerm.Meta && value1.index == value2.index -> pure(true)
                    value1 is C.VTerm.Meta -> when (val solved1 = getSolution(value1.index)) {
                        null -> {
                            solve(value1.index, value2)
                            pure(true)
                        }
                        else -> unifyTerms(solved1, value2)
                    }
                    value2 is C.VTerm.Meta -> unifyTerms(value2, value1)
                    value1 is C.VTerm.Var && value2 is C.VTerm.Var -> pure(value1.level == value2.level)
                    value1 is C.VTerm.Def && value2 is C.VTerm.Def && value1.name == value2.name -> pure(true)
                    value1 is C.VTerm.Match && value2 is C.VTerm.Match -> pure(false) // TODO
                    value1 is C.VTerm.UnitOf && value2 is C.VTerm.UnitOf -> pure(true)
                    value1 is C.VTerm.BoolOf && value2 is C.VTerm.BoolOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.ByteOf && value2 is C.VTerm.ByteOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.ShortOf && value2 is C.VTerm.ShortOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.IntOf && value2 is C.VTerm.IntOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.LongOf && value2 is C.VTerm.LongOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.FloatOf && value2 is C.VTerm.FloatOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.DoubleOf && value2 is C.VTerm.DoubleOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.StringOf && value2 is C.VTerm.StringOf -> pure(value1.value == value2.value)
                    value1 is C.VTerm.ByteArrayOf && value2 is C.VTerm.ByteArrayOf ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
                    value1 is C.VTerm.IntArrayOf && value2 is C.VTerm.IntArrayOf ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
                    value1 is C.VTerm.LongArrayOf && value2 is C.VTerm.LongArrayOf ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
                    value1 is C.VTerm.ListOf && value2 is C.VTerm.ListOf ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements zip value2.elements).allM { unifyTerms(it.first.value, it.second.value) }
                    value1 is C.VTerm.CompoundOf && value2 is C.VTerm.CompoundOf ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements.entries zip value2.elements.entries).allM { (entry1, entry2) ->
                                    pure<Normalizer, Boolean>(entry1.key.text == entry2.key.text) andM
                                            unifyTerms(entry1.value.value, entry2.value.value)
                                }
                    value1 is C.VTerm.BoxOf && value2 is C.VTerm.BoxOf ->
                        unifyTerms(value1.content.value, value2.content.value) andM
                                unifyTerms(value1.tag.value, value2.tag.value)
                    value1 is C.VTerm.RefOf && value2 is C.VTerm.RefOf -> unifyTerms(value1.element.value, value2.element.value)
                    value1 is C.VTerm.Refl && value2 is C.VTerm.Refl -> pure(true)
                    value1 is C.VTerm.FunOf && value2 is C.VTerm.FunOf ->
                        pure<Normalizer, Boolean>(value1.parameters.size == value2.parameters.size) andM
                                value1.parameters.forEachM<Normalizer, Name> { parameter ->
                                    put(bind(lazyOf(C.VTerm.Var(parameter.text, size))))
                                } % { unifyTerms(evalTerm(value1.body), evalTerm(value2.body)) }
                    value1 is C.VTerm.Apply && value2 is C.VTerm.Apply ->
                        unifyTerms(value1.function, value2.function) andM
                                (value1.arguments zip value2.arguments).allM { unifyTerms(it.first.value, it.second.value) }
                    value1 is C.VTerm.CodeOf && value2 is C.VTerm.CodeOf -> unifyTerms(value1.element.value, value2.element.value)
                    value1 is C.VTerm.Splice && value2 is C.VTerm.Splice -> unifyTerms(value1.element.value, value2.element.value)
                    value1 is C.VTerm.Or && value1.variants.isEmpty() && value2 is C.VTerm.Or && value2.variants.isEmpty() -> pure(true)
                    value1 is C.VTerm.And && value1.variants.isEmpty() && value2 is C.VTerm.And && value2.variants.isEmpty() -> pure(true)
                    value1 is C.VTerm.Unit && value2 is C.VTerm.Unit -> pure(true)
                    value1 is C.VTerm.Bool && value2 is C.VTerm.Bool -> pure(true)
                    value1 is C.VTerm.Byte && value2 is C.VTerm.Byte -> pure(true)
                    value1 is C.VTerm.Short && value2 is C.VTerm.Short -> pure(true)
                    value1 is C.VTerm.Int && value2 is C.VTerm.Int -> pure(true)
                    value1 is C.VTerm.Long && value2 is C.VTerm.Long -> pure(true)
                    value1 is C.VTerm.Float && value2 is C.VTerm.Float -> pure(true)
                    value1 is C.VTerm.Double && value2 is C.VTerm.Double -> pure(true)
                    value1 is C.VTerm.String && value2 is C.VTerm.String -> pure(true)
                    value1 is C.VTerm.ByteArray && value2 is C.VTerm.ByteArray -> pure(true)
                    value1 is C.VTerm.IntArray && value2 is C.VTerm.IntArray -> pure(true)
                    value1 is C.VTerm.LongArray && value2 is C.VTerm.LongArray -> pure(true)
                    value1 is C.VTerm.List && value2 is C.VTerm.List ->
                        unifyTerms(value1.element.value, value2.element.value) andM
                                unifyTerms(value1.size.value, value2.size.value)
                    value1 is C.VTerm.Compound && value2 is C.VTerm.Compound ->
                        pure<Normalizer, Boolean>(value1.elements.size == value2.elements.size) andM
                                (value1.elements.entries zip value2.elements.entries).allM { (entry1, entry2) ->
                                    put(bind(lazyOf(C.VTerm.Var(entry1.key.text, size)))) % {
                                        pure<Normalizer, Boolean>(entry1.key.text == entry2.key.text) andM
                                                pure(entry1.value.relevant == entry2.value.relevant) andM
                                                unifyTerms(evalTerm(entry1.value.type), evalTerm(entry2.value.type))
                                    }
                                }
                    value1 is C.VTerm.Box && value2 is C.VTerm.Box -> unifyTerms(value1.content.value, value2.content.value)
                    value1 is C.VTerm.Ref && value2 is C.VTerm.Ref -> unifyTerms(value1.element.value, value2.element.value)
                    value1 is C.VTerm.Eq && value2 is C.VTerm.Eq ->
                        unifyTerms(value1.left.value, value2.left.value) andM
                                unifyTerms(value1.right.value, value2.right.value)
                    value1 is C.VTerm.Fun && value2 is C.VTerm.Fun ->
                        pure<Normalizer, Boolean>(value1.parameters.size == value2.parameters.size) andM
                                (value1.parameters zip value2.parameters).allM { (parameter1, parameter2) ->
                                    put(bind(lazyOf(C.VTerm.Var("", size)))) % {
                                        unifyTerms(evalTerm(parameter2.type), evalTerm(parameter1.type))
                                    }
                                } andM unifyTerms(evalTerm(value1.resultant), evalTerm(value2.resultant)) andM pure(value1.effects == value2.effects)
                    value1 is C.VTerm.Code && value2 is C.VTerm.Code -> unifyTerms(value1.element.value, value2.element.value)
                    value1 is C.VTerm.Type && value2 is C.VTerm.Type -> pure(true)
                    else -> pure(false)
                }
            }
        }

    /**
     * Checks if the [term1] is a subtype of the [term2] under the context.
     */
    private fun subtypeTerms(term1: C.VTerm, term2: C.VTerm): State<Context, Boolean> =
        scope {
            gets<Context, Pair<C.VTerm, C.VTerm>> {
                normalizer.force(term1) to normalizer.force(term2)
            } % { (term1, term2) ->
                when {
                    term1 is C.VTerm.Var && term2 is C.VTerm.Var && term1.level == term2.level -> pure(true)
                    term1 is C.VTerm.Var && entries[term1.level].upper != null -> subtypeTerms(entries[term1.level].upper!!, term2)
                    term2 is C.VTerm.Var && entries[term2.level].lower != null -> subtypeTerms(term1, entries[term2.level].lower!!)
                    term1 is C.VTerm.Apply && term2 is C.VTerm.Apply ->
                        subtypeTerms(term1.function, term2.function) andM
                                (term1.arguments zip term2.arguments).allM { (argument1, argument2) ->
                                    unifyTerms(argument1.value, argument2.value).lift(normalizer) { withNormalizer(it) } // pointwise subtyping
                                }
                    term1 is C.VTerm.Or -> term1.variants.allM { subtypeTerms(it.value, term2) }
                    term2 is C.VTerm.Or -> term2.variants.anyM { subtypeTerms(term1, it.value) }
                    term2 is C.VTerm.And -> term2.variants.allM { subtypeTerms(term1, it.value) }
                    term1 is C.VTerm.And -> term1.variants.anyM { subtypeTerms(it.value, term2) }
                    term1 is C.VTerm.List && term2 is C.VTerm.List ->
                        subtypeTerms(term1.element.value, term2.element.value) andM
                                unifyTerms(term1.size.value, term2.size.value).lift(normalizer) { withNormalizer(it) }
                    term1 is C.VTerm.Compound && term2 is C.VTerm.Compound ->
                        term2.elements.entries.allM { entry2 ->
                            val element1 = term1.elements[entry2.key]
                            if (element1 != null) {
                                pure<Context, Boolean>(element1.relevant == entry2.value.relevant) andM run {
                                    val upper1 = normalizer.evalTerm(element1.type)
                                    put(bindUnchecked(Entry(false, entry2.key.text, null, upper1, TYPE, stage))) % {
                                        subtypeTerms(upper1, normalizer.evalTerm(entry2.value.type))
                                    }
                                }
                            } else pure(false)
                        }
                    term1 is C.VTerm.Box && term2 is C.VTerm.Box -> subtypeTerms(term1.content.value, term2.content.value)
                    term1 is C.VTerm.Ref && term2 is C.VTerm.Ref -> subtypeTerms(term1.element.value, term2.element.value)
                    term1 is C.VTerm.Fun && term2 is C.VTerm.Fun ->
                        pure<Context, Boolean>(term1.parameters.size == term2.parameters.size) andM
                                (term1.parameters zip term2.parameters).allM { (parameter1, parameter2) ->
                                    put(bindUnchecked(Entry(parameter1.relevant, "", null, null /* TODO */, TYPE, stage))) % {
                                        pure<Context, Boolean>(parameter1.relevant == parameter2.relevant) andM
                                                subtypeTerms(normalizer.evalTerm(parameter2.type), normalizer.evalTerm(parameter1.type))
                                    }
                                } andM
                                subtypeTerms(normalizer.evalTerm(term1.resultant), normalizer.evalTerm(term2.resultant)) andM
                                pure(term2.effects.containsAll(term1.effects))
                    term1 is C.VTerm.Code && term2 is C.VTerm.Code -> subtypeTerms(term1.element.value, term2.element.value)
                    else -> unifyTerms(term1, term2).lift(normalizer) { withNormalizer(it) }
                }
            }
        }

    /**
     * Infers the type of the [pattern] under the context.
     */
    private fun inferPattern(pattern: S.Pattern): State<Context, Pair<C.Pattern, C.VTerm>> = when (pattern) {
        is S.Pattern.Var ->
            gets<Context, C.VTerm> {
                normalizer.fresh(pattern.id)
            } % { type ->
                put(bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, type, stage))) / {
                    C.Pattern.Var(pattern.name, pattern.id) to type
                }
            }
        is S.Pattern.UnitOf -> pure(C.Pattern.UnitOf(pattern.id) to UNIT)
        is S.Pattern.BoolOf -> pure(C.Pattern.BoolOf(pattern.value, pattern.id) to BOOL)
        is S.Pattern.ByteOf -> pure(C.Pattern.ByteOf(pattern.value, pattern.id) to BYTE)
        is S.Pattern.ShortOf -> pure(C.Pattern.ShortOf(pattern.value, pattern.id) to SHORT)
        is S.Pattern.IntOf -> pure(C.Pattern.IntOf(pattern.value, pattern.id) to INT)
        is S.Pattern.LongOf -> pure(C.Pattern.LongOf(pattern.value, pattern.id) to LONG)
        is S.Pattern.FloatOf -> pure(C.Pattern.FloatOf(pattern.value, pattern.id) to FLOAT)
        is S.Pattern.DoubleOf -> pure(C.Pattern.DoubleOf(pattern.value, pattern.id) to DOUBLE)
        is S.Pattern.StringOf -> pure(C.Pattern.StringOf(pattern.value, pattern.id) to STRING)
        is S.Pattern.ByteArrayOf ->
            pattern.elements.mapM<Context, S.Pattern, C.Pattern> { element -> checkPattern(element, BYTE) } / { elements ->
                C.Pattern.ByteArrayOf(elements, pattern.id) to BYTE_ARRAY
            }
        is S.Pattern.IntArrayOf ->
            pattern.elements.mapM<Context, S.Pattern, C.Pattern> { element -> checkPattern(element, INT) } / { elements ->
                C.Pattern.IntArrayOf(elements, pattern.id) to INT_ARRAY
            }
        is S.Pattern.LongArrayOf ->
            pattern.elements.mapM<Context, S.Pattern, C.Pattern> { element -> checkPattern(element, LONG) } / { elements ->
                C.Pattern.LongArrayOf(elements, pattern.id) to LONG_ARRAY
            }
        is S.Pattern.ListOf ->
            if (pattern.elements.isEmpty()) {
                pure(C.Pattern.ListOf(emptyList(), pattern.id) to END)
            } else { // TODO: use union of element types
                inferPattern(pattern.elements.first()) % { (head, headType) ->
                    pattern.elements.drop(1).mapM<Context, S.Pattern, C.Pattern> { element -> checkPattern(element, headType) } / { elements ->
                        val size = C.VTerm.IntOf(elements.size)
                        C.Pattern.ListOf(listOf(head) + elements, pattern.id) to C.VTerm.List(lazyOf(headType), lazyOf(size))
                    }
                }
            }
        is S.Pattern.CompoundOf ->
            pattern.elements.mapM<Context, Pair<Name, S.Pattern>, Triple<Name, C.Pattern, C.VTerm>> { (name, element) ->
                inferPattern(element) / { (element, elementType) ->
                    Triple(name, element, elementType)
                }
            } / { elements ->
                val elementTerms = elements.map { (name, element, _) -> name to element }.toLinkedHashMap()
                val elementTypes = elements.map { (name, _, type) -> name to C.Entry(true, normalizer.quoteTerm(type), null) }.toLinkedHashMap()
                C.Pattern.CompoundOf(elementTerms, pattern.id) to C.VTerm.Compound(elementTypes)
            }
        is S.Pattern.BoxOf ->
            checkPattern(pattern.tag, TYPE) % { tag ->
                val vTag = tag.toType() ?: normalizer.fresh(pattern.id)
                checkPattern(pattern.content, vTag) / { content ->
                    C.Pattern.BoxOf(content, tag, pattern.id) to C.VTerm.Box(lazyOf(vTag))
                }
            }
        is S.Pattern.RefOf ->
            inferPattern(pattern.element) / { (element, elementType) ->
                C.Pattern.RefOf(element, pattern.id) to C.VTerm.Ref(lazyOf(elementType))
            }
        is S.Pattern.Refl ->
            gets {
                val left = lazy { normalizer.fresh(pattern.id) }
                C.Pattern.Refl(pattern.id) to C.VTerm.Eq(left, left)
            }
        is S.Pattern.Unit -> pure(C.Pattern.Unit(pattern.id) to UNIT)
        is S.Pattern.Bool -> pure(C.Pattern.Bool(pattern.id) to TYPE)
        is S.Pattern.Byte -> pure(C.Pattern.Byte(pattern.id) to TYPE)
        is S.Pattern.Short -> pure(C.Pattern.Short(pattern.id) to TYPE)
        is S.Pattern.Int -> pure(C.Pattern.Int(pattern.id) to TYPE)
        is S.Pattern.Long -> pure(C.Pattern.Long(pattern.id) to TYPE)
        is S.Pattern.Float -> pure(C.Pattern.Float(pattern.id) to TYPE)
        is S.Pattern.Double -> pure(C.Pattern.Double(pattern.id) to TYPE)
        is S.Pattern.String -> pure(C.Pattern.String(pattern.id) to TYPE)
        is S.Pattern.ByteArray -> pure(C.Pattern.ByteArray(pattern.id) to TYPE)
        is S.Pattern.IntArray -> pure(C.Pattern.IntArray(pattern.id) to TYPE)
        is S.Pattern.LongArray -> pure(C.Pattern.LongArray(pattern.id) to TYPE)
    } / {
        types[pattern.id] = it.second
        it
    }

    /**
     * Checks the [pattern] against the [type] under the context.
     */
    private fun checkPattern(pattern: S.Pattern, type: C.VTerm): State<Context, C.Pattern> =
        gets<Context, C.VTerm> {
            normalizer.force(type).also {
                types[pattern.id] = it
            }
        } % { type ->
            when {
                pattern is S.Pattern.Var ->
                    put(bind(pattern.id, Entry(true /* TODO */, pattern.name, END, ANY, type, stage))) / {
                        C.Pattern.Var(pattern.name, pattern.id)
                    }
                pattern is S.Pattern.ListOf && type is C.VTerm.List ->
                    pattern.elements.mapM<Context, S.Pattern, C.Pattern> { element ->
                        checkPattern(element, type.element.value)
                    } / { elements ->
                        when (val size = normalizer.force(type.size.value)) {
                            is C.VTerm.IntOf -> if (size.value != elements.size) diagnose(Diagnostic.SizeMismatch(size.value, elements.size, pattern.id))
                            else -> {}
                        }
                        C.Pattern.ListOf(elements, pattern.id)
                    }
                pattern is S.Pattern.CompoundOf && type is C.VTerm.Compound ->
                    (pattern.elements zip type.elements.entries).mapM<Context, Pair<Pair<Name, S.Pattern>, Map.Entry<Name, C.Entry>>, Pair<Name, C.Pattern>> { (element, entry) ->
                        val type = normalizer.evalTerm(entry.value.type)
                        checkPattern(element.second, type) / {
                            element.first to it
                        }
                    } / { elements ->
                        C.Pattern.CompoundOf(elements.toLinkedHashMap(), pattern.id)
                    }
                pattern is S.Pattern.BoxOf && type is C.VTerm.Box ->
                    checkPattern(pattern.tag, TYPE) % { tag ->
                        val vTag = tag.toType() ?: type.content.value
                        checkPattern(pattern.content, vTag) / { content ->
                            C.Pattern.BoxOf(content, tag, pattern.id)
                        }
                    }
                pattern is S.Pattern.RefOf && type is C.VTerm.Ref ->
                    checkPattern(pattern.element, type.element.value) / { element ->
                        C.Pattern.RefOf(element, pattern.id)
                    }
                pattern is S.Pattern.Refl && type is C.VTerm.Eq ->
                    gets<Context, Normalizer> {
                        match(type.left.value, type.right.value) with normalizer
                    } % { normalizer ->
                        put(withNormalizer(normalizer)) / {
                            C.Pattern.Refl(pattern.id)
                        }
                    }
                else ->
                    inferPattern(pattern) % { (inferred, inferredType) ->
                        subtypeTerms(inferredType, type) / { isSubtype ->
                            if (!isSubtype) {
                                types[pattern.id] = END
                                diagnose(Diagnostic.TermMismatch(serializeTerm(normalizer.quoteTerm(type)), serializeTerm(normalizer.quoteTerm(inferredType)), pattern.id))
                            }
                            inferred
                        }
                    }
            }
        }

    /**
     * Converts this pattern to a semantic term.
     */
    private fun C.Pattern.toType(): C.VTerm? = when (this) {
        is C.Pattern.Unit -> UNIT
        is C.Pattern.Bool -> BOOL
        is C.Pattern.Byte -> BYTE
        is C.Pattern.Short -> SHORT
        is C.Pattern.Int -> INT
        is C.Pattern.Long -> LONG
        is C.Pattern.Float -> FLOAT
        is C.Pattern.Double -> DOUBLE
        is C.Pattern.String -> STRING
        is C.Pattern.ByteArray -> BYTE_ARRAY
        is C.Pattern.IntArray -> INT_ARRAY
        is C.Pattern.LongArray -> LONG_ARRAY
        else -> null
    }

    /**
     * Matches the [term1] and the [term2].
     */
    private fun match(term1: C.VTerm, term2: C.VTerm): State<Normalizer, Unit> =
        gets<Normalizer, Pair<C.VTerm, C.VTerm>> {
            force(term1) to force(term2)
        } % { (term1, term2) ->
            when {
                term2 is C.VTerm.Var -> put(subst(term2.level, lazyOf(term1)))
                term1 is C.VTerm.Var -> put(subst(term1.level, lazyOf(term2)))
                term1 is C.VTerm.ByteArrayOf && term2 is C.VTerm.ByteArrayOf -> (term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
                term1 is C.VTerm.IntArrayOf && term2 is C.VTerm.IntArrayOf -> (term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
                term1 is C.VTerm.LongArrayOf && term2 is C.VTerm.LongArrayOf -> (term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
                term1 is C.VTerm.ListOf && term2 is C.VTerm.ListOf -> (term1.elements zip term2.elements).forEachM { (element1, element2) -> match(element1.value, element2.value) }
                term1 is C.VTerm.CompoundOf && term2 is C.VTerm.CompoundOf -> (term1.elements.entries zip term2.elements.entries).forEachM { (entry1, entry2) ->
                    if (entry1.key == entry2.key) match(entry1.value.value, entry2.value.value) else pure(Unit)
                }
                term1 is C.VTerm.BoxOf && term2 is C.VTerm.BoxOf -> match(term1.content.value, term2.content.value) % { match(term1.tag.value, term2.tag.value) }
                term1 is C.VTerm.RefOf && term2 is C.VTerm.RefOf -> match(term1.element.value, term2.element.value)
                else -> pure(Unit) // TODO
            }
        }

    /**
     * Elaborates the [effect].
     */
    private fun elaborateEffect(effect: S.Effect): C.Effect = when (effect) {
        is S.Effect.Name -> C.Effect.Name(effect.name)
    }

    /**
     * Ensures that the representation of the [type] is monomorphic under this normalizer.
     */
    private fun Normalizer.checkRepresentation(id: Id, type: C.VTerm) {
        fun join(term1: C.VTerm, term2: C.VTerm): Boolean {
            val term1 = force(term1)
            val term2 = force(term2)
            return when (term1) {
                is C.VTerm.Or -> term1.variants.all { join(it.value, term2) }
                is C.VTerm.And -> term1.variants.any { join(it.value, term2) }
                is C.VTerm.Unit -> term2 is C.VTerm.Unit
                is C.VTerm.Bool -> term2 is C.VTerm.Bool
                is C.VTerm.Byte -> term2 is C.VTerm.Byte
                is C.VTerm.Short -> term2 is C.VTerm.Short
                is C.VTerm.Int -> term2 is C.VTerm.Int
                is C.VTerm.Long -> term2 is C.VTerm.Long
                is C.VTerm.Float -> term2 is C.VTerm.Float
                is C.VTerm.Double -> term2 is C.VTerm.Double
                is C.VTerm.String -> term2 is C.VTerm.String
                is C.VTerm.ByteArray -> term2 is C.VTerm.ByteArray
                is C.VTerm.IntArray -> term2 is C.VTerm.IntArray
                is C.VTerm.LongArray -> term2 is C.VTerm.LongArray
                is C.VTerm.List -> term2 is C.VTerm.List
                is C.VTerm.Compound -> term2 is C.VTerm.Compound
                is C.VTerm.Box -> term2 is C.VTerm.Box
                is C.VTerm.Ref -> term2 is C.VTerm.Ref
                is C.VTerm.Eq -> term2 is C.VTerm.Eq
                is C.VTerm.Fun -> term2 is C.VTerm.Fun
                is C.VTerm.Type -> term2 is C.VTerm.Type
                else -> false
            }
        }

        when (type) {
            is C.VTerm.Var -> diagnose(Diagnostic.PolymorphicRepresentation(id))
            is C.VTerm.Or -> if (type.variants.size >= 2) {
                val first = type.variants.first().value
                if (!type.variants.drop(1).all { join(first, it.value) }) {
                    diagnose(Diagnostic.PolymorphicRepresentation(id))
                }
            }
            is C.VTerm.And -> if (type.variants.isEmpty()) {
                diagnose(Diagnostic.PolymorphicRepresentation(id))
            }
            else -> {}
        }
    }

    private fun diagnose(diagnostic: Diagnostic): C.VTerm {
        diagnostics += diagnostic
        return END
    }

    data class Result(
        val item: C.Item,
        val types: Map<Id, C.VTerm>,
        val normalizer: Normalizer,
        val diagnostics: List<Diagnostic>,
        val completions: Map<Id, List<Pair<String, C.VTerm>>>,
    )

    private data class Typing(
        val term: C.Term,
        val type: C.VTerm,
        val effects: Set<C.Effect> = emptySet(),
    )

    private data class Effecting(
        val term: C.Term,
        val effects: Set<C.Effect>,
    )

    private data class Entry(
        val relevant: Boolean,
        val name: String,
        val lower: C.VTerm?,
        val upper: C.VTerm?,
        val type: C.VTerm,
        val stage: Int,
    )

    private inner class Context(
        val entries: PersistentList<Entry>,
        val normalizer: Normalizer,
        val meta: Boolean,
        val stage: Int,
        val relevant: Boolean,
    ) {
        val size: Int get() = entries.size

        fun lookup(name: String): Int = entries.indexOfLast { it.name == name }

        fun bind(id: Id, entry: Entry, term: C.VTerm? = null): Context = bindUnchecked(entry, term).also { checkPhase(id, entry.type) }

        fun bindUnchecked(entry: Entry, term: C.VTerm? = null): Context = Context(entries + entry, normalizer.bind(lazyOf(term ?: C.VTerm.Var(entry.name, size))), meta, stage, relevant)

        fun empty(): Context = Context(persistentListOf(), normalizer.empty(), meta, stage, relevant)

        fun withNormalizer(normalizer: Normalizer): Context = Context(entries, normalizer, meta, stage, relevant)

        fun meta(meta: Boolean): Context = Context(entries, normalizer, meta, stage, relevant)

        fun up(): Context = Context(entries, normalizer, meta, stage + 1, relevant)

        fun down(): Context = Context(entries, normalizer, meta, stage - 1, relevant)

        fun relevant(): Context = if (relevant) this else Context(entries, normalizer, meta, stage, true)

        fun irrelevant(): Context = if (!relevant) this else Context(entries, normalizer, meta, stage, false)

        fun checkPhase(id: Id, type: C.VTerm) {
            if (!meta && type is C.VTerm.Code) diagnose(Diagnostic.PhaseMismatch(id))
        }
    }

    companion object {
        private val UNIT = C.VTerm.Unit()
        private val BOOL = C.VTerm.Bool()
        private val BYTE = C.VTerm.Byte()
        private val SHORT = C.VTerm.Short()
        private val INT = C.VTerm.Int()
        private val LONG = C.VTerm.Long()
        private val FLOAT = C.VTerm.Float()
        private val DOUBLE = C.VTerm.Double()
        private val STRING = C.VTerm.String()
        private val BYTE_ARRAY = C.VTerm.ByteArray()
        private val INT_ARRAY = C.VTerm.IntArray()
        private val LONG_ARRAY = C.VTerm.LongArray()
        private val END = C.VTerm.Or(emptyList())
        private val ANY = C.VTerm.And(emptyList())
        private val TYPE = C.VTerm.Type()

        operator fun invoke(item: S.Item, items: Map<String, C.Item>): Result = Elaborate(items).run {
            val (item, _) = inferItem(item)
            Result(item, types, Normalizer(persistentListOf(), items, solutions), diagnostics, completions)
        }
    }
}
