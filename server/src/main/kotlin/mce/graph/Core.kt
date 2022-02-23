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

/**
 * A core representation.
 */
object Core {
    /**
     * A top-level object.
     */
    sealed class Item {
        abstract val imports: KList<KString>
        abstract val exports: KList<KString>
        abstract val modifiers: KSet<Modifier>
        abstract val name: KString

        data class Def(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val modifiers: KSet<Modifier>,
            override val name: KString,
            val parameters: KList<Parameter>,
            val resultant: Value,
            val effects: KSet<Effect>,
            val body: Term,
        ) : Item()
    }

    enum class Modifier {
        BUILTIN,
        META
    }

    data class Parameter(val relevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val type: Term)

    /**
     * A syntactic term.
     */
    sealed class Term {
        abstract val id: Id?

        data class Hole(override val id: Id?) : Term()
        data class Meta(val index: KInt, override val id: Id?) : Term()
        data class Var(val name: KString, val level: KInt, override val id: Id?) : Term()
        data class Def(val name: KString, val arguments: KList<Term>, override val id: Id?) : Term()
        data class Let(val name: KString, val init: Term, val body: Term, override val id: Id?) : Term()
        data class Match(val scrutinee: Term, val clauses: KList<Pair<Pattern, Term>>, override val id: Id?) : Term()
        data class BoolOf(val value: KBoolean, override val id: Id?) : Term()
        data class ByteOf(val value: KByte, override val id: Id?) : Term()
        data class ShortOf(val value: KShort, override val id: Id?) : Term()
        data class IntOf(val value: KInt, override val id: Id?) : Term()
        data class LongOf(val value: KLong, override val id: Id?) : Term()
        data class FloatOf(val value: KFloat, override val id: Id?) : Term()
        data class DoubleOf(val value: KDouble, override val id: Id?) : Term()
        data class StringOf(val value: KString, override val id: Id?) : Term()
        data class ByteArrayOf(val elements: KList<Term>, override val id: Id?) : Term()
        data class IntArrayOf(val elements: KList<Term>, override val id: Id?) : Term()
        data class LongArrayOf(val elements: KList<Term>, override val id: Id?) : Term()
        data class ListOf(val elements: KList<Term>, override val id: Id?) : Term()
        data class CompoundOf(val elements: KList<Term>, override val id: Id?) : Term()
        data class BoxOf(val content: Term, val tag: Term, override val id: Id?) : Term()
        data class RefOf(val element: Term, override val id: Id?) : Term()
        data class Refl(override val id: Id?) : Term()
        data class FunOf(val parameters: KList<KString>, val body: Term, override val id: Id?) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id?) : Term()
        data class CodeOf(val element: Term, override val id: Id?) : Term()
        data class Splice(val element: Term, override val id: Id?) : Term()
        data class Union(val variants: KList<Term>, override val id: Id?) : Term()
        data class Intersection(val variants: KList<Term>, override val id: Id?) : Term()
        data class Bool(override val id: Id?) : Term()
        data class Byte(override val id: Id?) : Term()
        data class Short(override val id: Id?) : Term()
        data class Int(override val id: Id?) : Term()
        data class Long(override val id: Id?) : Term()
        data class Float(override val id: Id?) : Term()
        data class Double(override val id: Id?) : Term()
        data class String(override val id: Id?) : Term()
        data class ByteArray(override val id: Id?) : Term()
        data class IntArray(override val id: Id?) : Term()
        data class LongArray(override val id: Id?) : Term()
        data class List(val element: Term, override val id: Id?) : Term()
        data class Compound(val elements: KList<Pair<KString, Term>>, override val id: Id?) : Term()
        data class Box(val content: Term, override val id: Id?) : Term()
        data class Ref(val element: Term, override val id: Id?) : Term()
        data class Eq(val left: Term, val right: Term, override val id: Id?) : Term()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: KSet<Effect>, override val id: Id?) : Term()
        data class Code(val element: Term, override val id: Id?) : Term()
        data class Type(override val id: Id?) : Term()
    }

    /**
     * A syntactic pattern.
     */
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
        data class BoxOf(val content: Pattern, val tag: Pattern, override val id: Id) : Pattern()
        data class RefOf(val element: Pattern, override val id: Id) : Pattern()
        data class Refl(override val id: Id) : Pattern()
    }

    /**
     * A semantic object.
     */
    sealed class Value {
        abstract val id: Id?

        data class Hole(override val id: Id? = null) : Value()
        data class Meta(val index: KInt, override val id: Id? = null) : Value()
        data class Var(val name: KString, val level: KInt, override val id: Id? = null) : Value()
        data class Def(val name: KString, val arguments: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class Match(val scrutinee: Value, val clauses: KList<Pair<Pattern, Lazy<Value>>>, override val id: Id? = null) : Value()
        data class BoolOf(val value: KBoolean, override val id: Id? = null) : Value()
        data class ByteOf(val value: KByte, override val id: Id? = null) : Value()
        data class ShortOf(val value: KShort, override val id: Id? = null) : Value()
        data class IntOf(val value: KInt, override val id: Id? = null) : Value()
        data class LongOf(val value: KLong, override val id: Id? = null) : Value()
        data class FloatOf(val value: KFloat, override val id: Id? = null) : Value()
        data class DoubleOf(val value: KDouble, override val id: Id? = null) : Value()
        data class StringOf(val value: KString, override val id: Id? = null) : Value()
        data class ByteArrayOf(val elements: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class IntArrayOf(val elements: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class LongArrayOf(val elements: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class ListOf(val elements: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class CompoundOf(val elements: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class BoxOf(val content: Lazy<Value>, val tag: Lazy<Value>, override val id: Id? = null) : Value()
        data class RefOf(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Refl(override val id: Id? = null) : Value()
        data class FunOf(val parameters: KList<KString>, val body: Term, override val id: Id? = null) : Value()
        data class Apply(val function: Value, val arguments: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class CodeOf(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Splice(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Union(val variants: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class Intersection(val variants: KList<Lazy<Value>>, override val id: Id? = null) : Value()
        data class Bool(override val id: Id? = null) : Value()
        data class Byte(override val id: Id? = null) : Value()
        data class Short(override val id: Id? = null) : Value()
        data class Int(override val id: Id? = null) : Value()
        data class Long(override val id: Id? = null) : Value()
        data class Float(override val id: Id? = null) : Value()
        data class Double(override val id: Id? = null) : Value()
        data class String(override val id: Id? = null) : Value()
        data class ByteArray(override val id: Id? = null) : Value()
        data class IntArray(override val id: Id? = null) : Value()
        data class LongArray(override val id: Id? = null) : Value()
        data class List(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Compound(val elements: KList<Pair<KString, Term>>, override val id: Id? = null) : Value()
        data class Box(val content: Lazy<Value>, override val id: Id? = null) : Value()
        data class Ref(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Eq(val left: Lazy<Value>, val right: Lazy<Value>, override val id: Id? = null) : Value()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: KSet<Effect>, override val id: Id? = null) : Value()
        data class Code(val element: Lazy<Value>, override val id: Id? = null) : Value()
        data class Type(override val id: Id? = null) : Value()
    }

    sealed class Effect {
        data class Name(val name: KString) : Effect()
    }
}
