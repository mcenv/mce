package mce.graph

import java.util.*
import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

object Surface {
    sealed class Item {
        abstract val name: KString
        abstract val imports: KList<KString>

        data class Definition(
            override val name: KString,
            override val imports: KList<KString>,
            val type: Term,
            val body: Term,
        ) : Item()
    }

    sealed class Term {
        abstract val id: UUID

        data class Hole(override val id: UUID = UUID.randomUUID()) : Term()
        data class Dummy(override val id: UUID = UUID.randomUUID()) : Term()
        data class Meta(val index: KInt, override val id: UUID = UUID.randomUUID()) : Term()
        data class Name(val name: KString, override val id: UUID = UUID.randomUUID()) : Term()
        data class Let(val name: KString, val init: Term, val body: Term, override val id: UUID = UUID.randomUUID()) :
            Term()

        data class BooleanOf(val value: KBoolean, override val id: UUID = UUID.randomUUID()) : Term()
        data class ByteOf(val value: KByte, override val id: UUID = UUID.randomUUID()) : Term()
        data class ShortOf(val value: KShort, override val id: UUID = UUID.randomUUID()) : Term()
        data class IntOf(val value: KInt, override val id: UUID = UUID.randomUUID()) : Term()
        data class LongOf(val value: KLong, override val id: UUID = UUID.randomUUID()) : Term()
        data class FloatOf(val value: KFloat, override val id: UUID = UUID.randomUUID()) : Term()
        data class DoubleOf(val value: KDouble, override val id: UUID = UUID.randomUUID()) : Term()
        data class StringOf(val value: KString, override val id: UUID = UUID.randomUUID()) : Term()
        data class ByteArrayOf(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class IntArrayOf(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class LongArrayOf(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class ListOf(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class CompoundOf(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class FunctionOf(
            val parameters: KList<KString>,
            val body: Term,
            override val id: UUID = UUID.randomUUID()
        ) : Term()

        data class Apply(val function: Term, val arguments: KList<Term>, override val id: UUID = UUID.randomUUID()) :
            Term()

        data class Boolean(override val id: UUID = UUID.randomUUID()) : Term()
        data class Byte(override val id: UUID = UUID.randomUUID()) : Term()
        data class Short(override val id: UUID = UUID.randomUUID()) : Term()
        data class Int(override val id: UUID = UUID.randomUUID()) : Term()
        data class Long(override val id: UUID = UUID.randomUUID()) : Term()
        data class Float(override val id: UUID = UUID.randomUUID()) : Term()
        data class Double(override val id: UUID = UUID.randomUUID()) : Term()
        data class String(override val id: UUID = UUID.randomUUID()) : Term()
        data class ByteArray(override val id: UUID = UUID.randomUUID()) : Term()
        data class IntArray(override val id: UUID = UUID.randomUUID()) : Term()
        data class LongArray(override val id: UUID = UUID.randomUUID()) : Term()
        data class List(val element: Term, override val id: UUID = UUID.randomUUID()) : Term()
        data class Compound(val elements: KList<Term>, override val id: UUID = UUID.randomUUID()) : Term()
        data class Function(
            val parameters: KList<Pair<KString, Term>>,
            val resultant: Term,
            override val id: UUID = UUID.randomUUID()
        ) : Term()

        data class Type(override val id: UUID = UUID.randomUUID()) : Term()
    }
}
