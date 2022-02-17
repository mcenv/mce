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
import kotlin.collections.Set as KSet

object Defunctionalized {
    sealed class Item {
        abstract val imports: KList<KString>
        abstract val exports: KList<KString>
        abstract val name: KString

        data class Def(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val name: KString,
            val body: Term
        ) : Item()
    }

    sealed class Term {
        data class Var(val name: KString, val level: KInt) : Term()
        data class Def(val name: KString) : Term()
        data class Let(val name: KString, val init: Term, val body: Term) : Term()
        data class Match(val scrutinee: Term, val clauses: KList<Pair<Pattern, Term>>) : Term()
        data class BoolOf(val value: KBoolean) : Term()
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
        data class RefOf(val element: Term) : Term()
        object Refl : Term()
        data class FunOf(val tag: KInt) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>) : Term()
        data class Union(val variants: KList<Term>) : Term()
        data class Intersection(val variants: KList<Term>) : Term()
        object Bool : Term()
        object Byte : Term()
        object Short : Term()
        object Int : Term()
        object Long : Term()
        object Float : Term()
        object Double : Term()
        object String : Term()
        object ByteArray : Term()
        object IntArray : Term()
        object LongArray : Term()
        data class List(val element: Term) : Term()
        data class Compound(val elements: KList<Pair<KString, Term>>) : Term()
        data class Ref(val element: Term) : Term()
        data class Eq(val left: Term, val right: Term) : Term()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term) : Term()
        data class Thunk(val element: Term, val effects: Effects) : Term()
        object Type : Term()
    }

    sealed class Pattern {
        data class Var(val name: KString) : Pattern()
        data class BoolOf(val value: KBoolean) : Pattern()
        data class ByteOf(val value: KByte) : Pattern()
        data class ShortOf(val value: KShort) : Pattern()
        data class IntOf(val value: KInt) : Pattern()
        data class LongOf(val value: KLong) : Pattern()
        data class FloatOf(val value: KFloat) : Pattern()
        data class DoubleOf(val value: KDouble) : Pattern()
        data class StringOf(val value: KString) : Pattern()
        data class ByteArrayOf(val elements: KList<Pattern>) : Pattern()
        data class IntArrayOf(val elements: KList<Pattern>) : Pattern()
        data class LongArrayOf(val elements: KList<Pattern>) : Pattern()
        data class ListOf(val elements: KList<Pattern>) : Pattern()
        data class CompoundOf(val elements: KList<Pattern>) : Pattern()
        data class RefOf(val element: Pattern) : Pattern()
        object Refl : Pattern()
    }

    data class Parameter(val name: KString, val lower: Term?, val upper: Term?, val type: Term)

    sealed class Effect

    sealed class Effects {
        object Any : Effects()
        data class Set(val effects: KSet<Effect>) : Effects()
    }
}
