package mce.phase.backend

import mce.ast.Id
import mce.ast.defun.Item
import mce.ast.defun.Pat
import mce.ast.defun.Term
import mce.ast.pack.*
import mce.ast.pack.Command.*
import mce.ast.pack.Consumer.RESULT
import mce.ast.pack.Execute.Run
import mce.ast.pack.Execute.StoreValue
import mce.ast.pack.SourceComparator.Matches
import mce.ast.pack.SourceProvider.From
import mce.ast.pack.SourceProvider.Value
import mce.ast.core.VTerm as Type
import mce.ast.pack.Execute as E
import mce.ast.pack.Function as PFunction

@Suppress("NAME_SHADOWING")
class Pack private constructor(
    types: Map<Id, Type>,
) {
    private val types: Map<Id, NbtType> = types.mapValues { erase(it.value) }
    private val functions: MutableList<PFunction> = mutableListOf()

    private fun pack(terms: Map<Int, Term>, item: Item) {
        +Context(APPLY).apply {
            +Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1]))))
            +Pop(STACKS, INT)
            // TODO: use 4-ary search
            terms.forEach { (tag, term) ->
                val name = ResourceLocation("$tag")
                +Context(name).apply { packTerm(term) }
                +Execute(E.CheckScore(true, REGISTER_0, REGISTERS, Matches(tag, tag), Run(RunFunction(name))))
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
                val type = getType(term.id)
                val path = type.toPath()
                val index = getIndex(type, term.name)
                +Append(STACKS, path, From(STACKS, path[index]))
            }
            is Term.Def -> {
                term.arguments.forEach { packTerm(it) }
                +RunFunction(ResourceLocation(term.name))
            }
            is Term.Let -> {
                val type = getType(term.init.id)
                packTerm(term.init)
                bind(type, term.name)
                packTerm(term.body)
                pop(type)
            }
            is Term.Match -> {
                packTerm(term.scrutinee)
                +RunFunction(packMatch(term.clauses))
            }
            is Term.UnitOf -> +Append(STACKS, BYTE, Value(Nbt.Byte(0)))
            is Term.BoolOf -> +Append(STACKS, BYTE, Value(Nbt.Byte(if (term.value) 1 else 0)))
            is Term.ByteOf -> +Append(STACKS, BYTE, Value(Nbt.Byte(term.value)))
            is Term.ShortOf -> +Append(STACKS, SHORT, Value(Nbt.Short(term.value)))
            is Term.IntOf -> +Append(STACKS, INT, Value(Nbt.Int(term.value)))
            is Term.LongOf -> +Append(STACKS, LONG, Value(Nbt.Long(term.value)))
            is Term.FloatOf -> +Append(STACKS, FLOAT, Value(Nbt.Float(term.value)))
            is Term.DoubleOf -> +Append(STACKS, DOUBLE, Value(Nbt.Double(term.value)))
            is Term.StringOf -> +Append(STACKS, STRING, Value(Nbt.String(term.value)))
            is Term.ByteArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is Term.ByteOf -> it.value
                        else -> 0
                    }
                }
                +Append(STACKS, BYTE_ARRAY, Value(Nbt.ByteArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.ByteOf) {
                        packTerm(element)
                        +SetData(STACKS, BYTE_ARRAY[-1][index], From(STACKS, BYTE[-1]))
                        +Pop(STACKS, BYTE)
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
                +Append(STACKS, INT_ARRAY, Value(Nbt.IntArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.IntOf) {
                        packTerm(element)
                        +SetData(STACKS, INT_ARRAY[-1][index], From(STACKS, INT[-1]))
                        +Pop(STACKS, INT)
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
                +Append(STACKS, LONG_ARRAY, Value(Nbt.LongArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is Term.LongOf) {
                        packTerm(element)
                        +SetData(STACKS, LONG_ARRAY[-1][index], From(STACKS, LONG[-1]))
                        +Pop(STACKS, LONG)
                    }
                }
            }
            is Term.ListOf -> {
                +Append(STACKS, LIST, Value(Nbt.List(emptyList())))
                if (term.elements.isNotEmpty()) {
                    val type = getType(term.elements.first().id)
                    val targetPath = LIST[if (type == NbtType.LIST) -2 else -1]
                    val sourcePath = type.toPath()[-1]
                    term.elements.forEach { element ->
                        packTerm(element)
                        +Append(STACKS, targetPath, From(STACKS, sourcePath))
                        +RemoveData(STACKS, sourcePath)
                    }
                }
            }
            is Term.CompoundOf -> {
                +Append(STACKS, COMPOUND, Value(Nbt.Compound(emptyMap())))
                if (term.elements.isNotEmpty()) {
                    term.elements.entries.forEach { (name, element) ->
                        val type = getType(element.id)
                        val targetPath = COMPOUND[if (type == NbtType.COMPOUND) -2 else -1][name.text]
                        val sourcePath = type.toPath()[-1]
                        packTerm(element)
                        +SetData(STACKS, targetPath, From(STACKS, sourcePath))
                        +RemoveData(STACKS, sourcePath)
                    }
                }
            }
            is Term.BoxOf -> {
                +Append(STACKS, COMPOUND, Value(Nbt.Compound(emptyMap())))

                packTerm(term.content)
                val contentType = getType(term.content.id)
                val contentPath = contentType.toPath()[-1]
                +SetData(STACKS, COMPOUND[if (contentType == NbtType.COMPOUND) -2 else -1]["0"], From(STACKS, contentPath))
                +RemoveData(STACKS, contentPath)

                packTerm(term.tag)
                val tagPath = BYTE[-1]
                +SetData(STACKS, COMPOUND[-1]["1"], From(STACKS, tagPath))
                +RemoveData(STACKS, tagPath)
            }
            is Term.RefOf -> TODO()
            is Term.Refl -> +Append(STACKS, BYTE, Value(Nbt.Byte(0)))
            is Term.FunOf -> +Append(STACKS, INT, Value(Nbt.Int(term.tag)))
            is Term.Apply -> {
                term.arguments.forEach { packTerm(it) }
                packTerm(term.function)
                +RunFunction(APPLY)
            }
            is Term.Or -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.And -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.Unit -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.Bool -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.Byte -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.Short -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.SHORT.ordinal.toByte())))
            is Term.Int -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is Term.Long -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.LONG.ordinal.toByte())))
            is Term.Float -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.FLOAT.ordinal.toByte())))
            is Term.Double -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.DOUBLE.ordinal.toByte())))
            is Term.String -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.STRING.ordinal.toByte())))
            is Term.ByteArray -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE_ARRAY.ordinal.toByte())))
            is Term.IntArray -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.INT_ARRAY.ordinal.toByte())))
            is Term.LongArray -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.LONG_ARRAY.ordinal.toByte())))
            is Term.List -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.LIST.ordinal.toByte())))
            is Term.Compound -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.COMPOUND.ordinal.toByte())))
            is Term.Box -> +Append(STACKS, COMPOUND, Value(Nbt.Byte(NbtType.COMPOUND.ordinal.toByte())))
            is Term.Ref -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is Term.Eq -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is Term.Fun -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is Term.Type -> +Append(STACKS, BYTE, Value(Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
        }
    }

    private fun Context.packPat(pat: Pat) {
        // TODO: store 1 in [REGISTER_0] if matched
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
                        +SetScore(REGISTER_0, REGISTERS, 1) // TODO: avoid register restoration when possible
                    }
                    +Execute(E.CheckScore(true, REGISTER_0, REGISTERS, Matches(min = 1), Run(RunFunction(arm))))
                    +Execute(E.CheckScore(true, REGISTER_0, REGISTERS, Matches(max = 0), Run(RunFunction(ResourceLocation("${this@packMatch.name.path}-${index + 1}")))))
                } else {
                    packTerm(term)
                }
            }
        }
        return ResourceLocation("${name.path}-0")
    }

    private fun getType(id: Id): NbtType = types[id]!!

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
        private fun erase(type: Type): NbtType = when (type) {
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

        operator fun invoke(input: Defun.Result): Result = Pack(input.types).run {
            pack(input.functions, input.item)
            Result(functions)
        }
    }
}
