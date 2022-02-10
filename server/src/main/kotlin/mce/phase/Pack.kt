package mce.phase

import mce.graph.Packed as P
import mce.graph.Staged as S

class Pack private constructor() {
    private fun packItem(item: S.Item): P.Function = when (item) {
        is S.Item.Definition -> TODO()
    }

    private fun Context.packTerm(term: S.Term) {
        when (term) {
            is S.Term.Variable -> TODO()
            is S.Term.Definition -> TODO()
            is S.Term.Let -> TODO()
            is S.Term.Match -> TODO()
            is S.Term.BooleanOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(if (term.value) 1 else 0)))
            is S.Term.ByteOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(term.value)))
            is S.Term.ShortOf -> +append(STACKS, SHORT, P.SourceProvider.Value(P.Nbt.Short(term.value)))
            is S.Term.IntOf -> +append(STACKS, INT, P.SourceProvider.Value(P.Nbt.Int(term.value)))
            is S.Term.LongOf -> +append(STACKS, LONG, P.SourceProvider.Value(P.Nbt.Long(term.value)))
            is S.Term.FloatOf -> +append(STACKS, FLOAT, P.SourceProvider.Value(P.Nbt.Float(term.value)))
            is S.Term.DoubleOf -> +append(STACKS, DOUBLE, P.SourceProvider.Value(P.Nbt.Double(term.value)))
            is S.Term.StringOf -> +append(STACKS, STRING, P.SourceProvider.Value(P.Nbt.String(term.value)))
            is S.Term.ByteArrayOf -> TODO()
            is S.Term.IntArrayOf -> TODO()
            is S.Term.LongArrayOf -> TODO()
            is S.Term.ListOf -> TODO()
            is S.Term.CompoundOf -> TODO()
            is S.Term.ReferenceOf -> TODO()
            is S.Term.FunctionOf -> TODO()
            is S.Term.Apply -> TODO()
            is S.Term.ThunkOf -> TODO()
            is S.Term.Force -> TODO()
            is S.Term.Union -> TODO()
            is S.Term.Intersection -> TODO()
            is S.Term.Boolean -> TODO()
            is S.Term.Byte -> TODO()
            is S.Term.Short -> TODO()
            is S.Term.Int -> TODO()
            is S.Term.Long -> TODO()
            is S.Term.Float -> TODO()
            is S.Term.Double -> TODO()
            is S.Term.String -> TODO()
            is S.Term.ByteArray -> TODO()
            is S.Term.IntArray -> TODO()
            is S.Term.LongArray -> TODO()
            is S.Term.List -> TODO()
            is S.Term.Compound -> TODO()
            is S.Term.Reference -> TODO()
            is S.Term.Function -> TODO()
            is S.Term.Thunk -> TODO()
            is S.Term.Type -> TODO()
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

        operator fun invoke(item: S.Item): P.Function = Pack().packItem(item)
    }
}
