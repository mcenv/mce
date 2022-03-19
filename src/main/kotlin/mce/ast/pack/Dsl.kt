@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package mce.ast.pack

inline operator fun NbtPath.get(pattern: Nbt.Compound): NbtPath = NbtPath(nodes + NbtNode.MatchElement(pattern))

inline operator fun NbtPath.invoke(): NbtPath = NbtPath(nodes + NbtNode.AllElements)

inline operator fun NbtPath.get(index: Int): NbtPath = NbtPath(nodes + NbtNode.IndexedElement(index))

inline operator fun NbtPath.get(name: String, pattern: Nbt.Compound): NbtPath = NbtPath(nodes + NbtNode.MatchObject(name, pattern))

inline operator fun NbtPath.get(name: String): NbtPath = NbtPath(nodes + NbtNode.CompoundChild(name))

inline fun Append(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = Command.InsertAtIndex(target, path, -1, source)

inline fun Prepend(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = Command.InsertAtIndex(target, path, 0, source)
