package mce.phase

import mce.graph.Defunctionalized as D
import mce.graph.Packed as P

class Pack private constructor() {
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
            is D.Term.Var -> TODO()
            is D.Term.Def -> +P.Command.RunFunction(P.ResourceLocation(term.name))
            is D.Term.Let -> {
                packTerm(term.init)
                packTerm(term.body)
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
                        +P.Command.SetData(STACK, BYTE_ARRAY[index], P.SourceProvider.From(STACK, BYTE[-1]))
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
                        +P.Command.SetData(STACK, INT_ARRAY[index], P.SourceProvider.From(STACK, INT[-1]))
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
                        +P.Command.SetData(STACK, LONG_ARRAY[index], P.SourceProvider.From(STACK, LONG[-1]))
                        +P.Command.RemoveData(STACK, LONG[-1])
                    }
                }
            }
            is D.Term.ListOf -> TODO()
            is D.Term.CompoundOf -> TODO()
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
            is D.Term.Bool -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Byte -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Short -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Int -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Long -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Float -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Double -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.String -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.ByteArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.IntArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.LongArray -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.List -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Compound -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Ref -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Eq -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Fun -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Thunk -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Type -> +P.Command.InsertAtIndex(STACK, BYTE, -1, P.SourceProvider.Value(P.Nbt.Byte(0)))
        }
    }

    private class Context(
        private val _commands: MutableList<P.Command> = mutableListOf()
    ) {
        val commands: List<P.Command> get() = _commands

        operator fun P.Command.unaryPlus() {
            _commands.add(this)
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

        operator fun P.NbtPath.get(pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchElement(pattern))

        operator fun P.NbtPath.invoke(): P.NbtPath = P.NbtPath(nodes + P.NbtNode.AllElements)

        operator fun P.NbtPath.get(index: Int): P.NbtPath = P.NbtPath(nodes + P.NbtNode.IndexedElement(index))

        operator fun P.NbtPath.get(name: String, pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchObject(name, pattern))

        operator fun P.NbtPath.get(name: String): P.NbtPath = P.NbtPath(nodes + P.NbtNode.CompoundChild(name))

        fun nbtListOf(vararg elements: P.Nbt): P.Nbt.List = P.Nbt.List(elements.toList())

        fun nbtCompoundOf(vararg elements: Pair<String, P.Nbt>): P.Nbt.Compound = P.Nbt.Compound(elements.toMap())

        operator fun invoke(functions: Map<Int, D.Term>, item: D.Item): P.Datapack = Pack().pack(functions, item)
    }
}
