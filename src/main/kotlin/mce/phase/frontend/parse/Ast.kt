@file:UseSerializers(IdSerializer::class)

package mce.phase.frontend.parse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import mce.phase.Id
import mce.phase.IdSerializer
import mce.phase.Name
import kotlin.Boolean as KBoolean
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

// Weird serial names are a temporary workaround for the kotlinx.serialization implementation where sealed subclasses can only be found by string.

@Serializable
sealed class Item {
    abstract val imports: KList<KString>
    abstract val exports: KList<KString>
    abstract val modifiers: KList<Modifier>
    abstract val name: KString
    abstract val id: Id

    @Serializable
    @SerialName("0")
    data class Def(
        override val imports: KList<KString>,
        override val exports: KList<KString>,
        override val modifiers: KList<Modifier>,
        override val name: KString,
        val params: KList<Param>,
        val resultant: Term,
        val effs: KList<Eff>,
        val body: Term,
        override val id: Id,
    ) : Item()

    @Serializable
    @SerialName("1")
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
    @SerialName("2")
    data class Test(
        override val imports: KList<KString>,
        override val exports: KList<KString>,
        override val modifiers: KList<Modifier>,
        override val name: KString,
        val body: Term,
        override val id: Id,
    ) : Item()
}

enum class Modifier {
    BUILTIN,
    META,
}

@Serializable
data class Param(val termRelevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val typeRelevant: KBoolean, val type: Term, val id: Id)

@Serializable
data class Entry(val relevant: KBoolean, val name: Name, val type: Term, val id: Id)

@Serializable
sealed class Module {
    abstract val id: Id

    @Serializable
    @SerialName("0")
    data class Var(val name: KString, override val id: Id) : Module()

    @Serializable
    @SerialName("1")
    data class Str(val items: KList<Item>, override val id: Id) : Module()

    @Serializable
    @SerialName("2")
    data class Sig(val signatures: KList<Signature>, override val id: Id) : Module()

    @Serializable
    @SerialName("3")
    data class Type(override val id: Id) : Module()
}

@Serializable
sealed class Signature {
    abstract val id: Id

    @Serializable
    @SerialName("0")
    data class Def(val name: KString, val params: KList<Param>, val resultant: Term, override val id: Id) : Signature()

    @Serializable
    @SerialName("1")
    data class Mod(val name: KString, val type: Module, override val id: Id) : Signature()

    @Serializable
    @SerialName("2")
    data class Test(val name: KString, override val id: Id) : Signature()
}

@Serializable
sealed class Term {
    abstract val id: Id

    @Serializable
    @SerialName("0")
    data class Hole(override val id: Id) : Term()

    @Serializable
    @SerialName("1")
    data class Meta(override val id: Id) : Term()

    @Serializable
    @SerialName("2")
    data class Anno(val element: Term, val type: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("3")
    data class Var(val name: KString, override val id: Id) : Term()

    @Serializable
    @SerialName("4")
    data class Def(val name: KString, val arguments: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("5")
    data class Let(val name: KString, val init: Term, val body: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("6")
    data class Match(val scrutinee: Term, val clauses: KList<Pair<Pat, Term>>, override val id: Id) : Term()

    @Serializable
    @SerialName("7")
    data class UnitOf(override val id: Id) : Term()

    @Serializable
    @SerialName("8")
    data class BoolOf(val value: KBoolean, override val id: Id) : Term()

    @Serializable
    @SerialName("9")
    data class ByteOf(val value: KByte, override val id: Id) : Term()

    @Serializable
    @SerialName("10")
    data class ShortOf(val value: KShort, override val id: Id) : Term()

    @Serializable
    @SerialName("11")
    data class IntOf(val value: KInt, override val id: Id) : Term()

    @Serializable
    @SerialName("12")
    data class LongOf(val value: KLong, override val id: Id) : Term()

    @Serializable
    @SerialName("13")
    data class FloatOf(val value: KFloat, override val id: Id) : Term()

    @Serializable
    @SerialName("14")
    data class DoubleOf(val value: KDouble, override val id: Id) : Term()

    @Serializable
    @SerialName("15")
    data class StringOf(val value: KString, override val id: Id) : Term()

    @Serializable
    @SerialName("16")
    data class ByteArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("17")
    data class IntArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("18")
    data class LongArrayOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("19")
    data class ListOf(val elements: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("20")
    data class CompoundOf(val elements: KList<Pair<Name, Term>>, override val id: Id) : Term()

    @Serializable
    @SerialName("21")
    data class BoxOf(val content: Term, val tag: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("22")
    data class RefOf(val element: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("23")
    data class Refl(override val id: Id) : Term()

    @Serializable
    @SerialName("24")
    data class FunOf(val params: KList<Name>, val body: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("25")
    data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("26")
    data class CodeOf(val element: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("27")
    data class Splice(val element: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("28")
    data class Or(val variants: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("29")
    data class And(val variants: KList<Term>, override val id: Id) : Term()

    @Serializable
    @SerialName("30")
    data class Unit(override val id: Id) : Term()

    @Serializable
    @SerialName("31")
    data class Bool(override val id: Id) : Term()

    @Serializable
    @SerialName("32")
    data class Byte(override val id: Id) : Term()

    @Serializable
    @SerialName("33")
    data class Short(override val id: Id) : Term()

    @Serializable
    @SerialName("34")
    data class Int(override val id: Id) : Term()

    @Serializable
    @SerialName("35")
    data class Long(override val id: Id) : Term()

    @Serializable
    @SerialName("36")
    data class Float(override val id: Id) : Term()

    @Serializable
    @SerialName("37")
    data class Double(override val id: Id) : Term()

    @Serializable
    @SerialName("38")
    data class String(override val id: Id) : Term()

    @Serializable
    @SerialName("39")
    data class ByteArray(override val id: Id) : Term()

    @Serializable
    @SerialName("40")
    data class IntArray(override val id: Id) : Term()

    @Serializable
    @SerialName("41")
    data class LongArray(override val id: Id) : Term()

    @Serializable
    @SerialName("42")
    data class List(val element: Term, val size: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("43")
    data class Compound(val elements: KList<Entry>, override val id: Id) : Term()

    @Serializable
    @SerialName("44")
    data class Box(val content: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("45")
    data class Ref(val element: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("46")
    data class Eq(val left: Term, val right: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("47")
    data class Fun(val params: KList<Param>, val resultant: Term, val effs: KList<Eff>, override val id: Id) : Term()

    @Serializable
    @SerialName("48")
    data class Code(val element: Term, override val id: Id) : Term()

    @Serializable
    @SerialName("49")
    data class Type(override val id: Id) : Term()
}

@Serializable
sealed class Pat {
    abstract val id: Id

    @Serializable
    @SerialName("0")
    data class Var(val name: KString, override val id: Id) : Pat()

    @Serializable
    @SerialName("1")
    data class UnitOf(override val id: Id) : Pat()

    @Serializable
    @SerialName("2")
    data class BoolOf(val value: KBoolean, override val id: Id) : Pat()

    @Serializable
    @SerialName("3")
    data class ByteOf(val value: KByte, override val id: Id) : Pat()

    @Serializable
    @SerialName("4")
    data class ShortOf(val value: KShort, override val id: Id) : Pat()

    @Serializable
    @SerialName("5")
    data class IntOf(val value: KInt, override val id: Id) : Pat()

    @Serializable
    @SerialName("6")
    data class LongOf(val value: KLong, override val id: Id) : Pat()

    @Serializable
    @SerialName("7")
    data class FloatOf(val value: KFloat, override val id: Id) : Pat()

    @Serializable
    @SerialName("8")
    data class DoubleOf(val value: KDouble, override val id: Id) : Pat()

    @Serializable
    @SerialName("9")
    data class StringOf(val value: KString, override val id: Id) : Pat()

    @Serializable
    @SerialName("10")
    data class ByteArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("11")
    data class IntArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("12")
    data class LongArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("13")
    data class ListOf(val elements: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("14")
    data class CompoundOf(val elements: KList<Pair<Name, Pat>>, override val id: Id) : Pat()

    @Serializable
    @SerialName("15")
    data class BoxOf(val content: Pat, val tag: Pat, override val id: Id) : Pat()

    @Serializable
    @SerialName("16")
    data class RefOf(val element: Pat, override val id: Id) : Pat()

    @Serializable
    @SerialName("17")
    data class Refl(override val id: Id) : Pat()

    @Serializable
    @SerialName("18")
    data class Or(val variants: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("19")
    data class And(val variants: KList<Pat>, override val id: Id) : Pat()

    @Serializable
    @SerialName("20")
    data class Unit(override val id: Id) : Pat()

    @Serializable
    @SerialName("21")
    data class Bool(override val id: Id) : Pat()

    @Serializable
    @SerialName("22")
    data class Byte(override val id: Id) : Pat()

    @Serializable
    @SerialName("23")
    data class Short(override val id: Id) : Pat()

    @Serializable
    @SerialName("24")
    data class Int(override val id: Id) : Pat()

    @Serializable
    @SerialName("25")
    data class Long(override val id: Id) : Pat()

    @Serializable
    @SerialName("26")
    data class Float(override val id: Id) : Pat()

    @Serializable
    @SerialName("27")
    data class Double(override val id: Id) : Pat()

    @Serializable
    @SerialName("28")
    data class String(override val id: Id) : Pat()

    @Serializable
    @SerialName("29")
    data class ByteArray(override val id: Id) : Pat()

    @Serializable
    @SerialName("30")
    data class IntArray(override val id: Id) : Pat()

    @Serializable
    @SerialName("31")
    data class LongArray(override val id: Id) : Pat()

    // TODO: List

    // TODO: Compound

    @Serializable
    @SerialName("32")
    data class Box(val content: Pat, override val id: Id) : Pat()

    @Serializable
    @SerialName("33")
    data class Ref(val element: Pat, override val id: Id) : Pat()

    @Serializable
    @SerialName("34")
    data class Eq(val left: Pat, val right: Pat, override val id: Id) : Pat()

    // TODO: Fun?

    @Serializable
    @SerialName("35")
    data class Type(override val id: Id) : Pat()
}

@Serializable
sealed class Eff {
    @Serializable
    @SerialName("0")
    data class Name(val name: KString) : Eff()
}
