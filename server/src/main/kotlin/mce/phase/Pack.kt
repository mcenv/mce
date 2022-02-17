package mce.phase

import mce.graph.Defunctionalized as D
import mce.graph.Packed as P

class Pack private constructor() {
    private fun packItem(item: D.Item): P.Function = when (item) {
        is D.Item.Def -> TODO()
    }

    private fun Context.packTerm(term: D.Term) {
        when (term) {
            is D.Term.Var -> TODO()
            is D.Term.Def -> TODO()
            is D.Term.Let -> TODO()
            is D.Term.Match -> TODO()
            is D.Term.BoolOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(if (term.value) 1 else 0)))
            is D.Term.ByteOf -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(term.value)))
            is D.Term.ShortOf -> +append(STACKS, SHORT, P.SourceProvider.Value(P.Nbt.Short(term.value)))
            is D.Term.IntOf -> +append(STACKS, INT, P.SourceProvider.Value(P.Nbt.Int(term.value)))
            is D.Term.LongOf -> +append(STACKS, LONG, P.SourceProvider.Value(P.Nbt.Long(term.value)))
            is D.Term.FloatOf -> +append(STACKS, FLOAT, P.SourceProvider.Value(P.Nbt.Float(term.value)))
            is D.Term.DoubleOf -> +append(STACKS, DOUBLE, P.SourceProvider.Value(P.Nbt.Double(term.value)))
            is D.Term.StringOf -> +append(STACKS, STRING, P.SourceProvider.Value(P.Nbt.String(term.value)))
            is D.Term.ByteArrayOf -> TODO()
            is D.Term.IntArrayOf -> TODO()
            is D.Term.LongArrayOf -> TODO()
            is D.Term.ListOf -> TODO()
            is D.Term.CompoundOf -> TODO()
            is D.Term.RefOf -> TODO()
            is D.Term.Refl -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.FunOf -> +append(STACKS, INT, P.SourceProvider.Value(P.Nbt.Int(term.tag)))
            is D.Term.Apply -> TODO()
            is D.Term.Union -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Intersection -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Bool -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Byte -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Short -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Int -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Long -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Float -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Double -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.String -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.ByteArray -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.IntArray -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.LongArray -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.List -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Compound -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Ref -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Eq -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Fun -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Thunk -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
            is D.Term.Type -> +append(STACKS, BYTE, P.SourceProvider.Value(P.Nbt.Byte(0)))
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

        operator fun invoke(item: D.Item): P.Function = Pack().packItem(item)
    }
}
