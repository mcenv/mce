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
    sealed class Item {
        abstract val imports: KList<KString>
        abstract val exports: KList<KString>
        abstract val modifiers: KSet<Modifier>
        abstract val name: KString
        abstract val id: Id

        data class Def(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val modifiers: KSet<Modifier>,
            override val name: KString,
            val parameters: KList<Parameter>,
            val resultant: Term,
            val effects: KSet<Effect>,
            val body: Term,
            override val id: Id,
        ) : Item()

        data class Mod(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val modifiers: KSet<Modifier>,
            override val name: KString,
            val type: VModule,
            val body: Module,
            override val id: Id,
        ) : Item()

        data class Test(
            override val imports: KList<KString>,
            override val exports: KList<KString>,
            override val modifiers: KSet<Modifier>,
            override val name: KString,
            val body: Term,
            override val id: Id,
        ) : Item()
    }

    enum class Modifier {
        BUILTIN,
        META,
    }

    data class Parameter(val relevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val type: Term, val id: Id)

    /**
     * A syntactic module.
     */
    sealed class Module {
        abstract val id: Id?

        data class Var(val name: KString, override val id: Id?) : Module()
        data class Str(val items: KList<Item>, override val id: Id?) : Module()
        data class Sig(val signatures: KList<Signature>, override val id: Id?) : Module()
        data class Type(override val id: Id?) : Module()
    }

    /**
     * A semantic module.
     */
    sealed class VModule {
        abstract val id: Id?

        data class Var(val name: KString, override val id: Id?) : VModule()
        data class Str(val items: KList<Item>, override val id: Id?) : VModule()
        data class Sig(val signatures: KList<VSignature>, override val id: Id?) : VModule()
        data class Type(override val id: Id?) : VModule()
    }

    /**
     * A syntactic signature.
     */
    sealed class Signature {
        abstract val id: Id?

        data class Def(val name: KString, val parameters: KList<Parameter>, val resultant: Term, override val id: Id?) : Signature()
        data class Mod(val name: KString, val type: Module, override val id: Id?) : Signature()
        data class Test(val name: KString, override val id: Id?) : Signature()
    }

    /**
     * A semantic signature.
     */
    sealed class VSignature {
        abstract val id: Id?

        data class Def(val name: KString, val parameters: KList<Parameter>, val resultant: Term, override val id: Id?) : VSignature()
        data class Mod(val name: KString, val type: VModule, override val id: Id?) : VSignature()
        data class Test(val name: KString, override val id: Id?) : VSignature()
    }

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
        data class UnitOf(override val id: Id?) : Term()
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
        data class CompoundOf(val elements: LinkedHashMap<Name, Term>, override val id: Id?) : Term()
        data class BoxOf(val content: Term, val tag: Term, override val id: Id?) : Term()
        data class RefOf(val element: Term, override val id: Id?) : Term()
        data class Refl(override val id: Id?) : Term()
        data class FunOf(val parameters: KList<Name>, val body: Term, override val id: Id?) : Term()
        data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id?) : Term()
        data class CodeOf(val element: Term, override val id: Id?) : Term()
        data class Splice(val element: Term, override val id: Id?) : Term()
        data class Or(val variants: KList<Term>, override val id: Id?) : Term()
        data class And(val variants: KList<Term>, override val id: Id?) : Term()
        data class Unit(override val id: Id?) : Term()
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
        data class List(val element: Term, val size: Term, override val id: Id?) : Term()
        data class Compound(val elements: LinkedHashMap<Name, Term>, override val id: Id?) : Term()
        data class Box(val content: Term, override val id: Id?) : Term()
        data class Ref(val element: Term, override val id: Id?) : Term()
        data class Eq(val left: Term, val right: Term, override val id: Id?) : Term()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: KSet<Effect>, override val id: Id?) : Term()
        data class Code(val element: Term, override val id: Id?) : Term()
        data class Type(override val id: Id?) : Term()
    }

    /**
     * A semantic term.
     */
    sealed class VTerm {
        abstract val id: Id?

        data class Hole(override val id: Id? = null) : VTerm()
        data class Meta(val index: KInt, override val id: Id? = null) : VTerm()
        data class Var(val name: KString, val level: KInt, override val id: Id? = null) : VTerm()
        data class Def(val name: KString, val arguments: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class Match(val scrutinee: VTerm, val clauses: KList<Pair<Pattern, Lazy<VTerm>>>, override val id: Id? = null) : VTerm()
        data class UnitOf(override val id: Id? = null) : VTerm()
        data class BoolOf(val value: KBoolean, override val id: Id? = null) : VTerm()
        data class ByteOf(val value: KByte, override val id: Id? = null) : VTerm()
        data class ShortOf(val value: KShort, override val id: Id? = null) : VTerm()
        data class IntOf(val value: KInt, override val id: Id? = null) : VTerm()
        data class LongOf(val value: KLong, override val id: Id? = null) : VTerm()
        data class FloatOf(val value: KFloat, override val id: Id? = null) : VTerm()
        data class DoubleOf(val value: KDouble, override val id: Id? = null) : VTerm()
        data class StringOf(val value: KString, override val id: Id? = null) : VTerm()
        data class ByteArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class IntArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class LongArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class ListOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class CompoundOf(val elements: LinkedHashMap<Name, Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class BoxOf(val content: Lazy<VTerm>, val tag: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class RefOf(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Refl(override val id: Id? = null) : VTerm()
        data class FunOf(val parameters: KList<Name>, val body: Term, override val id: Id? = null) : VTerm()
        data class Apply(val function: VTerm, val arguments: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class CodeOf(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Splice(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Or(val variants: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class And(val variants: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm()
        data class Unit(override val id: Id? = null) : VTerm()
        data class Bool(override val id: Id? = null) : VTerm()
        data class Byte(override val id: Id? = null) : VTerm()
        data class Short(override val id: Id? = null) : VTerm()
        data class Int(override val id: Id? = null) : VTerm()
        data class Long(override val id: Id? = null) : VTerm()
        data class Float(override val id: Id? = null) : VTerm()
        data class Double(override val id: Id? = null) : VTerm()
        data class String(override val id: Id? = null) : VTerm()
        data class ByteArray(override val id: Id? = null) : VTerm()
        data class IntArray(override val id: Id? = null) : VTerm()
        data class LongArray(override val id: Id? = null) : VTerm()
        data class List(val element: Lazy<VTerm>, val size: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Compound(val elements: LinkedHashMap<Name, Term>, override val id: Id? = null) : VTerm()
        data class Box(val content: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Ref(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Eq(val left: Lazy<VTerm>, val right: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: KSet<Effect>, override val id: Id? = null) : VTerm()
        data class Code(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm()
        data class Type(override val id: Id? = null) : VTerm()
    }

    sealed class Pattern {
        abstract val id: Id

        data class Var(val name: KString, override val id: Id) : Pattern()
        data class UnitOf(override val id: Id) : Pattern()
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
        data class CompoundOf(val elements: LinkedHashMap<Name, Pattern>, override val id: Id) : Pattern()
        data class BoxOf(val content: Pattern, val tag: Pattern, override val id: Id) : Pattern()
        data class RefOf(val element: Pattern, override val id: Id) : Pattern()
        data class Refl(override val id: Id) : Pattern()
        data class Unit(override val id: Id) : Pattern()
        data class Bool(override val id: Id) : Pattern()
        data class Byte(override val id: Id) : Pattern()
        data class Short(override val id: Id) : Pattern()
        data class Int(override val id: Id) : Pattern()
        data class Long(override val id: Id) : Pattern()
        data class Float(override val id: Id) : Pattern()
        data class Double(override val id: Id) : Pattern()
        data class String(override val id: Id) : Pattern()
        data class ByteArray(override val id: Id) : Pattern()
        data class IntArray(override val id: Id) : Pattern()
        data class LongArray(override val id: Id) : Pattern()
    }

    sealed class Effect {
        data class Name(val name: KString) : Effect()
    }
}
