package mce.interpreter

import kotlin.math.floor
import mce.graph.Packed as P
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString

sealed class MutableNbt {
    sealed class Numeric : MutableNbt() {
        abstract fun toByte(): KByte
        abstract fun toShort(): KShort
        abstract fun toInt(): KInt
        abstract fun toLong(): KLong
        abstract fun toFloat(): KFloat
        abstract fun toDouble(): KDouble
    }

    data class Byte(val data: KByte) : Numeric() {
        override fun toByte(): KByte = data
        override fun toShort(): KShort = data.toShort()
        override fun toInt(): KInt = data.toInt()
        override fun toLong(): KLong = data.toLong()
        override fun toFloat(): KFloat = data.toFloat()
        override fun toDouble(): KDouble = data.toDouble()
    }

    data class Short(val data: KShort) : Numeric() {
        override fun toByte(): KByte = data.toInt().and(0xff).toByte()
        override fun toShort(): KShort = data
        override fun toInt(): KInt = data.toInt()
        override fun toLong(): KLong = data.toLong()
        override fun toFloat(): KFloat = data.toFloat()
        override fun toDouble(): KDouble = data.toDouble()
    }

    data class Int(val data: KInt) : Numeric() {
        override fun toByte(): KByte = data.and(0xff).toByte()
        override fun toShort(): KShort = data.and(0xffff).toShort()
        override fun toInt(): KInt = data
        override fun toLong(): KLong = data.toLong()
        override fun toFloat(): KFloat = data.toFloat()
        override fun toDouble(): KDouble = data.toDouble()
    }

    data class Long(val data: KLong) : Numeric() {
        override fun toByte(): KByte = data.and(0xff).toInt().toByte()
        override fun toShort(): KShort = data.and(0xffff).toInt().toShort()
        override fun toInt(): KInt = data.toInt()
        override fun toLong(): KLong = data
        override fun toFloat(): KFloat = data.toFloat()
        override fun toDouble(): KDouble = data.toDouble()
    }

    data class Float(val data: KFloat) : Numeric() {
        override fun toByte(): KByte = Math.floor(data).and(0xff).toByte()
        override fun toShort(): KShort = Math.floor(data).and(0xffff).toShort()
        override fun toInt(): KInt = Math.floor(data)
        override fun toLong(): KLong = data.toLong()
        override fun toFloat(): KFloat = data
        override fun toDouble(): KDouble = data.toDouble()
    }

    data class Double(val data: KDouble) : Numeric() {
        override fun toByte(): KByte = Math.floor(data).and(0xff).toByte()
        override fun toShort(): KShort = Math.floor(data).and(0xffff).toShort()
        override fun toInt(): KInt = Math.floor(data)
        override fun toLong(): KLong = floor(data).toLong()
        override fun toFloat(): KFloat = data.toFloat()
        override fun toDouble(): KDouble = data
    }

    sealed class Collection<T : MutableNbt>(elements: MutableList<T>) : MutableNbt(), MutableList<T> by elements

    data class ByteArray(val elements: MutableList<Byte>) : Collection<Byte>(elements)

    data class IntArray(val elements: MutableList<Int>) : Collection<Int>(elements)

    data class LongArray(val elements: MutableList<Long>) : Collection<Long>(elements)

    data class List(val elements: MutableList<MutableNbt>) : Collection<MutableNbt>(elements)

    data class String(val data: KString) : MutableNbt()

    data class Compound(val elements: MutableMap<KString, MutableNbt>) : MutableNbt(), MutableMap<KString, MutableNbt> by elements

    companion object {
        fun P.Nbt.toMutableNbt(): MutableNbt = when (this) {
            is P.Nbt.Byte -> Byte(data)
            is P.Nbt.Short -> Short(data)
            is P.Nbt.Int -> Int(data)
            is P.Nbt.Long -> Long(data)
            is P.Nbt.Float -> Float(data)
            is P.Nbt.Double -> Double(data)
            is P.Nbt.ByteArray -> ByteArray(elements.map { Byte(it) }.toMutableList())
            is P.Nbt.String -> String(data)
            is P.Nbt.List -> List(elements.map { it.toMutableNbt() }.toMutableList())
            is P.Nbt.Compound -> Compound(elements.mapValues { it.value.toMutableNbt() }.toMutableMap())
            is P.Nbt.IntArray -> IntArray(elements.map { Int(it) }.toMutableList())
            is P.Nbt.LongArray -> LongArray(elements.map { Long(it) }.toMutableList())
        }
    }
}
