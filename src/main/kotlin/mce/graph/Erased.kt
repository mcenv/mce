package mce.graph

import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

object Erased {
    sealed class Item {
        abstract val name: KString

        data class Definition(override val name: KString, val body: Term) : Item()
    }

    sealed class Term {
        data class Variable(val name: KString, val level: KInt) : Term()
        data class Definition(val name: KString) : Term()
        data class Let(val name: KString, val init: Term, val body: Term) : Term()
        data class BooleanOf(val value: KBoolean) : Term()
        data class ByteOf(val value: KByte) : Term()
        data class ShortOf(val value: KShort) : Term()
        data class IntOf(val value: KInt) : Term()
        data class LongOf(val value: KLong) : Term()
        data class FloatOf(val value: KFloat) : Term()
        data class DoubleOf(val value: KDouble) : Term()
        data class StringOf(val value: KString) : Term()
        data class ByteArrayOf(val elements: KList<Term>) : Term()
        data class IntArrayOf(val elements: KList<Term>) : Term()
        data class LongArrayOf(val elements: KList<Term>) : Term()
        data class ListOf(val elements: KList<Term>) : Term()
        data class CompoundOf(val elements: KList<Term>) : Term()
        data class FunctionOf(val parameters: KList<KString>, val body: Term) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>) : Term()
        object Type : Term()
    }
}
