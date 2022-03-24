package mce.ast.defun

import mce.ast.Id
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
import kotlin.collections.Set as KSet

sealed class Item {
    abstract val modifiers: KSet<Modifier>
    abstract val name: KString
    abstract val id: Id

    data class Def(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val params: KList<Param>,
        val body: Term,
        override val id: Id,
    ) : Item()

    data class Mod(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Module,
        override val id: Id,
    ) : Item()

    data class Test(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Term,
        override val id: Id,
    ) : Item()
}

enum class Modifier {
    BUILTIN,
}

data class Param(val termRelevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val typeRelevant: KBoolean, val type: Term, val id: Id)

data class Entry(val relevant: KBoolean, val type: Term, val id: Id)

sealed class Module {
    abstract val id: Id?

    data class Var(val name: KString, override val id: Id?) : Module()
    data class Str(val items: KList<Item>, override val id: Id?) : Module()
    data class Sig(val types: KList<Signature>, override val id: Id?) : Module()
    data class Type(override val id: Id?) : Module()
}

sealed class Signature {
    abstract val id: Id

    data class Def(val name: KString, val params: KList<Param>, val resultant: Term, override val id: Id) : Signature()
    data class Mod(val name: KString, val type: Module, override val id: Id) : Signature()
    data class Test(val name: KString, override val id: Id) : Signature()
}

sealed class Term {
    abstract val id: Id

    data class Var(val name: KString, val level: KInt, override val id: Id) : Term()
    data class Def(val name: KString, val arguments: KList<Term>, override val id: Id) : Term()
    data class Let(val name: KString, val init: Term, val body: Term, override val id: Id) : Term()
    data class Match(val scrutinee: Term, val clauses: KList<Pair<Pat, Term>>, override val id: Id) : Term()
    data class UnitOf(override val id: Id) : Term()
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
    data class CompoundOf(val elements: LinkedHashMap<Name, Term>, override val id: Id) : Term()
    data class BoxOf(val content: Term, val tag: Term, override val id: Id) : Term()
    data class RefOf(val element: Term, override val id: Id) : Term()
    data class Refl(override val id: Id) : Term()
    data class FunOf(val tag: KInt, override val id: Id) : Term()
    data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id) : Term()
    data class Or(val variants: KList<Term>, override val id: Id) : Term()
    data class And(val variants: KList<Term>, override val id: Id) : Term()
    data class Unit(override val id: Id) : Term()
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
    data class List(val element: Term, val size: Term, override val id: Id) : Term()
    data class Compound(val elements: LinkedHashMap<Name, Entry>, override val id: Id) : Term()
    data class Box(val content: Term, override val id: Id) : Term()
    data class Ref(val element: Term, override val id: Id) : Term()
    data class Eq(val left: Term, val right: Term, override val id: Id) : Term()
    data class Fun(val params: KList<Param>, val resultant: Term, val effs: KSet<Eff>, override val id: Id) : Term()
    data class Type(override val id: Id) : Term()
}

sealed class Pat {
    abstract val id: Id

    data class Var(val name: KString, override val id: Id) : Pat()
    data class UnitOf(override val id: Id) : Pat()
    data class BoolOf(val value: KBoolean, override val id: Id) : Pat()
    data class ByteOf(val value: KByte, override val id: Id) : Pat()
    data class ShortOf(val value: KShort, override val id: Id) : Pat()
    data class IntOf(val value: KInt, override val id: Id) : Pat()
    data class LongOf(val value: KLong, override val id: Id) : Pat()
    data class FloatOf(val value: KFloat, override val id: Id) : Pat()
    data class DoubleOf(val value: KDouble, override val id: Id) : Pat()
    data class StringOf(val value: KString, override val id: Id) : Pat()
    data class ByteArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()
    data class IntArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()
    data class LongArrayOf(val elements: KList<Pat>, override val id: Id) : Pat()
    data class ListOf(val elements: KList<Pat>, override val id: Id) : Pat()
    data class CompoundOf(val elements: LinkedHashMap<Name, Pat>, override val id: Id) : Pat()
    data class BoxOf(val content: Pat, val tag: Pat, override val id: Id) : Pat()
    data class RefOf(val element: Pat, override val id: Id) : Pat()
    data class Refl(override val id: Id) : Pat()
    data class Unit(override val id: Id) : Pat()
    data class Bool(override val id: Id) : Pat()
    data class Byte(override val id: Id) : Pat()
    data class Short(override val id: Id) : Pat()
    data class Int(override val id: Id) : Pat()
    data class Long(override val id: Id) : Pat()
    data class Float(override val id: Id) : Pat()
    data class Double(override val id: Id) : Pat()
    data class String(override val id: Id) : Pat()
    data class ByteArray(override val id: Id) : Pat()
    data class IntArray(override val id: Id) : Pat()
    data class LongArray(override val id: Id) : Pat()
}

sealed class Eff {
    data class Name(val name: KString) : Eff()
}
