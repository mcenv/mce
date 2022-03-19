@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package mce.ast.pack

import mce.ast.pack.Command.InsertAtIndex
import mce.ast.pack.Command.RemoveData
import mce.ast.pack.NbtNode.*

inline operator fun NbtPath.get(pattern: Nbt.Compound): NbtPath = NbtPath(nodes + MatchElement(pattern))

inline operator fun NbtPath.invoke(): NbtPath = NbtPath(nodes + AllElements)

inline operator fun NbtPath.get(index: Int): NbtPath = NbtPath(nodes + IndexedElement(index))

inline operator fun NbtPath.get(name: String, pattern: Nbt.Compound): NbtPath = NbtPath(nodes + MatchObject(name, pattern))

inline operator fun NbtPath.get(name: String): NbtPath = NbtPath(nodes + CompoundChild(name))

inline fun Append(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = InsertAtIndex(target, path, -1, source)

inline fun Prepend(target: ResourceLocation, path: NbtPath, source: SourceProvider): Command = InsertAtIndex(target, path, 0, source)

inline fun Pop(target: ResourceLocation, path: NbtPath): Command = RemoveData(target, path[-1])
