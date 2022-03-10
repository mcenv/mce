package mce.phase.backend

import mce.graph.Id
import mce.graph.Packed.Command.*
import mce.graph.Packed.Consumer.RESULT
import mce.graph.Packed.Execute.Run
import mce.graph.Packed.Execute.StoreValue
import mce.graph.Packed.NbtType
import mce.graph.Packed.SourceComparator.Matches
import mce.graph.Packed.SourceProvider.From
import mce.graph.Packed.SourceProvider.Value
import mce.graph.Core as C
import mce.graph.Defunctionalized as D
import mce.graph.Packed as P

@Suppress("NAME_SHADOWING")
class Pack private constructor(
    types: Map<Id, C.VTerm>
) {
    private val types: Map<Id, NbtType> = types.mapValues { erase(it.value) }

    private fun pack(functions: Map<Int, D.Term>, item: D.Item): P.Datapack {
        val functions = functions.mapValues { (tag, term) ->
            Context().run {
                packTerm(term)
                P.Function(P.ResourceLocation("$tag"), commands)
            }
        }
        val dispatch = P.Function(APPLY, mutableListOf<P.Command>().apply {
            add(Execute(StoreValue(RESULT, REGISTER_0, REGISTERS, Run(GetData(STACKS, INT[-1])))))
            add(RemoveData(STACKS, INT[-1]))
            // TODO: use 4-ary search
            functions.forEach { (tag, function) ->
                add(Execute(P.Execute.CheckScore(true, REGISTER_0, REGISTERS, Matches(tag..tag), Run(RunFunction(function.name)))))
            }
        })
        val item = packItem(item)
        return P.Datapack(functions.values + dispatch + item)
    }

    private fun packItem(item: D.Item): P.Function = when (item) {
        is D.Item.Def -> Context().run {
            packTerm(item.body)
            P.Function(P.ResourceLocation(item.name), commands)
        }
        is D.Item.Mod -> TODO()
    }

    private fun Context.packTerm(term: D.Term) {
        when (term) {
            is D.Term.Var -> {
                val type = getType(term.id)
                val path = type.toPath()
                val index = getIndex(type, term.name)
                +Append(STACKS, path, From(STACKS, path[index]))
            }
            is D.Term.Def -> {
                term.arguments.forEach { packTerm(it) }
                +RunFunction(P.ResourceLocation(term.name))
            }
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
            is D.Term.UnitOf -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(0)))
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
                    val targetPath = LIST[if (type == NbtType.LIST) -2 else -1]
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
            is D.Term.BoxOf -> {
                +Append(STACKS, COMPOUND, Value(P.Nbt.Compound(emptyMap())))

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
            is D.Term.RefOf -> TODO()
            is D.Term.Refl -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(0)))
            is D.Term.FunOf -> +Append(STACKS, INT, Value(P.Nbt.Int(term.tag)))
            is D.Term.Apply -> {
                term.arguments.forEach { packTerm(it) }
                packTerm(term.function)
                +RunFunction(APPLY)
            }
            is D.Term.Or -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.And -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Unit -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
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
            is D.Term.Box -> +Append(STACKS, COMPOUND, Value(P.Nbt.Byte(NbtType.COMPOUND.ordinal.toByte())))
            is D.Term.Ref -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is D.Term.Eq -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
            is D.Term.Fun -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.INT.ordinal.toByte())))
            is D.Term.Type -> +Append(STACKS, BYTE, Value(P.Nbt.Byte(NbtType.BYTE.ordinal.toByte())))
        }
    }

    private fun getType(id: Id): NbtType = types[id]!!

    companion object {
        /**
         * A storage to store stacks.
         * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
         */
        @Suppress("KDocUnresolvedReference")
        val STACKS = mce.graph.Packed.ResourceLocation("0")

        val BYTE = mce.graph.Packed.NbtPath()["a"]
        val SHORT = mce.graph.Packed.NbtPath()["b"]
        val INT = mce.graph.Packed.NbtPath()["c"]
        val LONG = mce.graph.Packed.NbtPath()["d"]
        val FLOAT = mce.graph.Packed.NbtPath()["e"]
        val DOUBLE = mce.graph.Packed.NbtPath()["f"]
        val BYTE_ARRAY = mce.graph.Packed.NbtPath()["g"]
        val STRING = mce.graph.Packed.NbtPath()["h"]
        val LIST = mce.graph.Packed.NbtPath()["i"]
        val COMPOUND = mce.graph.Packed.NbtPath()["j"]
        val INT_ARRAY = mce.graph.Packed.NbtPath()["k"]
        val LONG_ARRAY = mce.graph.Packed.NbtPath()["l"]

        /**
         * An objective to store registers.
         * @see [net.minecraft.commands.arguments.ObjectiveArgument.parse]
         */
        @Suppress("KDocUnresolvedReference")
        val REGISTERS = mce.graph.Packed.Objective("0")

        val REGISTER_0 = mce.graph.Packed.ScoreHolder("0")

        /**
         * A resource location of the apply function.
         */
        val APPLY = mce.graph.Packed.ResourceLocation("apply")

        operator fun mce.graph.Packed.NbtPath.get(pattern: mce.graph.Packed.Nbt.Compound): mce.graph.Packed.NbtPath = mce.graph.Packed.NbtPath(nodes + mce.graph.Packed.NbtNode.MatchElement(pattern))

        operator fun mce.graph.Packed.NbtPath.invoke(): mce.graph.Packed.NbtPath = mce.graph.Packed.NbtPath(nodes + mce.graph.Packed.NbtNode.AllElements)

        operator fun mce.graph.Packed.NbtPath.get(index: Int): mce.graph.Packed.NbtPath = mce.graph.Packed.NbtPath(nodes + mce.graph.Packed.NbtNode.IndexedElement(index))

        operator fun mce.graph.Packed.NbtPath.get(name: String, pattern: mce.graph.Packed.Nbt.Compound): mce.graph.Packed.NbtPath = mce.graph.Packed.NbtPath(nodes + mce.graph.Packed.NbtNode.MatchObject(name, pattern))

        operator fun mce.graph.Packed.NbtPath.get(name: String): mce.graph.Packed.NbtPath = mce.graph.Packed.NbtPath(nodes + mce.graph.Packed.NbtNode.CompoundChild(name))

        fun Append(target: mce.graph.Packed.ResourceLocation, path: mce.graph.Packed.NbtPath, source: mce.graph.Packed.SourceProvider): mce.graph.Packed.Command = mce.graph.Packed.Command.InsertAtIndex(target, path, -1, source)

        fun Prepend(target: mce.graph.Packed.ResourceLocation, path: mce.graph.Packed.NbtPath, source: mce.graph.Packed.SourceProvider): mce.graph.Packed.Command = mce.graph.Packed.Command.InsertAtIndex(target, path, 0, source)

        private fun erase(type: C.VTerm): NbtType = when (type) {
            is C.VTerm.Hole -> throw Error()
            is C.VTerm.Meta -> throw Error()
            is C.VTerm.Var -> TODO()
            is C.VTerm.Def -> TODO()
            is C.VTerm.Match -> TODO()
            is C.VTerm.UnitOf -> throw Error()
            is C.VTerm.BoolOf -> throw Error()
            is C.VTerm.ByteOf -> throw Error()
            is C.VTerm.ShortOf -> throw Error()
            is C.VTerm.IntOf -> throw Error()
            is C.VTerm.LongOf -> throw Error()
            is C.VTerm.FloatOf -> throw Error()
            is C.VTerm.DoubleOf -> throw Error()
            is C.VTerm.StringOf -> throw Error()
            is C.VTerm.ByteArrayOf -> throw Error()
            is C.VTerm.IntArrayOf -> throw Error()
            is C.VTerm.LongArrayOf -> throw Error()
            is C.VTerm.ListOf -> throw Error()
            is C.VTerm.CompoundOf -> throw Error()
            is C.VTerm.BoxOf -> throw Error()
            is C.VTerm.RefOf -> throw Error()
            is C.VTerm.Refl -> throw Error()
            is C.VTerm.FunOf -> throw Error()
            is C.VTerm.Apply -> TODO()
            is C.VTerm.CodeOf -> throw Error()
            is C.VTerm.Splice -> throw Error()
            is C.VTerm.Or -> TODO()
            is C.VTerm.And -> TODO()
            is C.VTerm.Unit -> NbtType.BYTE
            is C.VTerm.Bool -> NbtType.BYTE
            is C.VTerm.Byte -> NbtType.BYTE
            is C.VTerm.Short -> NbtType.SHORT
            is C.VTerm.Int -> NbtType.INT
            is C.VTerm.Long -> NbtType.LONG
            is C.VTerm.Float -> NbtType.FLOAT
            is C.VTerm.Double -> NbtType.DOUBLE
            is C.VTerm.String -> NbtType.STRING
            is C.VTerm.ByteArray -> NbtType.BYTE_ARRAY
            is C.VTerm.IntArray -> NbtType.INT_ARRAY
            is C.VTerm.LongArray -> NbtType.LONG_ARRAY
            is C.VTerm.List -> NbtType.LIST
            is C.VTerm.Compound -> NbtType.COMPOUND
            is C.VTerm.Box -> NbtType.COMPOUND
            is C.VTerm.Ref -> NbtType.INT
            is C.VTerm.Eq -> NbtType.BYTE
            is C.VTerm.Fun -> NbtType.BYTE
            is C.VTerm.Code -> throw Error()
            is C.VTerm.Type -> NbtType.BYTE
        }

        private fun NbtType.toPath(): P.NbtPath = when (this) {
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

        operator fun invoke(input: Defunctionalize.Result): P.Datapack = Pack(input.types).pack(input.functions, input.item)
    }
}
