package mce.emulator

import mce.phase.backend.pack.Nbt
import mce.phase.backend.pack.NbtNode
import mce.phase.backend.pack.NbtPath

object NbtLens {
    fun NbtPath.get(target: MutableNbt): List<MutableNbt> =
        nodes.fold(mutableListOf(target)) { context, node ->
            mutableListOf<MutableNbt>().also { nbts ->
                context.forEach {
                    it.get(node, nbts)
                    if (nbts.isEmpty()) {
                        throw Exception()
                    }
                }
            }
        }

    fun NbtPath.countMatching(target: MutableNbt): Int =
        nodes.fold(mutableListOf(target)) { context, node ->
            mutableListOf<MutableNbt>().also { nbts ->
                context.forEach {
                    it.get(node, nbts)
                    if (nbts.isEmpty()) {
                        return 0
                    }
                }
            }
        }.size

    private fun NbtPath.getOrCreateParents(target: MutableNbt): List<MutableNbt> =
        nodes.windowed(2).fold(mutableListOf(target)) { context, (left, right) ->
            context.onEach { it.getOrCreate(left, lazy { right.createPreferredParent() }, context) }
        }

    fun NbtPath.getOrCreate(target: MutableNbt, source: Lazy<MutableNbt>): List<MutableNbt> =
        mutableListOf<MutableNbt>().also { context ->
            getOrCreateParents(target).onEach {
                it.getOrCreate(nodes.last(), source, context)
                if (context.isEmpty()) {
                    throw Exception()
                }
            }
        }

    fun NbtPath.set(target: MutableNbt, source: Lazy<MutableNbt>): Int =
        getOrCreateParents(target).sumOf { it.set(nodes.last(), source) }

    fun NbtPath.remove(target: MutableNbt): Int =
        nodes.dropLast(1).fold(mutableListOf(target)) { context, node ->
            mutableListOf<MutableNbt>().also { nbts -> context.forEach { it.get(node, nbts) } }
        }.sumOf { it.remove(nodes.last()) }

    private fun MutableNbt.get(node: NbtNode, context: MutableList<MutableNbt>) {
        when (node) {
            is NbtNode.MatchRootObject -> if (this is CompoundNbt && node.pattern match this) {
                context.add(this)
            }
            is NbtNode.MatchElement -> if (this is ListNbt) {
                context.addAll(filter { node.pattern match it })
            }
            is NbtNode.AllElements -> if (this is CollectionNbt<*>) {
                context.addAll(this)
            }
            is NbtNode.IndexedElement -> if (this is CollectionNbt<*>) {
                getOrNull(normalize(this, node.index))?.let { context.add(it) }
            }
            is NbtNode.MatchObject -> if (this is CompoundNbt) {
                this[node.name]?.takeIf { node.pattern match it }?.let { context.add(it) }
            }
            is NbtNode.CompoundChild -> if (this is CompoundNbt) {
                this[node.name]?.let { context.add(it) }
            }
        }
    }

    private fun MutableNbt.getOrCreate(node: NbtNode, source: Lazy<MutableNbt>, context: MutableList<MutableNbt>) {
        when (node) {
            is NbtNode.MatchRootObject -> get(node, context)
            is NbtNode.MatchElement -> if (this is ListNbt) {
                filter { node.pattern match it }.let {
                    context.addAll(it)
                    if (it.isEmpty()) {
                        val pattern = node.pattern.toMutableNbt()
                        this.add(pattern)
                        context.add(pattern)
                    }
                }
            }
            is NbtNode.AllElements -> if (this is CollectionNbt<*>) {
                if (isEmpty()) {
                    if (this.addNbt(0, source.value)) {
                        context.add(source.value)
                    }
                }
                context.addAll(this)
            }
            is NbtNode.IndexedElement -> get(node, context)
            is NbtNode.MatchObject -> if (this is CompoundNbt) {
                context.add(this[node.name]?.takeIf { node.pattern match it } ?: node.pattern.toMutableNbt().also { this[node.name] = it })
            }
            is NbtNode.CompoundChild -> if (this is CompoundNbt) {
                context.add(this[node.name] ?: source.value.also { this[node.name] = it })
            }
        }
    }

    private fun NbtNode.createPreferredParent(): MutableNbt = when (this) {
        is NbtNode.MatchRootObject -> CompoundNbt(mutableMapOf())
        is NbtNode.MatchElement -> ListNbt(mutableListOf(), null)
        is NbtNode.AllElements -> ListNbt(mutableListOf(), null)
        is NbtNode.IndexedElement -> ListNbt(mutableListOf(), null)
        is NbtNode.MatchObject -> CompoundNbt(mutableMapOf())
        is NbtNode.CompoundChild -> CompoundNbt(mutableMapOf())
    }

    private fun MutableNbt.set(node: NbtNode, source: Lazy<MutableNbt>): Int {
        return when (node) {
            is NbtNode.MatchRootObject -> 0
            is NbtNode.MatchElement -> if (this is ListNbt) {
                if (isEmpty()) {
                    add(source.value)
                    1
                } else {
                    filterIndexed { index, nbt -> node.pattern match nbt && source.value != nbt && setNbt(index, nbt) }.size
                }
            } else {
                0
            }
            is NbtNode.AllElements -> if (this is CollectionNbt<*>) {
                if (isEmpty()) {
                    addNbt(0, source.value)
                    1
                } else {
                    val diff = filterNot { it == source.value }.size
                    if (diff == 0) {
                        0
                    } else {
                        clear()
                        if (addNbt(0, source.value)) {
                            for (index in 1 until size) {
                                addNbt(index, source.value)
                            }
                            diff
                        } else {
                            0
                        }
                    }
                }
            } else {
                0
            }
            is NbtNode.IndexedElement -> {
                if (this is CollectionNbt<*>) {
                    val index = normalize(this, node.index)
                    if ((0..lastIndex).contains(index)) {
                        if (source.value != this[index] && setNbt(index, source.value)) {
                            return 1
                        }
                    }
                }
                0
            }
            is NbtNode.MatchObject -> {
                if (this is CompoundNbt) {
                    val nbt = this[node.name]
                    if (nbt != null && node.pattern match nbt && source.value != nbt) {
                        this[node.name] = source.value
                        return 1
                    }
                }
                0
            }
            is NbtNode.CompoundChild -> {
                if (this is CompoundNbt) {
                    val nbt = put(node.name, source.value)
                    if (source.value != nbt) {
                        return 1
                    }
                }
                0
            }
        }
    }

    private fun MutableNbt.remove(node: NbtNode): Int {
        return when (node) {
            is NbtNode.MatchRootObject -> 0
            is NbtNode.MatchElement -> if (this is ListNbt) {
                val size = size
                for (index in lastIndex downTo 0) {
                    if (node.pattern match this[index]) {
                        removeAt(index)
                    }
                }
                size - this.size
            } else {
                0
            }
            is NbtNode.AllElements -> if (this is ListNbt) {
                size.also { clear() }
            } else {
                0
            }
            is NbtNode.IndexedElement -> {
                if (this is CollectionNbt<*>) {
                    val index = normalize(this, node.index)
                    if ((0..lastIndex).contains(index)) {
                        removeAt(index)
                        return 1
                    }
                }
                0
            }
            is NbtNode.MatchObject -> {
                if (this is CompoundNbt) {
                    this[node.name]?.takeIf { node.pattern match it }?.let {
                        remove(node.name)
                        return 1
                    }
                }
                0
            }
            is NbtNode.CompoundChild -> if (this is CompoundNbt && containsKey(node.name)) {
                remove(node.name)
                1
            } else {
                0
            }
        }
    }

    private infix fun Nbt.match(nbt: MutableNbt): Boolean = when (this) {
        is Nbt.Byte -> nbt is ByteNbt && data == nbt.data
        is Nbt.Short -> nbt is ShortNbt && data == nbt.data
        is Nbt.Int -> nbt is IntNbt && data == nbt.data
        is Nbt.Long -> nbt is LongNbt && data == nbt.data
        is Nbt.Float -> nbt is FloatNbt && data == nbt.data
        is Nbt.Double -> nbt is DoubleNbt && data == nbt.data
        is Nbt.ByteArray -> nbt is ByteArrayNbt && elements == nbt.elements
        is Nbt.String -> nbt is StringNbt && data == nbt.data
        is Nbt.List -> nbt is ListNbt && (elements.isEmpty() && nbt.elements.isEmpty() || elements.all { left -> nbt.elements.any { right -> left match right } })
        is Nbt.Compound -> nbt is CompoundNbt && elements.all { left -> nbt.elements[left.key]?.let { right -> left.value match right } ?: false }
        is Nbt.IntArray -> nbt is IntArrayNbt && elements == nbt.elements
        is Nbt.LongArray -> nbt is LongArrayNbt && elements == nbt.elements
    }

    private fun normalize(collection: CollectionNbt<*>, index: Int): Int = if (index < 0) collection.size + index else index
}
