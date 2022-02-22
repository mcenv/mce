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

object Defunctionalized {
    sealed class Item {
        abstract val imports: KList<KString>
        abstract val exports: KList<KString>
        abstract val name: KString

        data class Def(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val name: KString,
            val parameters: KList<Parameter>,
            val body: Term
        ) : Item()
    }

    data class Parameter(val relevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val type: Term)

    sealed class Term {
        abstract val id: Id

        data class Var(val name: KString, val level: KInt, override val id: Id) : Term()
        data class TaggedVar(val name: KString, val level: KInt, val tag: Term, override val id: Id) : Term()
        data class Def(val name: KString, override val id: Id) : Term()
        data class Let(val name: KString, val init: Term, val body: Term, override val id: Id) : Term()
        data class Match(val scrutinee: Term, val clauses: KList<Pair<Pattern, Term>>, override val id: Id) : Term()
        data class BoolOf(val value: KBoolean, override val id: Id) : Term()
        data class ByteOf(val value: KByte, override val id: Id) : Term()
        data class ShortOf(val value: KShort, override val id: Id) : Term()
        data class IntOf(val value: KInt, override val id: Id) : Term()
        data class LongOf(val value: KLong, override val id: Id) : Term()
        data class FloatOf(val value: KFloat, override val id: Id) : Term()
        data class DoubleOf(val value: KDouble, override val id: Id) : Term()
        data class StringOf(val value: KString, override val id: Id) : Term()
        data class ByteArrayOf(val elements: KList<Term>, override val id: Id) : Term()
        data class IntArrayOf(val elements: KList<Term>, override val id: Id) : Term()
        data class LongArrayOf(val elements: KList<Term>, override val id: Id) : Term()
        data class ListOf(val elements: KList<Term>, override val id: Id) : Term()
        data class CompoundOf(val elements: KList<Term>, override val id: Id) : Term()
        data class RefOf(val element: Term, override val id: Id) : Term()
        data class Refl(override val id: Id) : Term()
        data class FunOf(val tag: KInt, override val id: Id) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id) : Term()
        data class Union(val variants: KList<Term>, override val id: Id) : Term()
        data class Intersection(val variants: KList<Term>, override val id: Id) : Term()
        data class Bool(override val id: Id) : Term()
        data class Byte(override val id: Id) : Term()
        data class Short(override val id: Id) : Term()
        data class Int(override val id: Id) : Term()
        data class Long(override val id: Id) : Term()
        data class Float(override val id: Id) : Term()
        data class Double(override val id: Id) : Term()
        data class String(override val id: Id) : Term()
        data class ByteArray(override val id: Id) : Term()
        data class IntArray(override val id: Id) : Term()
        data class LongArray(override val id: Id) : Term()
        data class List(val element: Term, override val id: Id) : Term()
        data class Compound(val elements: KList<Pair<KString, Term>>, override val id: Id) : Term()
        data class Ref(val element: Term, override val id: Id) : Term()
        data class Eq(val left: Term, val right: Term, override val id: Id) : Term()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: Set<Effect>, override val id: Id) : Term()
        data class Type(override val id: Id) : Term()
    }

    sealed class Pattern {
        abstract val id: Id

        data class Var(val name: KString, override val id: Id) : Pattern()
        data class BoolOf(val value: KBoolean, override val id: Id) : Pattern()
        data class ByteOf(val value: KByte, override val id: Id) : Pattern()
        data class ShortOf(val value: KShort, override val id: Id) : Pattern()
        data class IntOf(val value: KInt, override val id: Id) : Pattern()
        data class LongOf(val value: KLong, override val id: Id) : Pattern()
        data class FloatOf(val value: KFloat, override val id: Id) : Pattern()
        data class DoubleOf(val value: KDouble, override val id: Id) : Pattern()
        data class StringOf(val value: KString, override val id: Id) : Pattern()
        data class ByteArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()
        data class IntArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()
        data class LongArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()
        data class ListOf(val elements: KList<Pattern>, override val id: Id) : Pattern()
        data class CompoundOf(val elements: KList<Pattern>, override val id: Id) : Pattern()
        data class RefOf(val element: Pattern, override val id: Id) : Pattern()
        data class Refl(override val id: Id) : Pattern()
    }

    sealed class Effect {
        data class Name(val name: KString) : Effect()
    }
}
