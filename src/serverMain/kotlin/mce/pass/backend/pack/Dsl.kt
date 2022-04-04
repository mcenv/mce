@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package mce.pass.backend.pack

import mce.pass.backend.pack.Command.RemoveData
import mce.pass.backend.pack.NbtNode.*

// TODO: optimize

inline operator fun NbtPath.get(pattern: Nbt.Compound): NbtPath = NbtPath(nodes + MatchElement(pattern))

inline operator fun NbtPath.invoke(): NbtPath = NbtPath(nodes + AllElements)

inline operator fun NbtPath.get(index: Int): NbtPath = NbtPath(nodes + IndexedElement(index))

inline operator fun NbtPath.get(name: String, pattern: Nbt.Compound): NbtPath = NbtPath(nodes + MatchObject(name, pattern))

inline operator fun NbtPath.get(name: String): NbtPath = NbtPath(nodes + CompoundChild(name))

inline fun Pop(target: ResourceLocation, path: NbtPath): Command = RemoveData(target, path[-1])
