package mce.phase.backend.pack

import mce.phase.backend.defun.Defun
import mce.phase.backend.defun.Item
import mce.phase.backend.defun.Pat
import mce.phase.backend.defun.Term
import mce.phase.backend.pack.Command.*
import mce.phase.backend.pack.Command.Execute
import mce.phase.backend.pack.Consumer.RESULT
import mce.phase.backend.pack.Execute.Run
import mce.phase.backend.pack.Execute.StoreValue
import mce.phase.backend.pack.SourceComparator.*
import mce.phase.backend.pack.SourceProvider.From
import mce.phase.backend.pack.SourceProvider.Value
import mce.phase.backend.pack.Execute as E
import mce.phase.backend.pack.Function as PFunction
import mce.phase.frontend.elab.VTerm as Type

@Suppress("NAME_SHADOWING")
class Pack private constructor() {
    private val functions: MutableList<PFunction> = mutableListOf()

    private fun pack(terms: Map<Int, Term>, item: Item) {
        +Context(APPLY).apply {
            +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, INT[-1]))))
            +Pop(MAIN, INT)
            // TODO: use 4-ary search
            terms.forEach { (tag, term) ->
                val name = ResourceLocation("$tag")
                +Context(name).apply {
                    packTerm(term)
                    +SetScore(R0, REG, -1)
                }
                +Execute(E.CheckScore(true, R0, REG, EqConst(tag), Run(RunFunction(name))))
            }
        }

        packItem(item)
    }

    private fun packItem(item: Item) {
        when (item) {
            is Item.Def -> +Context(ResourceLocation(item.name)).apply { packTerm(item.body) }
            is Item.Mod -> TODO()
            is Item.Test -> TODO()
        }
    }

    private fun Context.packTerm(term: Term) {
        when (term) {
            is Term.Var -> {
                val type = eraseType(term.type)
                val path = type.toPath()
                val index = getIndex(type, term.name)
                +Append(MAIN, path, From(MAIN, path[index]))
            }
            is Term.Def -> {
                term.arguments.forEach { packTerm(it) }
                +RunFunction(ResourceLocation(term.name))
            }
            is Term.Let -> {
                val type = eraseType(term.init.type)
                packTerm(term.init)
                bind(type, term.name)
                packTerm(term.body)
                pop(type)
            }
            is Term.Match -> {
                packTerm(term.scrutinee)
                +RunFunction(packMatch(term.clauses))
            }
            is Term.UnitOf -> +Append(MAIN, BYTE, Value(Nbt.Byte(0)))
            is Term.BoolOf -> +Append(MAIN, BYTE, Value(Nbt.Byte(if (term.value) 1 else 0)))
            is Term.ByteOf -> +Append(MAIN, BYTE, Value(Nbt.Byte(term.value)))
            is Term.ShortOf -> +Append(MAIN, SHORT, Value(Nbt.Short(term.value)))
            is Term.IntOf -> +Append(MAIN, INT, Value(Nbt.Int(term.value)))
            is Term.LongOf -> +Append(MAIN, LONG, Value(Nbt.Long(term.value)))
            is Term.FloatOf -> +Append(MAIN, FLOAT, Value(Nbt.Float(term.value)))
            is Term.DoubleOf -> +Append(MAIN, DOUBLE, Value(Nbt.Double(term.value)))
            is Term.StringOf -> +Append(MAIN, STRING, Value(Nbt.String(term.value)))
            is Term.ByteArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is Term.ByteOf -> it.value
                        else -> 0
                    }
                }
                +Append(MAIN, BYTE_ARRAY, Value(Nbt.ByteArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.ByteOf) {
                        packTerm(element)
                        +SetData(MAIN, BYTE_ARRAY[-1][index], From(MAIN, BYTE[-1]))
                        +Pop(MAIN, BYTE)
                    }
                }
            }
            is Term.IntArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is Term.IntOf -> it.value
                        else -> 0
                    }
                }
                +Append(MAIN, INT_ARRAY, Value(Nbt.IntArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.IntOf) {
                        packTerm(element)
                        +SetData(MAIN, INT_ARRAY[-1][index], From(MAIN, INT[-1]))
                        +Pop(MAIN, INT)
                    }
                }
            }
            is Term.LongArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is Term.LongOf -> it.value
                        else -> 0
                    }
                }
                +Append(MAIN, LONG_ARRAY, Value(Nbt.LongArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.LongOf) {
                        packTerm(element)
                        +SetData(MAIN, LONG_ARRAY[-1][index], From(MAIN, LONG[-1]))
                        +Pop(MAIN, LONG)
                    }
                }
            }
            is Term.ListOf -> {
                +Append(MAIN, LIST, Value(Nbt.List(emptyList())))
                if (term.elements.isNotEmpty()) {
                    val type = eraseType(term.elements.first().type)
                    val targetPath = LIST[if (type == NbtType.LIST) -2 else -1]
                    val sourcePath = type.toPath()[-1]
                    term.elements.forEach { element ->
                        packTerm(element)
                        +Append(MAIN, targetPath, From(MAIN, sourcePath))
                        +RemoveData(MAIN, sourcePath)
                    }
                }
            }
            is Term.CompoundOf -> {
                +Append(MAIN, COMPOUND, Value(Nbt.Compound(emptyMap())))
                if (term.elements.isNotEmpty()) {
                    term.elements.entries.forEach { (name, element) ->
                        val type = eraseType(element.type)
                        val targetPath = COMPOUND[if (type == NbtType.COMPOUND) -2 else -1][name.text]
                        val sourcePath = type.toPath()[-1]
                        packTerm(element)
                        +SetData(MAIN, targetPath, From(MAIN, sourcePath))
                        +RemoveData(MAIN, sourcePath)
                    }
                }
            }
            is Term.BoxOf -> {
                +Append(MAIN, COMPOUND, Value(Nbt.Compound(emptyMap())))

                packTerm(term.content)
                val contentType = eraseType(term.content.type)
                val contentPath = contentType.toPath()[-1]
                +SetData(MAIN, COMPOUND[if (contentType == NbtType.COMPOUND) -2 else -1]["0"], From(MAIN, contentPath))
                +RemoveData(MAIN, contentPath)

                packTerm(term.tag)
                val tagPath = BYTE[-1]
                +SetData(MAIN, COMPOUND[-1]["1"], From(MAIN, tagPath))
                +RemoveData(MAIN, tagPath)
            }
            is Term.RefOf -> TODO()
            is Term.Refl -> +Append(MAIN, BYTE, Value(Nbt.Byte(0)))
            is Term.FunOf -> +Append(MAIN, INT, Value(Nbt.Int(term.tag)))
            is Term.Apply -> {
                term.arguments.forEach { packTerm(it) }
                packTerm(term.function)
                +RunFunction(APPLY)
            }
            is Term.Or -> TODO()
            is Term.And -> TODO()
            is Term.Unit -> +Append(MAIN, BYTE, Value(Nbt.Byte(20)))
            is Term.Bool -> +Append(MAIN, BYTE, Value(Nbt.Byte(21)))
            is Term.Byte -> +Append(MAIN, BYTE, Value(Nbt.Byte(22)))
            is Term.Short -> +Append(MAIN, BYTE, Value(Nbt.Byte(23)))
            is Term.Int -> +Append(MAIN, BYTE, Value(Nbt.Byte(24)))
            is Term.Long -> +Append(MAIN, BYTE, Value(Nbt.Byte(25)))
            is Term.Float -> +Append(MAIN, BYTE, Value(Nbt.Byte(26)))
            is Term.Double -> +Append(MAIN, BYTE, Value(Nbt.Byte(27)))
            is Term.String -> +Append(MAIN, BYTE, Value(Nbt.Byte(28)))
            is Term.ByteArray -> +Append(MAIN, BYTE, Value(Nbt.Byte(29)))
            is Term.IntArray -> +Append(MAIN, BYTE, Value(Nbt.Byte(30)))
            is Term.LongArray -> +Append(MAIN, BYTE, Value(Nbt.Byte(31)))
            is Term.List -> TODO()
            is Term.Compound -> TODO()
            is Term.Box -> TODO()
            is Term.Ref -> TODO()
            is Term.Eq -> TODO()
            is Term.Fun -> TODO()
            is Term.Type -> +Append(MAIN, BYTE, Value(Nbt.Byte(35)))
        }
    }

    // TODO: nested patterns
    private fun Context.packPat(pat: Pat) {
        val scrutinee = eraseType(pat.type).toPath()[-1]
        when (pat) {
            is Pat.Var -> TODO()
            is Pat.UnitOf -> +SetScore(R0, REG, 1)
            is Pat.BoolOf -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(if (pat.value) 1 else 0), Run(SetScore(R0, REG, 1))))
            }
            is Pat.ByteOf -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(pat.value.toInt()), Run(SetScore(R0, REG, 1))))
            }
            is Pat.ShortOf -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(pat.value.toInt()), Run(SetScore(R0, REG, 1))))
            }
            is Pat.IntOf -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(pat.value), Run(SetScore(R0, REG, 1))))
            }
            is Pat.LongOf -> { // TODO: benchmark other methods
                +SetData(MAIN, SCRUTINEE, From(MAIN, scrutinee))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckMatchingData(true, MAIN, NbtPath(listOf(NbtNode.MatchRootObject(Nbt.Compound(mapOf(SCRUTINEE_KEY to Nbt.Long(pat.value)))))), Run(SetScore(R0, REG, 1))))
            }
            is Pat.FloatOf -> {
                +SetData(MAIN, SCRUTINEE, From(MAIN, scrutinee))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckMatchingData(true, MAIN, NbtPath(listOf(NbtNode.MatchRootObject(Nbt.Compound(mapOf(SCRUTINEE_KEY to Nbt.Float(pat.value)))))), Run(SetScore(R0, REG, 1))))
            }
            is Pat.DoubleOf -> {
                +SetData(MAIN, SCRUTINEE, From(MAIN, scrutinee))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckMatchingData(true, MAIN, NbtPath(listOf(NbtNode.MatchRootObject(Nbt.Compound(mapOf(SCRUTINEE_KEY to Nbt.Double(pat.value)))))), Run(SetScore(R0, REG, 1))))
            }
            is Pat.StringOf -> {
                +SetData(MAIN, SCRUTINEE, From(MAIN, scrutinee))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckMatchingData(true, MAIN, NbtPath(listOf(NbtNode.MatchRootObject(Nbt.Compound(mapOf(SCRUTINEE_KEY to Nbt.String(pat.value)))))), Run(SetScore(R0, REG, 1))))
            }
            is Pat.ByteArrayOf -> TODO()
            is Pat.IntArrayOf -> TODO()
            is Pat.LongArrayOf -> TODO()
            is Pat.ListOf -> TODO()
            is Pat.CompoundOf -> TODO()
            is Pat.BoxOf -> TODO()
            is Pat.RefOf -> +SetScore(R0, REG, 1)
            is Pat.Refl -> TODO()
            is Pat.Or -> TODO()
            is Pat.And -> TODO()
            is Pat.Unit -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(20), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Bool -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(21), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Byte -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(22), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Short -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(23), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Int -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(24), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Long -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(25), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Float -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(26), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Double -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(27), Run(SetScore(R0, REG, 1))))
            }
            is Pat.String -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(28), Run(SetScore(R0, REG, 1))))
            }
            is Pat.ByteArray -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(29), Run(SetScore(R0, REG, 1))))
            }
            is Pat.IntArray -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(30), Run(SetScore(R0, REG, 1))))
            }
            is Pat.LongArray -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(31), Run(SetScore(R0, REG, 1))))
            }
            is Pat.Box -> TODO()
            is Pat.Ref -> TODO()
            is Pat.Eq -> TODO()
            is Pat.Type -> {
                +Execute(StoreValue(RESULT, R0, REG, Run(GetData(MAIN, scrutinee))))
                +SetScore(R0, REG, 0)
                +Execute(E.CheckScore(true, R0, REG, EqConst(35), Run(SetScore(R0, REG, 1))))
            }
        }
    }

    private fun Context.packMatch(clauses: List<Pair<Pat, Term>>): ResourceLocation {
        clauses.windowed(2, partialWindows = true).forEachIndexed { index, clauses ->
            val (pat, term) = clauses[0]
            +withName(ResourceLocation("${name.path}-$index")).apply {
                if (clauses.size == 2) {
                    packPat(pat)

                    val arm = name.copy(path = "${name.path}-0")
                    +withName(arm).apply {
                        packTerm(term)
                        +SetScore(R0, REG, 1) // TODO: avoid register restoration when possible
                    }
                    +Execute(E.CheckScore(true, R0, REG, GeConst(1), Run(RunFunction(arm))))
                    +Execute(E.CheckScore(true, R0, REG, LeConst(0), Run(RunFunction(ResourceLocation("${this@packMatch.name.path}-${index + 1}")))))
                } else {
                    packTerm(term)
                }
            }
        }
        return ResourceLocation("${name.path}-0")
    }

    private operator fun Context.unaryPlus() {
        functions += toFunction()
    }

    private data class Context(
        val name: ResourceLocation,
        private val commands: MutableList<Command> = mutableListOf(),
        private val entries: Map<NbtType, MutableList<String>> = NbtType.values().associateWith { mutableListOf() },
    ) {
        operator fun Command.unaryPlus() {
            commands += this
        }

        fun getIndex(type: NbtType, name: String): Int {
            val entry = entries[type]!!
            return entry.lastIndexOf(name) - entry.size
        }

        fun bind(type: NbtType, name: String) {
            entries[type]!! += name
        }

        fun pop(type: NbtType) {
            entries[type]!!.removeLast()
        }

        fun withName(name: ResourceLocation): Context = copy(name = name, commands = mutableListOf())

        fun toFunction(): PFunction = PFunction(name, commands)
    }

    data class Result(
        val functions: List<PFunction>,
    )

    companion object {
        private fun eraseType(type: Type): NbtType = when (type) {
            is Type.Hole -> throw Error()
            is Type.Meta -> throw Error()
            is Type.Var -> TODO()
            is Type.Def -> TODO()
            is Type.Match -> TODO()
            is Type.UnitOf -> throw Error()
            is Type.BoolOf -> throw Error()
            is Type.ByteOf -> throw Error()
            is Type.ShortOf -> throw Error()
            is Type.IntOf -> throw Error()
            is Type.LongOf -> throw Error()
            is Type.FloatOf -> throw Error()
            is Type.DoubleOf -> throw Error()
            is Type.StringOf -> throw Error()
            is Type.ByteArrayOf -> throw Error()
            is Type.IntArrayOf -> throw Error()
            is Type.LongArrayOf -> throw Error()
            is Type.ListOf -> throw Error()
            is Type.CompoundOf -> throw Error()
            is Type.BoxOf -> throw Error()
            is Type.RefOf -> throw Error()
            is Type.Refl -> throw Error()
            is Type.FunOf -> throw Error()
            is Type.Apply -> TODO()
            is Type.CodeOf -> throw Error()
            is Type.Splice -> throw Error()
            is Type.Or -> TODO()
            is Type.And -> TODO()
            is Type.Unit -> NbtType.BYTE
            is Type.Bool -> NbtType.BYTE
            is Type.Byte -> NbtType.BYTE
            is Type.Short -> NbtType.SHORT
            is Type.Int -> NbtType.INT
            is Type.Long -> NbtType.LONG
            is Type.Float -> NbtType.FLOAT
            is Type.Double -> NbtType.DOUBLE
            is Type.String -> NbtType.STRING
            is Type.ByteArray -> NbtType.BYTE_ARRAY
            is Type.IntArray -> NbtType.INT_ARRAY
            is Type.LongArray -> NbtType.LONG_ARRAY
            is Type.List -> NbtType.LIST
            is Type.Compound -> NbtType.COMPOUND
            is Type.Box -> NbtType.COMPOUND
            is Type.Ref -> NbtType.INT
            is Type.Eq -> NbtType.BYTE
            is Type.Fun -> NbtType.BYTE
            is Type.Code -> throw Error()
            is Type.Type -> NbtType.BYTE
        }

        private fun NbtType.toPath(): NbtPath = when (this) {
            NbtType.BYTE -> BYTE
            NbtType.SHORT -> SHORT
            NbtType.INT -> INT
            NbtType.LONG -> LONG
            NbtType.FLOAT -> FLOAT
            NbtType.DOUBLE -> DOUBLE
            NbtType.BYTE_ARRAY -> BYTE_ARRAY
            NbtType.STRING -> STRING
            NbtType.LIST -> LIST
            NbtType.COMPOUND -> COMPOUND
            NbtType.INT_ARRAY -> INT_ARRAY
            NbtType.LONG_ARRAY -> LONG_ARRAY
        }

        operator fun invoke(input: Defun.Result): Result = Pack().run {
            pack(input.functions, input.item)
            Result(functions)
        }
    }
}
