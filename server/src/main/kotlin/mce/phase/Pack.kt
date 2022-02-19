package mce.phase

import mce.graph.Id
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
                +P.Command.InsertAtIndex(STACK, path, -1, P.SourceProvider.From(STACK, path[index]))
            }
            is D.Term.Def -> +P.Command.RunFunction(P.ResourceLocation(term.name))
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
            is D.Term.BoolOf -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(if (term.value) 1 else 0)))
            is D.Term.ByteOf -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(term.value)))
            is D.Term.ShortOf -> +P.Command.InsertAtIndex(STACK, SHORT, -1, P.SourceProvider.Value(P.Nbt.Short(term.value)))
            is D.Term.IntOf -> +P.Command.InsertAtIndex(STACK, INT, -1, P.SourceProvider.Value(P.Nbt.Int(term.value)))
            is D.Term.LongOf -> +P.Command.InsertAtIndex(STACK, LONG, -1, P.SourceProvider.Value(P.Nbt.Long(term.value)))
            is D.Term.FloatOf -> +P.Command.InsertAtIndex(STACK, FLOAT, -1, P.SourceProvider.Value(P.Nbt.Float(term.value)))
            is D.Term.DoubleOf -> +P.Command.InsertAtIndex(STACK, DOUBLE, -1, P.SourceProvider.Value(P.Nbt.Double(term.value)))
            is D.Term.StringOf -> +P.Command.InsertAtIndex(STACK, STRING, -1, P.SourceProvider.Value(P.Nbt.String(term.value)))
            is D.Term.ByteArrayOf -> {
                val elements = term.elements.map {
                    when (it) {
                        is D.Term.ByteOf -> it.value
                        else -> 0
                    }
                }
                +P.Command.InsertAtIndex(STACK, BYTE_ARRAY, -1, P.SourceProvider.Value(P.Nbt.ByteArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.ByteOf) {
                        packTerm(element)
                        +P.Command.SetData(STACK, BYTE_ARRAY[-1][index], P.SourceProvider.From(STACK, BYTE[-1]))
                        +P.Command.RemoveData(STACK, BYTE[-1])
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
                +P.Command.InsertAtIndex(STACK, INT_ARRAY, -1, P.SourceProvider.Value(P.Nbt.IntArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.IntOf) {
                        packTerm(element)
                        +P.Command.SetData(STACK, INT_ARRAY[-1][index], P.SourceProvider.From(STACK, INT[-1]))
                        +P.Command.RemoveData(STACK, INT[-1])
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
                +P.Command.InsertAtIndex(STACK, LONG_ARRAY, -1, P.SourceProvider.Value(P.Nbt.LongArray(elements)))
                term.elements.mapIndexed { index, element ->
                    if (element !is D.Term.LongOf) {
                        packTerm(element)
                        +P.Command.SetData(STACK, LONG_ARRAY[-1][index], P.SourceProvider.From(STACK, LONG[-1]))
                        +P.Command.RemoveData(STACK, LONG[-1])
                    }
                }
            }
            is D.Term.ListOf -> {
                +P.Command.InsertAtIndex(STACK, LIST, -1, P.SourceProvider.Value(P.Nbt.List(emptyList())))
                if (term.elements.isNotEmpty()) {
                    val type = getType(term.elements.first().id)
                    val targetPath = LIST[if (type == P.NbtType.LIST) -2 else -1]
                    val sourcePath = type.toPath()[-1]
                    term.elements.forEach { element ->
                        packTerm(element)
                        +P.Command.InsertAtIndex(STACK, targetPath, -1, P.SourceProvider.From(STACK, sourcePath))
                        +P.Command.RemoveData(STACK, sourcePath)
                    }
                }
            }
            is D.Term.CompoundOf -> {
                +P.Command.InsertAtIndex(STACK, COMPOUND, -1, P.SourceProvider.Value(P.Nbt.Compound(emptyMap())))
                if (term.elements.isNotEmpty()) {
                    term.elements.forEachIndexed { index, element ->
                        val type = getType(element.id)
                        val targetPath = COMPOUND[if (type == P.NbtType.COMPOUND) -2 else -1]["$index"]
                        val sourcePath = type.toPath()[-1]
                        packTerm(element)
                        +P.Command.SetData(STACK, targetPath, P.SourceProvider.From(STACK, sourcePath))
                        +P.Command.RemoveData(STACK, sourcePath)
                    }
                }
            }
            is D.Term.BoxOf -> {
                +P.Command.InsertAtIndex(STACK, LIST, -1, P.SourceProvider.Value(P.Nbt.List(emptyList())))
                TODO()
            }
            is D.Term.RefOf -> TODO()
            is D.Term.Refl -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.FunOf -> +P.Command.InsertAtIndex(STACK, INT, -1, P.SourceProvider.Value(P.Nbt.Int(term.tag)))
            is D.Term.Apply -> {
                term.arguments.forEach { packTerm(it) }
                packTerm(term.function)
                TODO()
            }
            is D.Term.Union -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Intersection -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Bool -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(1)))
            is D.Term.Byte -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(1)))
            is D.Term.Short -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(2)))
            is D.Term.Int -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(3)))
            is D.Term.Long -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(4)))
            is D.Term.Float -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(5)))
            is D.Term.Double -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(6)))
            is D.Term.String -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(8)))
            is D.Term.ByteArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(7)))
            is D.Term.IntArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(11)))
            is D.Term.LongArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(12)))
            is D.Term.List -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(9)))
            is D.Term.Compound -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(10)))
            is D.Term.Box -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(9)))
            is D.Term.Ref -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(3)))
            is D.Term.Eq -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(1)))
            is D.Term.Fun -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(3)))
            is D.Term.Type -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(1)))
        }
    }

    private fun getType(id: Id): P.NbtType = types[id]!!

    private fun erase(type: C.Value): P.NbtType = when (type) {
        is C.Value.Hole -> throw Error()
        is C.Value.Meta -> throw Error()
        is C.Value.Var -> TODO()
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
        is C.Value.BoxOf -> throw Error()
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
        is C.Value.Box -> P.NbtType.LIST
        is C.Value.Ref -> P.NbtType.INT
        is C.Value.Eq -> P.NbtType.BYTE
        is C.Value.Fun -> P.NbtType.BYTE
        is C.Value.Code -> throw Error()
        is C.Value.Type -> P.NbtType.BYTE
    }

    private class Context(
        private val _commands: MutableList<P.Command> = mutableListOf(),
        private val entries: Map<P.NbtType, MutableList<String>> = P.NbtType.values().associateWith { mutableListOf() }
    ) {
        val commands: List<P.Command> get() = _commands

        operator fun P.Command.unaryPlus() {
            _commands.add(this)
        }

        fun getIndex(type: P.NbtType, name: String): Int {
            val entry = entries[type]!!
            return entry.lastIndexOf(name) - entry.size
        }

        fun bind(type: P.NbtType, name: String) {
            entries[type]!! += name
        }

        fun pop(type: P.NbtType) {
            entries[type]!!.removeLast()
        }
    }

    companion object {
        /**
         * A storage to store stacks.
         * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
         */
        @Suppress("KDocUnresolvedReference")
        val STACK = P.ResourceLocation("0")

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

        operator fun invoke(input: Defunctionalize.Result): P.Datapack = Pack(input.types).pack(input.functions, input.item)
    }
}
