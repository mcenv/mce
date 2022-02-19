package mce.interpreter

import kotlin.math.floor
import kotlin.Byte as KByte
import kotlin.ByteArray as KByteArray
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.IntArray as KIntArray
import kotlin.Long as KLong
import kotlin.LongArray as KLongArray
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

    sealed class Collection : MutableNbt()

    data class ByteArray(val data: KByteArray) : Collection() {
        override fun equals(other: Any?): Boolean = this === other || other is ByteArray && data contentEquals other.data
        override fun hashCode(): KInt = data.contentHashCode()
    }

    data class IntArray(val data: KIntArray) : Collection() {
        override fun equals(other: Any?): Boolean = this === other || other is IntArray && data contentEquals other.data
        override fun hashCode(): KInt = data.contentHashCode()
    }

    data class LongArray(val data: KLongArray) : Collection() {
        override fun equals(other: Any?): Boolean = this === other || other is LongArray && data contentEquals other.data
        override fun hashCode(): KInt = data.contentHashCode()
    }

    data class List(val elements: MutableList<MutableNbt>) : Collection()

    data class String(val data: KString) : MutableNbt()

    data class Compound(val elements: MutableMap<KString, MutableNbt>) : MutableNbt()
}
