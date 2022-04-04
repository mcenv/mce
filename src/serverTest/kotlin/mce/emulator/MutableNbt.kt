package mce.emulator

import mce.pass.backend.pack.Nbt
import mce.pass.backend.pack.NbtType

sealed class MutableNbt {
    abstract val type: NbtType
    abstract fun clone(): MutableNbt
}

sealed class NumericNbt : MutableNbt() {
    abstract fun toByte(): Byte
    abstract fun toShort(): Short
    abstract fun toInt(): Int
    abstract fun toLong(): Long
    abstract fun toFloat(): Float
    abstract fun toDouble(): Double
}

data class ByteNbt(val data: Byte) : NumericNbt() {
    override val type: NbtType = NbtType.BYTE
    override fun clone(): ByteNbt = copy()
    override fun toByte(): Byte = data
    override fun toShort(): Short = data.toShort()
    override fun toInt(): Int = data.toInt()
    override fun toLong(): Long = data.toLong()
    override fun toFloat(): Float = data.toFloat()
    override fun toDouble(): Double = data.toDouble()
}

data class ShortNbt(val data: Short) : NumericNbt() {
    override val type: NbtType = NbtType.SHORT
    override fun clone(): ShortNbt = copy()
    override fun toByte(): Byte = data.toInt().and(0xff).toByte()
    override fun toShort(): Short = data
    override fun toInt(): Int = data.toInt()
    override fun toLong(): Long = data.toLong()
    override fun toFloat(): Float = data.toFloat()
    override fun toDouble(): Double = data.toDouble()
}

data class IntNbt(val data: Int) : NumericNbt() {
    override val type: NbtType = NbtType.INT
    override fun clone(): IntNbt = copy()
    override fun toByte(): Byte = data.and(0xff).toByte()
    override fun toShort(): Short = data.and(0xffff).toShort()
    override fun toInt(): Int = data
    override fun toLong(): Long = data.toLong()
    override fun toFloat(): Float = data.toFloat()
    override fun toDouble(): Double = data.toDouble()
}

data class LongNbt(val data: Long) : NumericNbt() {
    override val type: NbtType = NbtType.LONG
    override fun clone(): LongNbt = copy()
    override fun toByte(): Byte = data.and(0xff).toInt().toByte()
    override fun toShort(): Short = data.and(0xffff).toInt().toShort()
    override fun toInt(): Int = data.toInt()
    override fun toLong(): Long = data
    override fun toFloat(): Float = data.toFloat()
    override fun toDouble(): Double = data.toDouble()
}

data class FloatNbt(val data: Float) : NumericNbt() {
    override val type: NbtType = NbtType.FLOAT
    override fun clone(): FloatNbt = copy()
    override fun toByte(): Byte = floor(data).and(0xff).toByte()
    override fun toShort(): Short = floor(data).and(0xffff).toShort()
    override fun toInt(): Int = floor(data)
    override fun toLong(): Long = data.toLong()
    override fun toFloat(): Float = data
    override fun toDouble(): Double = data.toDouble()
}

data class DoubleNbt(val data: Double) : NumericNbt() {
    override val type: NbtType = NbtType.DOUBLE
    override fun clone(): DoubleNbt = copy()
    override fun toByte(): Byte = floor(data).and(0xff).toByte()
    override fun toShort(): Short = floor(data).and(0xffff).toShort()
    override fun toInt(): Int = floor(data)
    override fun toLong(): Long = kotlin.math.floor(data).toLong()
    override fun toFloat(): Float = data.toFloat()
    override fun toDouble(): Double = data
}

sealed class CollectionNbt<T : MutableNbt>(elements: MutableList<T>) : MutableNbt(), MutableList<T> by elements {
    abstract fun setNbt(index: Int, nbt: MutableNbt): Boolean
    abstract fun addNbt(index: Int, nbt: MutableNbt): Boolean
}

data class ByteArrayNbt(val elements: MutableList<ByteNbt>) : CollectionNbt<ByteNbt>(elements) {
    override val type: NbtType = NbtType.BYTE_ARRAY

    override fun clone(): ByteArrayNbt = copy()

    override fun setNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements[index] = ByteNbt(nbt.toByte())
            true
        }
        else -> false
    }

    override fun addNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements.add(index, ByteNbt(nbt.toByte()))
            true
        }
        else -> false
    }
}

data class IntArrayNbt(val elements: MutableList<IntNbt>) : CollectionNbt<IntNbt>(elements) {
    override val type: NbtType = NbtType.INT_ARRAY

    override fun clone(): IntArrayNbt = copy()

    override fun setNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements[index] = IntNbt(nbt.toInt())
            true
        }
        else -> false
    }

    override fun addNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements.add(index, IntNbt(nbt.toInt()))
            true
        }
        else -> false
    }
}

data class LongArrayNbt(val elements: MutableList<LongNbt>) : CollectionNbt<LongNbt>(elements) {
    override val type: NbtType = NbtType.LONG_ARRAY

    override fun clone(): LongArrayNbt = copy()

    override fun setNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements[index] = LongNbt(nbt.toLong())
            true
        }
        else -> false
    }

    override fun addNbt(index: Int, nbt: MutableNbt): Boolean = when (nbt) {
        is NumericNbt -> {
            elements.add(index, LongNbt(nbt.toLong()))
            true
        }
        else -> false
    }
}

data class ListNbt(val elements: MutableList<MutableNbt>, private var elementType: NbtType?) : CollectionNbt<MutableNbt>(elements) {
    override val type: NbtType = NbtType.LIST

    override fun clone(): ListNbt = copy()

    override fun setNbt(index: Int, nbt: MutableNbt): Boolean = if (updateType(nbt)) {
        elements[index] = nbt
        true
    } else false

    override fun addNbt(index: Int, nbt: MutableNbt): Boolean = if (updateType(nbt)) {
        elements.add(index, nbt)
        true
    } else false

    private fun updateType(nbt: MutableNbt): Boolean = when (elementType) {
        null -> {
            elementType = nbt.type
            true
        }
        else -> elementType == nbt.type
    }
}

data class StringNbt(val data: String) : MutableNbt() {
    override val type: NbtType = NbtType.STRING
    override fun clone(): StringNbt = copy()
}

data class CompoundNbt(val elements: MutableMap<String, MutableNbt>) : MutableNbt(), MutableMap<String, MutableNbt> by elements {
    override val type: NbtType = NbtType.COMPOUND
    override fun clone(): CompoundNbt = copy()

    fun merge(other: CompoundNbt): CompoundNbt = apply {
        other.forEach { (key, value) ->
            val target = this[key]
            if (value is CompoundNbt && target is CompoundNbt) {
                target.merge(value)
            } else {
                this[key] = value.clone()
            }
        }
    }
}

fun Nbt.toMutableNbt(): MutableNbt = when (this) {
    is Nbt.Byte -> ByteNbt(data)
    is Nbt.Short -> ShortNbt(data)
    is Nbt.Int -> IntNbt(data)
    is Nbt.Long -> LongNbt(data)
    is Nbt.Float -> FloatNbt(data)
    is Nbt.Double -> DoubleNbt(data)
    is Nbt.ByteArray -> ByteArrayNbt(elements.map { ByteNbt(it) }.toMutableList())
    is Nbt.String -> StringNbt(data)
    is Nbt.List -> elements.map { it.toMutableNbt() }.toMutableList().let { ListNbt(it, it.firstOrNull()?.type) }
    is Nbt.Compound -> CompoundNbt(elements.mapValues { it.value.toMutableNbt() }.toMutableMap())
    is Nbt.IntArray -> IntArrayNbt(elements.map { IntNbt(it) }.toMutableList())
    is Nbt.LongArray -> LongArrayNbt(elements.map { LongNbt(it) }.toMutableList())
}
