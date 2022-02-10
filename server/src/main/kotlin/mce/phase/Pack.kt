package mce.phase

import mce.graph.Core as C
import mce.graph.Packed as P

class Pack private constructor() {
    private fun packItem(item: C.Item): P.Function = when (item) {
        is C.Item.Definition -> TODO()
    }

    private fun Context.packTerm(term: C.Term) {
        when (term) {
            is C.Term.Hole -> TODO()
            is C.Term.Meta -> TODO()
            is C.Term.Variable -> TODO()
            is C.Term.Definition -> TODO()
            is C.Term.Let -> TODO()
            is C.Term.Match -> TODO()
            is C.Term.BooleanOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(if (term.value) 1 else 0)))
            is C.Term.ByteOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(term.value)))
            is C.Term.ShortOf -> +append(STACKS, SHORT, P.SourceProvider.Value(P.Nbt.Short(term.value)))
            is C.Term.IntOf -> +append(STACKS, INT, P.SourceProvider.Value(P.Nbt.Int(term.value)))
            is C.Term.LongOf -> +append(STACKS, LONG, P.SourceProvider.Value(P.Nbt.Long(term.value)))
            is C.Term.FloatOf -> +append(STACKS, FLOAT, P.SourceProvider.Value(P.Nbt.Float(term.value)))
            is C.Term.DoubleOf -> +append(STACKS, DOUBLE, P.SourceProvider.Value(P.Nbt.Double(term.value)))
            is C.Term.StringOf -> +append(STACKS, STRING, P.SourceProvider.Value(P.Nbt.String(term.value)))
            is C.Term.ByteArrayOf -> TODO()
            is C.Term.IntArrayOf -> TODO()
            is C.Term.LongArrayOf -> TODO()
            is C.Term.ListOf -> TODO()
            is C.Term.CompoundOf -> TODO()
            is C.Term.ReferenceOf -> TODO()
            is C.Term.FunctionOf -> TODO()
            is C.Term.Apply -> TODO()
            is C.Term.ThunkOf -> TODO()
            is C.Term.Force -> TODO()
            is C.Term.CodeOf -> TODO()
            is C.Term.Splice -> TODO()
            is C.Term.Union -> TODO()
            is C.Term.Intersection -> TODO()
            is C.Term.Boolean -> TODO()
            is C.Term.Byte -> TODO()
            is C.Term.Short -> TODO()
            is C.Term.Int -> TODO()
            is C.Term.Long -> TODO()
            is C.Term.Float -> TODO()
            is C.Term.Double -> TODO()
            is C.Term.String -> TODO()
            is C.Term.ByteArray -> TODO()
            is C.Term.IntArray -> TODO()
            is C.Term.LongArray -> TODO()
            is C.Term.List -> TODO()
            is C.Term.Compound -> TODO()
            is C.Term.Reference -> TODO()
            is C.Term.Function -> TODO()
            is C.Term.Thunk -> TODO()
            is C.Term.Code -> TODO()
            is C.Term.Type -> TODO()
        }
    }

    private class Context(
        private val commands: MutableList<P.Command> = mutableListOf()
    ) {
        operator fun P.Command.unaryPlus() {
            commands.add(this)
        }
    }

    companion object {
        /**
         * A storage to store stacks.
         * @see [net.minecraft.resources.ResourceLocation.isAllowedInResourceLocation]
         */
        @Suppress("KDocUnresolvedReference")
        private val STACKS = P.ResourceLocation("0")

        private val BYTE = P.NbtPath()["a"]
        private val SHORT = P.NbtPath()["b"]
        private val INT = P.NbtPath()["c"]
        private val LONG = P.NbtPath()["d"]
        private val FLOAT = P.NbtPath()["e"]
        private val DOUBLE = P.NbtPath()["f"]
        private val BYTE_ARRAY = P.NbtPath()["g"]
        private val STRING = P.NbtPath()["h"]
        private val LIST = P.NbtPath()["i"]
        private val COMPOUND = P.NbtPath()["j"]
        private val INT_ARRAY = P.NbtPath()["k"]
        private val LONG_ARRAY = P.NbtPath()["l"]

        private fun append(target: P.ResourceLocation, path: P.NbtPath, source: P.SourceProvider): P.Command = P.Command.InsertAtIndex(target, path, -1, source)

        private operator fun P.NbtPath.get(pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchElement(pattern))

        private operator fun P.NbtPath.invoke(): P.NbtPath = P.NbtPath(nodes + P.NbtNode.AllElements)

        private operator fun P.NbtPath.get(index: Int): P.NbtPath = P.NbtPath(nodes + P.NbtNode.IndexedElement(index))

        private operator fun P.NbtPath.get(name: String, pattern: P.Nbt.Compound): P.NbtPath = P.NbtPath(nodes + P.NbtNode.MatchObject(name, pattern))

        private operator fun P.NbtPath.get(name: String): P.NbtPath = P.NbtPath(nodes + P.NbtNode.CompoundChild(name))

        private fun nbtListOf(vararg elements: P.Nbt): P.Nbt.List = P.Nbt.List(elements.toList())

        private fun nbtCompoundOf(vararg elements: Pair<String, P.Nbt>): P.Nbt.Compound = P.Nbt.Compound(elements.toMap())

        operator fun invoke(item: C.Item): P.Function = Pack().packItem(item)
    }
}
