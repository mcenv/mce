package mce.phase.pack

import mce.graph.Packed.Command
import mce.graph.Packed.Nbt
import mce.graph.Packed.NbtNode
import mce.graph.Packed.NbtPath
import mce.graph.Packed.ResourceLocation
import mce.graph.Packed.SourceProvider

object Dsl {
    operator fun NbtPath.get(pattern: Nbt.Compound): NbtPath = NbtPath(nodes + NbtNode.MatchElement(pattern))

    operator fun NbtPath.invoke(): NbtPath = NbtPath(nodes + NbtNode.AllElements)

    operator fun NbtPath.get(index: Int): NbtPath = NbtPath(nodes + NbtNode.IndexedElement(index))

    operator fun NbtPath.get(name: String, pattern: Nbt.Compound): NbtPath = NbtPath(nodes + NbtNode.MatchObject(name, pattern))

    operator fun NbtPath.get(name: String): NbtPath = NbtPath(nodes + NbtNode.CompoundChild(name))

    fun Append(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = Command.InsertAtIndex(target, path, -1, source)

    fun Prepend(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = Command.InsertAtIndex(target, path, 0, source)
}
