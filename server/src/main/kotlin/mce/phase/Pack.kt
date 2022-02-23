package mce.phase

import mce.graph.Id
import mce.graph.Packed.Command.*
import mce.graph.Packed.Consumer.*
import mce.graph.Packed.Execute
import mce.graph.Packed.Execute.*
import mce.graph.Packed.NbtType
import mce.graph.Packed.SourceComparator.*
import mce.graph.Packed.SourceProvider.*
import mce.graph.Core as C
import mce.graph.Defunctionalized as D
import mce.graph.Packed as P

class Pack private constructor(
    types: Map<Id, C.Value>
) {
    private val types: Map<Id, P.NbtType> = types.mapValues { erase(it.value) }

    private fun pack(functions: Map<Int, D.Term>, item: D.Item): P.Datapack = P.Datapack(functions.map { (tag, term) ->
        Context().run {
            packTerm(term)
            P.Function(P.ResourceLocation("$tag"), commands)
        }
    } + packItem(item))

    private fun packItem(item: D.Item): P.Function = when (item) {
        is D.Item.Def -> Context().run {
            packTerm(item.body)
            P.Function(P.ResourceLocation(item.name), commands)
        }
    }

    private fun Context.packTerm(term: D.Term) {
        when (term) {
            is D.Term.Var -> {
                val type = getType(term.id)
                val path = type.toPath()
                val index = getIndex(type, term.name)
                +Append(STACKS, path, From(STACKS, path[index]))
            }
            is D.Term.TaggedVar -> {
                packTerm(term.tag)
                +Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, BYTE[-1]))))
                +RemoveData(STACKS, BYTE[-1])
                // TODO: fix index
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.BYTE.ordinal), Run(Append(STACKS, BYTE, From(STACKS, BYTE[getIndex(NbtType.BYTE, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.SHORT.ordinal), Run(Append(STACKS, SHORT, From(STACKS, SHORT[getIndex(NbtType.SHORT, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.INT.ordinal), Run(Append(STACKS, INT, From(STACKS, INT[getIndex(NbtType.INT, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.LONG.ordinal), Run(Append(STACKS, LONG, From(STACKS, LONG[getIndex(NbtType.LONG, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.FLOAT.ordinal), Run(Append(STACKS, FLOAT, From(STACKS, FLOAT[getIndex(NbtType.FLOAT, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.DOUBLE.ordinal), Run(Append(STACKS, DOUBLE, From(STACKS, DOUBLE[getIndex(NbtType.DOUBLE, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.BYTE_ARRAY.ordinal), Run(Append(STACKS, BYTE_ARRAY, From(STACKS, BYTE_ARRAY[getIndex(NbtType.BYTE_ARRAY, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.STRING.ordinal), Run(Append(STACKS, STRING, From(STACKS, STRING[getIndex(NbtType.STRING, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.LIST.ordinal), Run(Append(STACKS, LIST, From(STACKS, LIST[getIndex(NbtType.LIST, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.COMPOUND.ordinal), Run(Append(STACKS, COMPOUND, From(STACKS, COMPOUND[getIndex(NbtType.COMPOUND, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.INT_ARRAY.ordinal), Run(Append(STACKS, INT_ARRAY, From(STACKS, INT_ARRAY[getIndex(NbtType.INT_ARRAY, term.name)])))))
                +Execute(Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(NbtType.LONG_ARRAY.ordinal), Run(Append(STACKS, LONG_ARRAY, From(STACKS, LONG_ARRAY[getIndex(NbtType.LONG_ARRAY, term.name)])))))
            }
            is D.Term.Def -> +RunFunction(P.ResourceLocation(term.name))
            is D.Term.Let -> {
                val type = getType(term.init.id)
                packTerm(term.init)
                bind(type, term.name)
                packTerm(term.body)
                pop(type)
            }
            is D.Term.Match -> {
                packTerm(term.scrutinee)
                TODO()
            }
            is D.Term.BoolOf -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(if (term.value) 1 else 0)))
            is D.Term.ByteOf -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(term.value)))
            is D.Term.ShortOf -> +Append(STACKS, SHORT, Value(P.Nbt.Short(term.value)))
            is D.Term.IntOf -> +Append(STACKS, INT, Value(P.Nbt.Int(term.value)))
            is D.Term.LongOf -> +Append(STACKS, LONG, Value(P.Nbt.Long(term.value)))
            is D.Term.FloatOf -> +Append(STACKS, FLOAT, Value(P.Nbt.Float(term.value)))
            is D.Term.DoubleOf -> +Append(STACKS, DOUBLE, Value(P.Nbt.Double(term.value)))
            is D.Term.StringOf -> +Append(STACKS, STRING, Value(P.Nbt.String(term.value)))
            is D.Term.ByteArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is D.Term.ByteOf -> it.value
                        else -> 0
                    }
                }
                +Append(STACKS, BYTE_ARRAY, Value(P.Nbt.ByteArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.ByteOf) {
                        packTerm(element)
                        +SetData(STACKS, BYTE_ARRAY[-1][index], From(STACKS, BYTE[-1]))
                        +RemoveData(STACKS, BYTE[-1])
                    }
                }
            }
            is D.Term.IntArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is D.Term.IntOf -> it.value
                        else -> 0
                    }
                }
                +Append(STACKS, INT_ARRAY, Value(P.Nbt.IntArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.IntOf) {
                        packTerm(element)
                        +SetData(STACKS, INT_ARRAY[-1][index], From(STACKS, INT[-1]))
                        +RemoveData(STACKS, INT[-1])
                    }
                }
            }
            is D.Term.LongArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is D.Term.LongOf -> it.value
                        else -> 0
                    }
                }
                +Append(STACKS, LONG_ARRAY, Value(P.Nbt.LongArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.LongOf) {
                        packTerm(element)
                        +SetData(STACKS, LONG_ARRAY[-1][index], From(STACKS, LONG[-1]))
                        +RemoveData(STACKS, LONG[-1])
                    }
                }
            }
            is D.Term.ListOf -> {
                +Append(STACKS, LIST, Value(P.Nbt.List(emptyList())))
                if (term.elements.isNotEmpty()) {
                    val type = getType(term.elements.first().id)
                    val targetPath = LIST[if (type == P.NbtType.LIST) -2 else -1]
                    val sourcePath = type.toPath()[-1]
                    term.elements.forEach { element ->
                        packTerm(element)
                        +Append(STACKS, targetPath, From(STACKS, sourcePath))
                        +RemoveData(STACKS, sourcePath)
                    }
                }
            }
            is D.Term.CompoundOf -> {
                +Append(STACKS, COMPOUND, Value(P.Nbt.Compound(emptyMap())))
                if (term.elements.isNotEmpty()) {
                    term.elements.forEachIndexed { index, element ->
                        val type = getType(element.id)
                        val targetPath = COMPOUND[if (type == P.NbtType.COMPOUND) -2 else -1]["$index"]
                        val sourcePath = type.toPath()[-1]
                        packTerm(element)
                        +SetData(STACKS, targetPath, From(STACKS, sourcePath))
                        +RemoveData(STACKS, sourcePath)
                    }
                }
            }
            is D.Term.RefOf -> TODO()
            is D.Term.Refl -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(0)))
            is D.Term.FunOf -> +Append(STACKS, INT, Value(P.Nbt.Int(term.tag)))
            is D.Term.Apply -> {
                term.arguments.forEach { packTerm(it) }
                packTerm(term.function)
                TODO()
            }
            is D.Term.Union -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Intersection -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Bool -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Byte -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Short -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.SHORT.ordinal.toByte())))
            is D.Term.Int -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is D.Term.Long -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.LONG.ordinal.toByte())))
            is D.Term.Float -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.FLOAT.ordinal.toByte())))
            is D.Term.Double -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.DOUBLE.ordinal.toByte())))
            is D.Term.String -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.STRING.ordinal.toByte())))
            is D.Term.ByteArray -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE_ARRAY.ordinal.toByte())))
            is D.Term.IntArray -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT_ARRAY.ordinal.toByte())))
            is D.Term.LongArray -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.LONG_ARRAY.ordinal.toByte())))
            is D.Term.List -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.LIST.ordinal.toByte())))
            is D.Term.Compound -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.COMPOUND.ordinal.toByte())))
            is D.Term.Ref -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is D.Term.Eq -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Fun -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is D.Term.Type -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
        }
    }

    private fun getType(id: Id): P.NbtType = types[id]!!

    private fun erase(type: C.Value): P.NbtType = when (type) {
        is C.Value.Hole -> throw Error()
        is C.Value.Meta -> throw Error()
        is C.Value.Var -> TODO()
        is C.Value.TaggedVar -> TODO()
        is C.Value.Def -> TODO()
        is C.Value.Match -> TODO()
        is C.Value.BoolOf -> throw Error()
        is C.Value.ByteOf -> throw Error()
        is C.Value.ShortOf -> throw Error()
        is C.Value.IntOf -> throw Error()
        is C.Value.LongOf -> throw Error()
        is C.Value.FloatOf -> throw Error()
        is C.Value.DoubleOf -> throw Error()
        is C.Value.StringOf -> throw Error()
        is C.Value.ByteArrayOf -> throw Error()
        is C.Value.IntArrayOf -> throw Error()
        is C.Value.LongArrayOf -> throw Error()
        is C.Value.ListOf -> throw Error()
        is C.Value.CompoundOf -> throw Error()
        is C.Value.RefOf -> throw Error()
        is C.Value.Refl -> throw Error()
        is C.Value.FunOf -> throw Error()
        is C.Value.Apply -> TODO()
        is C.Value.CodeOf -> throw Error()
        is C.Value.Splice -> throw Error()
        is C.Value.Union -> TODO()
        is C.Value.Intersection -> TODO()
        is C.Value.Bool -> P.NbtType.BYTE
        is C.Value.Byte -> P.NbtType.BYTE
        is C.Value.Short -> P.NbtType.SHORT
        is C.Value.Int -> P.NbtType.INT
        is C.Value.Long -> P.NbtType.LONG
        is C.Value.Float -> P.NbtType.FLOAT
        is C.Value.Double -> P.NbtType.DOUBLE
        is C.Value.String -> P.NbtType.STRING
        is C.Value.ByteArray -> P.NbtType.BYTE_ARRAY
        is C.Value.IntArray -> P.NbtType.INT_ARRAY
        is C.Value.LongArray -> P.NbtType.LONG_ARRAY
        is C.Value.List -> P.NbtType.LIST
        is C.Value.Compound -> P.NbtType.COMPOUND
        is C.Value.Ref -> P.NbtType.INT
        is C.Value.Eq -> P.NbtType.BYTE
        is C.Value.Fun -> P.NbtType.BYTE
        is C.Value.Code -> throw Error()
        is C.Value.Type -> P.NbtType.BYTE
    }

    private class Context(
        private val _commands: MutableList<P.Command> = mutableListOf(),
        private val entries: Map<NbtType, MutableList<String>> = NbtType.values().associateWith { mutableListOf() }
    ) {
        val commands: List<P.Command> get() = _commands

        operator fun P.Command.unaryPlus() {
            _commands.add(this)
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
    }

    companion object {
        /**
         * A storage to store stacks.
         * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
         */
        @Suppress("KDocUnresolvedReference")
        val STACKS = P.ResourceLocation("0")

        val BYTE = P.NbtPath()["a"]
        val SHORT = P.NbtPath()["b"]
        val INT = P.NbtPath()["c"]
        val LONG = P.NbtPath()["d"]
        val FLOAT = P.NbtPath()["e"]
        val DOUBLE = P.NbtPath()["f"]
        val BYTE_ARRAY = P.NbtPath()["g"]
        val STRING = P.NbtPath()["h"]
        val LIST = P.NbtPath()["i"]
        val COMPOUND = P.NbtPath()["j"]
        val INT_ARRAY = P.NbtPath()["k"]
        val LONG_ARRAY = P.NbtPath()["l"]

        val REGISTERS = P.Objective("0")
        val REGISTER_0 = P.ScoreHolder("0")

        private fun P.NbtType.toPath(): P.NbtPath = when (this) {
            P.NbtType.BYTE -> BYTE
            P.NbtType.SHORT -> SHORT
            P.NbtType.INT -> INT
            P.NbtType.LONG -> LONG
            P.NbtType.FLOAT -> FLOAT
            P.NbtType.DOUBLE -> DOUBLE
            P.NbtType.BYTE_ARRAY -> BYTE_ARRAY
            P.NbtType.STRING -> STRING
            P.NbtType.LIST -> LIST
            P.NbtType.COMPOUND -> COMPOUND
            P.NbtType.INT_ARRAY -> INT_ARRAY
            P.NbtType.LONG_ARRAY -> LONG_ARRAY
        }

        operator fun P.NbtPath.get(pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchElement(pattern))

        operator fun P.NbtPath.invoke(): P.NbtPath = P.NbtPath(nodes + P.NbtNode.AllElements)

        operator fun P.NbtPath.get(index: Int): P.NbtPath = P.NbtPath(nodes + P.NbtNode.IndexedElement(index))

        operator fun P.NbtPath.get(name: String, pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchObject(name, pattern))

        operator fun P.NbtPath.get(name: String): P.NbtPath = P.NbtPath(nodes + P.NbtNode.CompoundChild(name))

        fun nbtListOf(vararg elements: P.Nbt): P.Nbt.List = P.Nbt.List(elements.toList())

        fun nbtCompoundOf(vararg elements: Pair<String, P.Nbt>): P.Nbt.Compound = P.Nbt.Compound(elements.toMap())

        private fun Append(target: P.ResourceLocation, path: P.NbtPath, source: P.SourceProvider): P.Command = InsertAtIndex(target, path, -1, source)

        private fun Prepend(target: P.ResourceLocation, path: P.NbtPath, source: P.SourceProvider): P.Command = InsertAtIndex(target, path, 0, source)

        operator fun invoke(input: Defunctionalize.Result): P.Datapack = Pack(input.types).pack(input.functions, input.item)
    }
}
