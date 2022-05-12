package mce.ast.defun

import mce.ast.Modifier
import mce.ast.Name
import mce.ast.core.VTerm
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

    data class Def(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val params: KList<Param>,
        val body: Term,
    ) : Item()

    data class Mod(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Module,
    ) : Item()

    data class Test(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Term,
    ) : Item()

    data class Advancement(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Term,
    ) : Item()
}

data class Param(val termRelevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val typeRelevant: KBoolean, val type: VTerm)

sealed class Module {
    data class Var(val name: KString) : Module()
    data class Str(val items: KList<Item>) : Module()
    data class Sig(val types: KList<Signature>) : Module()
    object Type : Module()
}

sealed class Signature {
    data class Def(val name: KString, val params: KList<Param>, val resultant: Term) : Signature()
    data class Mod(val name: KString, val type: Module) : Signature()
    data class Advancement(val name: KString) : Signature()
    data class Test(val name: KString) : Signature()
}

sealed class Term {
    abstract val type: VTerm

    data class Builtin(override val type: VTerm) : Term()
    data class Command(val body: Term, override val type: VTerm) : Term()
    data class Block(val elements: KList<Term>, override val type: VTerm) : Term()
    data class Var(val name: KString, val level: KInt, override val type: VTerm) : Term()
    data class Def(val name: KString, val arguments: KList<Term>, override val type: VTerm) : Term()
    data class Let(val name: KString, val init: Term, override val type: VTerm) : Term()
    data class Match(val scrutinee: Term, val clauses: KList<Pair<Pat, Term>>, override val type: VTerm) : Term()
    data class UnitOf(override val type: VTerm) : Term()
    data class BoolOf(val value: KBoolean, override val type: VTerm) : Term()
    data class ByteOf(val value: KByte, override val type: VTerm) : Term()
    data class ShortOf(val value: KShort, override val type: VTerm) : Term()
    data class IntOf(val value: KInt, override val type: VTerm) : Term()
    data class LongOf(val value: KLong, override val type: VTerm) : Term()
    data class FloatOf(val value: KFloat, override val type: VTerm) : Term()
    data class DoubleOf(val value: KDouble, override val type: VTerm) : Term()
    data class StringOf(val value: KString, override val type: VTerm) : Term()
    data class ByteArrayOf(val elements: KList<Term>, override val type: VTerm) : Term()
    data class IntArrayOf(val elements: KList<Term>, override val type: VTerm) : Term()
    data class LongArrayOf(val elements: KList<Term>, override val type: VTerm) : Term()
    data class ListOf(val elements: KList<Term>, override val type: VTerm) : Term()
    data class CompoundOf(val elements: KList<Entry>, override val type: VTerm) : Term() {
        data class Entry(val name: Name, val element: Term)
    }

    data class TupleOf(val elements: KList<Term>, override val type: VTerm) : Term()
    data class RefOf(val element: Term, override val type: VTerm) : Term()
    data class Refl(override val type: VTerm) : Term()
    data class FunOf(val tag: KInt, override val type: VTerm) : Term()
    data class Apply(val function: Term, val arguments: KList<Term>, override val type: VTerm) : Term()
    data class Or(val variants: KList<Term>, override val type: VTerm) : Term()
    data class And(val variants: KList<Term>, override val type: VTerm) : Term()
    data class Unit(override val type: VTerm) : Term()
    data class Bool(override val type: VTerm) : Term()
    data class Byte(override val type: VTerm) : Term()
    data class Short(override val type: VTerm) : Term()
    data class Int(override val type: VTerm) : Term()
    data class Long(override val type: VTerm) : Term()
    data class Float(override val type: VTerm) : Term()
    data class Double(override val type: VTerm) : Term()
    data class String(override val type: VTerm) : Term()
    data class ByteArray(override val type: VTerm) : Term()
    data class IntArray(override val type: VTerm) : Term()
    data class LongArray(override val type: VTerm) : Term()
    data class List(val element: Term, val size: Term, override val type: VTerm) : Term()
    data class Compound(val elements: KList<Entry>, override val type: VTerm) : Term() {
        data class Entry(val relevant: KBoolean, val name: Name, val type: Term)
    }

    data class Tuple(val elements: KList<Entry>, override val type: VTerm) : Term() {
        data class Entry(val relevant: KBoolean, val type: Term)
    }

    data class Ref(val element: Term, override val type: VTerm) : Term()
    data class Eq(val left: Term, val right: Term, override val type: VTerm) : Term()
    data class Fun(val params: KList<Param>, val resultant: Term, val effs: KSet<Eff>, override val type: VTerm) : Term()
    data class Type(override val type: VTerm) : Term()
}

sealed class Pat {
    abstract val type: VTerm

    data class Var(val name: KString, override val type: VTerm) : Pat()
    data class UnitOf(override val type: VTerm) : Pat()
    data class BoolOf(val value: KBoolean, override val type: VTerm) : Pat()
    data class ByteOf(val value: KByte, override val type: VTerm) : Pat()
    data class ShortOf(val value: KShort, override val type: VTerm) : Pat()
    data class IntOf(val value: KInt, override val type: VTerm) : Pat()
    data class LongOf(val value: KLong, override val type: VTerm) : Pat()
    data class FloatOf(val value: KFloat, override val type: VTerm) : Pat()
    data class DoubleOf(val value: KDouble, override val type: VTerm) : Pat()
    data class StringOf(val value: KString, override val type: VTerm) : Pat()
    data class ByteArrayOf(val elements: KList<Pat>, override val type: VTerm) : Pat()
    data class IntArrayOf(val elements: KList<Pat>, override val type: VTerm) : Pat()
    data class LongArrayOf(val elements: KList<Pat>, override val type: VTerm) : Pat()
    data class ListOf(val elements: KList<Pat>, override val type: VTerm) : Pat()
    data class CompoundOf(val elements: KList<Pair<Name, Pat>>, override val type: VTerm) : Pat()
    data class TupleOf(val elements: KList<Pat>, override val type: VTerm) : Pat()
    data class RefOf(val element: Pat, override val type: VTerm) : Pat()
    data class Refl(override val type: VTerm) : Pat()
}

sealed class Eff {
    data class Name(val name: KString) : Eff()
}
