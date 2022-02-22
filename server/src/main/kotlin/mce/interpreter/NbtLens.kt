package mce.interpreter

import mce.interpreter.MutableNbt.Companion.toMutableNbt
import mce.graph.Packed as P

object NbtLens {
    fun P.NbtPath.get(target: MutableNbt): List<MutableNbt> =
        fold(mutableListOf(target)) { context, node ->
            context.onEach {
                it.get(node, context)
                if (context.isEmpty()) throw Exception()
            }
        }

    fun P.NbtPath.countMatching(target: MutableNbt): Int =
        fold(mutableListOf(target)) { context, node ->
            context.onEach {
                it.get(node, context)
                if (context.isEmpty()) return 0
            }
        }.size

    fun P.NbtPath.getOrCreateParents(target: MutableNbt): List<MutableNbt> =
        windowed(2).fold(mutableListOf(target)) { context, (left, right) ->
            context.onEach { it.getOrCreate(left, lazy { right.createPreferredParent() }, context) }
        }

    fun P.NbtPath.getOrCreate(target: MutableNbt, source: Lazy<MutableNbt>): List<MutableNbt> =
        mutableListOf<MutableNbt>().also { context ->
            getOrCreateParents(target).onEach {
                it.getOrCreate(last(), source, context)
                if (context.isEmpty()) throw Exception()
            }
        }

    fun P.NbtPath.set(target: MutableNbt, source: Lazy<MutableNbt>): Int =
        getOrCreateParents(target).sumOf { it.set(last(), source) }

    fun P.NbtPath.remove(target: MutableNbt): Int =
        dropLast(1).fold(mutableListOf(target)) { context, node ->
            context.onEach { it.get(node, context) }
        }.sumOf { it.remove(last()) }

    private fun MutableNbt.get(node: P.NbtNode, context: MutableList<MutableNbt>) {
        when (node) {
            is P.NbtNode.MatchRootObject -> if (this is MutableNbt.Compound && node.pattern match this) context += this
            is P.NbtNode.MatchElement -> if (this is MutableNbt.List) context += filter { node.pattern match it }
            is P.NbtNode.AllElements -> if (this is MutableNbt.Collection<*>) context.addAll(this)
            is P.NbtNode.IndexedElement -> if (this is MutableNbt.Collection<*>) getOrNull(normalize(this, node.index))?.let { context += it }
            is P.NbtNode.MatchObject -> if (this is MutableNbt.Compound) this[node.name]?.takeIf { node.pattern match it }?.let { context += it }
            is P.NbtNode.CompoundChild -> if (this is MutableNbt.Compound) this[node.name]?.let { context += it }
        }
    }

    private fun MutableNbt.getOrCreate(node: P.NbtNode, source: Lazy<MutableNbt>, context: MutableList<MutableNbt>) {
        when (node) {
            is P.NbtNode.MatchRootObject -> get(node, context)
            is P.NbtNode.MatchElement -> if (this is MutableNbt.List) filter { node.pattern match it }.let {
                this += node.pattern.toMutableNbt()
                context += it
            }
            is P.NbtNode.AllElements -> if (this is MutableNbt.Collection<*>) {
                if (context.isEmpty()) (this as MutableNbt.Collection<MutableNbt>) += source.value
                context.addAll(this)
            }
            is P.NbtNode.IndexedElement -> get(node, context)
            is P.NbtNode.MatchObject -> if (this is MutableNbt.Compound) context += this[node.name]?.takeIf { node.pattern match it } ?: node.pattern.toMutableNbt().also { this[node.name] = it }
            is P.NbtNode.CompoundChild -> if (this is MutableNbt.Compound) context += this[node.name] ?: source.value.also { this[node.name] = it }
        }
    }

    private fun P.NbtNode.createPreferredParent(): MutableNbt = when (this) {
        is P.NbtNode.MatchRootObject -> MutableNbt.Compound(mutableMapOf())
        is P.NbtNode.MatchElement -> MutableNbt.List(mutableListOf())
        is P.NbtNode.AllElements -> MutableNbt.List(mutableListOf())
        is P.NbtNode.IndexedElement -> MutableNbt.List(mutableListOf())
        is P.NbtNode.MatchObject -> MutableNbt.Compound(mutableMapOf())
        is P.NbtNode.CompoundChild -> MutableNbt.Compound(mutableMapOf())
    }

    private fun MutableNbt.set(node: P.NbtNode, source: Lazy<MutableNbt>): Int = when (node) {
        is P.NbtNode.MatchRootObject -> 0
        is P.NbtNode.MatchElement -> TODO()
        is P.NbtNode.AllElements -> TODO()
        is P.NbtNode.IndexedElement -> TODO()
        is P.NbtNode.MatchObject -> TODO()
        is P.NbtNode.CompoundChild -> TODO()
    }

    private fun MutableNbt.remove(node: P.NbtNode): Int = when (node) {
        is P.NbtNode.MatchRootObject -> 0
        is P.NbtNode.MatchElement -> if (this is MutableNbt.List) {
            val size = size
            for (index in lastIndex downTo 0) if (node.pattern match this[index]) removeAt(index)
            size - this.size
        } else 0
        is P.NbtNode.AllElements -> if (this is MutableNbt.List) {
            size.also { clear() }
        } else 0
        is P.NbtNode.IndexedElement -> {
            if (this is MutableNbt.Collection<*>) normalize(this, node.index).let {
                if ((0..lastIndex).contains(it)) {
                    removeAt(it)
                    return 1
                }
            }
            0
        }
        is P.NbtNode.MatchObject -> {
            if (this is MutableNbt.Compound) {
                this[node.name]?.takeIf { node.pattern match it }?.let {
                    remove(node.name)
                    return 1
                }
            }
            0
        }
        is P.NbtNode.CompoundChild -> if (this is MutableNbt.Compound && containsKey(node.name)) {
            remove(node.name)
            1
        } else 0
    }

    private infix fun P.Nbt.match(nbt: MutableNbt): Boolean = when (this) {
        is P.Nbt.Byte -> nbt is MutableNbt.Byte && data == nbt.data
        is P.Nbt.Short -> nbt is MutableNbt.Short && data == nbt.data
        is P.Nbt.Int -> nbt is MutableNbt.Int && data == nbt.data
        is P.Nbt.Long -> nbt is MutableNbt.Long && data == nbt.data
        is P.Nbt.Float -> nbt is MutableNbt.Float && data == nbt.data
        is P.Nbt.Double -> nbt is MutableNbt.Double && data == nbt.data
        is P.Nbt.ByteArray -> nbt is MutableNbt.ByteArray && elements == nbt.elements
        is P.Nbt.String -> nbt is MutableNbt.String && data == nbt.data
        is P.Nbt.List -> nbt is MutableNbt.List && (elements.isEmpty() && nbt.elements.isEmpty() || elements.all { left -> nbt.elements.any { right -> left match right } })
        is P.Nbt.Compound -> nbt is MutableNbt.Compound && elements.all { left -> nbt.elements[left.key]?.let { right -> left.value match right } ?: false }
        is P.Nbt.IntArray -> nbt is MutableNbt.IntArray && elements == nbt.elements
        is P.Nbt.LongArray -> nbt is MutableNbt.LongArray && elements == nbt.elements
    }

    private fun normalize(collection: Collection<*>, index: Int): Int = if (index < 0) collection.size + index else index
}
