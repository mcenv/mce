@file:UseSerializers(IdSerializer::class)

package mce.ast.surface

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import mce.ast.Id
import mce.ast.IdSerializer
import mce.ast.Name
import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

@Serializable
sealed class Item {
    abstract val imports: KList<KString>
    abstract val exports: KList<KString>
    abstract val modifiers: KList<Modifier>
    abstract val name: KString
    abstract val id: Id

    @Serializable
    data class Def(
        override val imports: KList<KString>,
        override val exports: KList<KString>,
        override val modifiers: KList<Modifier>,
        override val name: KString,
        val parameters: KList<Parameter>,
        val resultant: Term,
        val effects: KList<Effect>,
        val body: Term,
        override val id: Id,
    ) : Item()

    @Serializable
    data class Mod(
        override val imports: KList<KString>,
        override val exports: KList<KString>,
        override val modifiers: KList<Modifier>,
        override val name: KString,
        val type: Module,
        val body: Module,
        override val id: Id,
    ) : Item()

    @Serializable
    data class Test(
        override val imports: KList<KString>,
        override val exports: KList<KString>,
        override val modifiers: KList<Modifier>,
        override val name: KString,
        val body: Term,
        override val id: Id,
    ) : Item()
}

@Serializable
enum class Modifier {
    BUILTIN,
    META,
}

@Serializable
data class Parameter(val termRelevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val typeRelevant: KBoolean, val type: Term, val id: Id)

@Serializable
data class Entry(val relevant: KBoolean, val name: Name, val type: Term, val id: Id)

@Serializable
sealed class Module {
    abstract val id: Id

    @Serializable
    data class Var(val name: KString, override val id: Id) : Module()

    @Serializable
    data class Str(val items: KList<Item>, override val id: Id) : Module()

    @Serializable
    data class Sig(val signatures: KList<Signature>, override val id: Id) : Module()

    @Serializable
    data class Type(override val id: Id) : Module()
}

@Serializable
sealed class Signature {
    abstract val id: Id

    @Serializable
    data class Def(val name: KString, val parameters: KList<Parameter>, val resultant: Term, override val id: Id) : Signature()

    @Serializable
    data class Mod(val name: KString, val type: Module, override val id: Id) : Signature()

    @Serializable
    data class Test(val name: KString, override val id: Id) : Signature()
}

@Serializable
sealed class Term {
    abstract val id: Id

    @Serializable
    data class Hole(override val id: Id) : Term()

    @Serializable
    data class Meta(override val id: Id) : Term()

    @Serializable
    data class Anno(val element: Term, val type: Term, override val id: Id) : Term()

    @Serializable
    data class Var(val name: KString, override val id: Id) : Term()

    @Serializable
    data class Def(val name: KString, val arguments: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class Let(val name: KString, val init: Term, val body: Term, override val id: Id) : Term()

    @Serializable
    data class Match(val scrutinee: Term, val clauses: KList<Pair<Pattern, Term>>, override val id: Id) : Term()

    @Serializable
    data class UnitOf(override val id: Id) : Term()

    @Serializable
    data class BoolOf(val value: KBoolean, override val id: Id) : Term()

    @Serializable
    data class ByteOf(val value: KByte, override val id: Id) : Term()

    @Serializable
    data class ShortOf(val value: KShort, override val id: Id) : Term()

    @Serializable
    data class IntOf(val value: KInt, override val id: Id) : Term()

    @Serializable
    data class LongOf(val value: KLong, override val id: Id) : Term()

    @Serializable
    data class FloatOf(val value: KFloat, override val id: Id) : Term()

    @Serializable
    data class DoubleOf(val value: KDouble, override val id: Id) : Term()

    @Serializable
    data class StringOf(val value: KString, override val id: Id) : Term()

    @Serializable
    data class ByteArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class IntArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class LongArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class ListOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class CompoundOf(val elements: KList<Pair<Name, Term>>, override val id: Id) : Term()

    @Serializable
    data class BoxOf(val content: Term, val tag: Term, override val id: Id) : Term()

    @Serializable
    data class RefOf(val element: Term, override val id: Id) : Term()

    @Serializable
    data class Refl(override val id: Id) : Term()

    @Serializable
    data class FunOf(val parameters: KList<Name>, val body: Term, override val id: Id) : Term()

    @Serializable
    data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class CodeOf(val element: Term, override val id: Id) : Term()

    @Serializable
    data class Splice(val element: Term, override val id: Id) : Term()

    @Serializable
    data class Or(val variants: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class And(val variants: KList<Term>, override val id: Id) : Term()

    @Serializable
    data class Unit(override val id: Id) : Term()

    @Serializable
    data class Bool(override val id: Id) : Term()

    @Serializable
    data class Byte(override val id: Id) : Term()

    @Serializable
    data class Short(override val id: Id) : Term()

    @Serializable
    data class Int(override val id: Id) : Term()

    @Serializable
    data class Long(override val id: Id) : Term()

    @Serializable
    data class Float(override val id: Id) : Term()

    @Serializable
    data class Double(override val id: Id) : Term()

    @Serializable
    data class String(override val id: Id) : Term()

    @Serializable
    data class ByteArray(override val id: Id) : Term()

    @Serializable
    data class IntArray(override val id: Id) : Term()

    @Serializable
    data class LongArray(override val id: Id) : Term()

    @Serializable
    data class List(val element: Term, val size: Term, override val id: Id) : Term()

    @Serializable
    data class Compound(val elements: KList<Entry>, override val id: Id) : Term()

    @Serializable
    data class Box(val content: Term, override val id: Id) : Term()

    @Serializable
    data class Ref(val element: Term, override val id: Id) : Term()

    @Serializable
    data class Eq(val left: Term, val right: Term, override val id: Id) : Term()

    @Serializable
    data class Fun(val parameters: KList<Parameter>, val resultant: Term, val effects: KList<Effect>, override val id: Id) : Term()

    @Serializable
    data class Code(val element: Term, override val id: Id) : Term()

    @Serializable
    data class Type(override val id: Id) : Term()
}

@Serializable
sealed class Pattern {
    abstract val id: Id

    @Serializable
    data class Var(val name: KString, override val id: Id) : Pattern()

    @Serializable
    data class UnitOf(override val id: Id) : Pattern()

    @Serializable
    data class BoolOf(val value: KBoolean, override val id: Id) : Pattern()

    @Serializable
    data class ByteOf(val value: KByte, override val id: Id) : Pattern()

    @Serializable
    data class ShortOf(val value: KShort, override val id: Id) : Pattern()

    @Serializable
    data class IntOf(val value: KInt, override val id: Id) : Pattern()

    @Serializable
    data class LongOf(val value: KLong, override val id: Id) : Pattern()

    @Serializable
    data class FloatOf(val value: KFloat, override val id: Id) : Pattern()

    @Serializable
    data class DoubleOf(val value: KDouble, override val id: Id) : Pattern()

    @Serializable
    data class StringOf(val value: KString, override val id: Id) : Pattern()

    @Serializable
    data class ByteArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()

    @Serializable
    data class IntArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()

    @Serializable
    data class LongArrayOf(val elements: KList<Pattern>, override val id: Id) : Pattern()

    @Serializable
    data class ListOf(val elements: KList<Pattern>, override val id: Id) : Pattern()

    @Serializable
    data class CompoundOf(val elements: KList<Pair<Name, Pattern>>, override val id: Id) : Pattern()

    @Serializable
    data class BoxOf(val content: Pattern, val tag: Pattern, override val id: Id) : Pattern()

    @Serializable
    data class RefOf(val element: Pattern, override val id: Id) : Pattern()

    @Serializable
    data class Refl(override val id: Id) : Pattern()

    @Serializable
    data class Unit(override val id: Id) : Pattern()

    @Serializable
    data class Bool(override val id: Id) : Pattern()

    @Serializable
    data class Byte(override val id: Id) : Pattern()

    @Serializable
    data class Short(override val id: Id) : Pattern()

    @Serializable
    data class Int(override val id: Id) : Pattern()

    @Serializable
    data class Long(override val id: Id) : Pattern()

    @Serializable
    data class Float(override val id: Id) : Pattern()

    @Serializable
    data class Double(override val id: Id) : Pattern()

    @Serializable
    data class String(override val id: Id) : Pattern()

    @Serializable
    data class ByteArray(override val id: Id) : Pattern()

    @Serializable
    data class IntArray(override val id: Id) : Pattern()

    @Serializable
    data class LongArray(override val id: Id) : Pattern()
}

@Serializable
sealed class Effect {
    @Serializable
    data class Name(val name: KString) : Effect()
}
