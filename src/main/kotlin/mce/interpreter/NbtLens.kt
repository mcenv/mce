package mce.interpreter

import mce.ast.Packed as P

object NbtLens {
    fun P.NbtPath.get(target: MutableNbt): List<MutableNbt> =
        nodes.fold(mutableListOf(target)) { context, node ->
            context.onEach {
                it.get(node, context)
                if (context.isEmpty()) {
                    throw Exception()
                }
            }
        }

    fun P.NbtPath.countMatching(target: MutableNbt): Int =
        nodes.fold(mutableListOf(target)) { context, node ->
            context.onEach {
                it.get(node, context)
                if (context.isEmpty()) {
                    return 0
                }
            }
        }.size

    private fun P.NbtPath.getOrCreateParents(target: MutableNbt): List<MutableNbt> =
        nodes.windowed(2).fold(mutableListOf(target)) { context, (left, right) ->
            context.onEach { it.getOrCreate(left, lazy { right.createPreferredParent() }, context) }
        }

    fun P.NbtPath.getOrCreate(target: MutableNbt, source: Lazy<MutableNbt>): List<MutableNbt> =
        mutableListOf<MutableNbt>().also { context ->
            getOrCreateParents(target).onEach {
                it.getOrCreate(nodes.last(), source, context)
                if (context.isEmpty()) {
                    throw Exception()
                }
            }
        }

    fun P.NbtPath.set(target: MutableNbt, source: Lazy<MutableNbt>): Int =
        getOrCreateParents(target).sumOf { it.set(nodes.last(), source) }

    fun P.NbtPath.remove(target: MutableNbt): Int =
        nodes.dropLast(1).fold(mutableListOf(target)) { context, node ->
            context.onEach { it.get(node, context) }
        }.sumOf { it.remove(nodes.last()) }

    private fun MutableNbt.get(node: P.NbtNode, context: MutableList<MutableNbt>) {
        when (node) {
            is P.NbtNode.MatchRootObject -> if (this is CompoundNbt && node.pattern match this) {
                context.add(this)
            }
            is P.NbtNode.MatchElement -> if (this is ListNbt) {
                context.addAll(filter { node.pattern match it })
            }
            is P.NbtNode.AllElements -> if (this is CollectionNbt<*>) {
                context.addAll(this)
            }
            is P.NbtNode.IndexedElement -> if (this is CollectionNbt<*>) {
                getOrNull(normalize(this, node.index))?.let { context.add(it) }
            }
            is P.NbtNode.MatchObject -> if (this is CompoundNbt) {
                this[node.name]?.takeIf { node.pattern match it }?.let { context.add(it) }
            }
            is P.NbtNode.CompoundChild -> if (this is CompoundNbt) {
                this[node.name]?.let { context.add(it) }
            }
        }
    }

    private fun MutableNbt.getOrCreate(node: P.NbtNode, source: Lazy<MutableNbt>, context: MutableList<MutableNbt>) {
        when (node) {
            is P.NbtNode.MatchRootObject -> get(node, context)
            is P.NbtNode.MatchElement -> if (this is ListNbt) {
                filter { node.pattern match it }.let {
                    context.addAll(it)
                    if (it.isEmpty()) {
                        val pattern = node.pattern.toMutableNbt()
                        this.add(pattern)
                        context.add(pattern)
                    }
                }
            }
            is P.NbtNode.AllElements -> if (this is CollectionNbt<*>) {
                if (isEmpty()) {
                    if (this.addNbt(0, source.value)) {
                        context.add(source.value)
                    }
                }
                context.addAll(this)
            }
            is P.NbtNode.IndexedElement -> get(node, context)
            is P.NbtNode.MatchObject -> if (this is CompoundNbt) {
                context.add(this[node.name]?.takeIf { node.pattern match it } ?: node.pattern.toMutableNbt().also { this[node.name] = it })
            }
            is P.NbtNode.CompoundChild -> if (this is CompoundNbt) {
                context.add(this[node.name] ?: source.value.also { this[node.name] = it })
            }
        }
    }

    private fun P.NbtNode.createPreferredParent(): MutableNbt = when (this) {
        is P.NbtNode.MatchRootObject -> CompoundNbt(mutableMapOf())
        is P.NbtNode.MatchElement -> ListNbt(mutableListOf(), null)
        is P.NbtNode.AllElements -> ListNbt(mutableListOf(), null)
        is P.NbtNode.IndexedElement -> ListNbt(mutableListOf(), null)
        is P.NbtNode.MatchObject -> CompoundNbt(mutableMapOf())
        is P.NbtNode.CompoundChild -> CompoundNbt(mutableMapOf())
    }

    private fun MutableNbt.set(node: P.NbtNode, source: Lazy<MutableNbt>): Int {
        return when (node) {
            is P.NbtNode.MatchRootObject -> 0
            is P.NbtNode.MatchElement -> if (this is ListNbt) {
                if (isEmpty()) {
                    add(source.value)
                    1
                } else {
                    filterIndexed { index, nbt -> node.pattern match nbt && source.value != nbt && setNbt(index, nbt) }.size
                }
            } else {
                0
            }
            is P.NbtNode.AllElements -> if (this is CollectionNbt<*>) {
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
            is P.NbtNode.IndexedElement -> {
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
            is P.NbtNode.MatchObject -> {
                if (this is CompoundNbt) {
                    val nbt = this[node.name]
                    if (nbt != null && node.pattern match nbt && source.value != nbt) {
                        this[node.name] = source.value
                        return 1
                    }
                }
                0
            }
            is P.NbtNode.CompoundChild -> {
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

    private fun MutableNbt.remove(node: P.NbtNode): Int {
        return when (node) {
            is P.NbtNode.MatchRootObject -> 0
            is P.NbtNode.MatchElement -> if (this is ListNbt) {
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
            is P.NbtNode.AllElements -> if (this is ListNbt) {
                size.also { clear() }
            } else {
                0
            }
            is P.NbtNode.IndexedElement -> {
                if (this is CollectionNbt<*>) {
                    val index = normalize(this, node.index)
                    if ((0..lastIndex).contains(index)) {
                        removeAt(index)
                        return 1
                    }
                }
                0
            }
            is P.NbtNode.MatchObject -> {
                if (this is CompoundNbt) {
                    this[node.name]?.takeIf { node.pattern match it }?.let {
                        remove(node.name)
                        return 1
                    }
                }
                0
            }
            is P.NbtNode.CompoundChild -> if (this is CompoundNbt && containsKey(node.name)) {
                remove(node.name)
                1
            } else {
                0
            }
        }
    }

    private infix fun P.Nbt.match(nbt: MutableNbt): Boolean = when (this) {
        is P.Nbt.Byte -> nbt is ByteNbt && data == nbt.data
        is P.Nbt.Short -> nbt is ShortNbt && data == nbt.data
        is P.Nbt.Int -> nbt is IntNbt && data == nbt.data
        is P.Nbt.Long -> nbt is LongNbt && data == nbt.data
        is P.Nbt.Float -> nbt is FloatNbt && data == nbt.data
        is P.Nbt.Double -> nbt is DoubleNbt && data == nbt.data
        is P.Nbt.ByteArray -> nbt is ByteArrayNbt && elements == nbt.elements
        is P.Nbt.String -> nbt is StringNbt && data == nbt.data
        is P.Nbt.List -> nbt is ListNbt && (elements.isEmpty() && nbt.elements.isEmpty() || elements.all { left -> nbt.elements.any { right -> left match right } })
        is P.Nbt.Compound -> nbt is CompoundNbt && elements.all { left -> nbt.elements[left.key]?.let { right -> left.value match right } ?: false }
        is P.Nbt.IntArray -> nbt is IntArrayNbt && elements == nbt.elements
        is P.Nbt.LongArray -> nbt is LongArrayNbt && elements == nbt.elements
    }

    private fun normalize(collection: CollectionNbt<*>, index: Int): Int = if (index < 0) collection.size + index else index
}
