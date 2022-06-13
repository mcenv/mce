package mce.ast.core

import mce.Id
import mce.ast.Modifier
import mce.ast.Name
import mce.util.*
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
        val resultant: Term,
        val effs: KSet<Eff>,
        val body: Term,
        override val id: Id,
    ) : Item()

    data class Mod(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val type: VModule,
        val body: Module,
        override val id: Id,
    ) : Item()

    data class Test(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Term,
        override val id: Id,
    ) : Item()

    data class Advancement(
        override val modifiers: KSet<Modifier>,
        override val name: KString,
        val body: Term,
        override val id: Id,
    ) : Item()

    data class Pack(
        val body: Term,
        override val id: Id,
    ) : Item() {
        override val modifiers: KSet<Modifier> = emptySet()
        override val name: KString = "pack"
    }
}

data class Param(val termRelevant: KBoolean, val name: KString, val lower: Term?, val upper: Term?, val typeRelevant: KBoolean, val type: Term, val id: Id)

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

    data class Def(val name: KString, val params: KList<Param>, val resultant: Term, override val id: Id?) : Signature()
    data class Mod(val name: KString, val type: Module, override val id: Id?) : Signature()
    data class Test(val name: KString, override val id: Id?) : Signature()
    data class Advancement(val name: KString, override val id: Id?) : Signature()
    data class Pack(override val id: Id?) : Signature()
}

/**
 * A semantic signature.
 */
sealed class VSignature {
    abstract val id: Id?

    data class Def(val name: KString, val params: KList<Param>, val resultant: Term, override val id: Id?) : VSignature()
    data class Mod(val name: KString, val type: VModule, override val id: Id?) : VSignature()
    data class Test(val name: KString, override val id: Id?) : VSignature()
    data class Advancement(val name: KString, override val id: Id?) : VSignature()
    data class Pack(override val id: Id?) : VSignature()
}

/**
 * A syntactic term.
 */
sealed class Term {
    abstract val id: Id?

    data class Builtin(override val id: Id?) : Term()
    data class Hole(override val id: Id?) : Term()
    data class Meta(val index: KInt, override val id: Id?) : Term()
    data class Command(val body: Term, override val id: Id?) : Term()
    data class Block(val elements: KList<Term>, override val id: Id?) : Term()
    data class Var(val name: KString, val level: KInt, override val id: Id?) : Term()
    data class Def(val name: KString, val arguments: KList<Term>, override val id: Id?) : Term()
    data class Let(val name: KString, val init: Term, override val id: Id?) : Term()
    data class Match(val scrutinee: Term, val clauses: KList<Pair<Pat, Term>>, override val id: Id?) : Term()
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
    data class CompoundOf(val elements: KList<Entry>, override val id: Id?) : Term() {
        data class Entry(val name: Name, val element: Term)
    }

    data class TupleOf(val elements: KList<Term>, override val id: Id?) : Term()
    data class RefOf(val element: Term, override val id: Id?) : Term()
    data class Refl(override val id: Id?) : Term()
    data class FunOf(val params: KList<Name>, val body: Term, override val id: Id?) : Term()
    data class Apply(val function: Term, val arguments: KList<Term>, override val id: Id?) : Term()
    data class CodeOf(val element: Term, override val id: Id?) : Term()
    data class Splice(val element: Term, override val id: Id?) : Term()
    data class Singleton(val element: Term, override val id: Id?) : Term()
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
    data class Compound(val elements: KList<Entry>, override val id: Id?) : Term() {
        data class Entry(val relevant: KBoolean, val name: Name, val type: Term, val id: Id?)
    }

    data class Tuple(val elements: KList<Entry>, override val id: Id?) : Term() {
        data class Entry(val relevant: KBoolean, val name: Name, val type: Term, val id: Id?)
    }

    data class Ref(val element: Term, override val id: Id?) : Term()
    data class Eq(val left: Term, val right: Term, override val id: Id?) : Term()
    data class Fun(val params: KList<Param>, val resultant: Term, val effs: KSet<Eff>, override val id: Id?) : Term()
    data class Code(val element: Term, override val id: Id?) : Term()
    data class Type(override val id: Id?) : Term()
}

/**
 * A semantic term.
 */
sealed class VTerm {
    abstract val id: Id?
    abstract val hash: Lazy<KInt>

    data class Hole(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_3)
    }

    data class Meta(val index: KInt, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_4 xor index.hashCode() }
    }

    data class Command(val body: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_5 xor !body.value.hash }
    }

    data class Var(val name: KString, val level: KInt, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_6 xor name.hashCode() xor level.hashCode() }
    }

    data class Def(val name: KString, val arguments: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_7 xor name.hashCode() xor arguments.fold(0) { acc, argument -> acc xor !argument.value.hash } }
    }

    data class Match(val scrutinee: VTerm, val clauses: KList<Pair<Pat, Lazy<VTerm>>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(0) // TODO
    }

    data class UnitOf(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_9)
    }

    data class BoolOf(val value: KBoolean, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_10 xor value.hashCode() }
    }

    data class ByteOf(val value: KByte, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_11 xor value.hashCode() }
    }

    data class ShortOf(val value: KShort, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_12 xor value.hashCode() }
    }

    data class IntOf(val value: KInt, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_13 xor value.hashCode() }
    }

    data class LongOf(val value: KLong, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_14 xor value.hashCode() }
    }

    data class FloatOf(val value: KFloat, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_15 xor value.hashCode() }
    }

    data class DoubleOf(val value: KDouble, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_16 xor value.hashCode() }
    }

    data class StringOf(val value: KString, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_17 xor value.hashCode() }
    }

    data class ByteArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_18 xor elements.fold(0) { acc, element -> acc xor !element.value.hash } }
    }

    data class IntArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_19 xor elements.fold(0) { acc, element -> acc xor !element.value.hash } }
    }

    data class LongArrayOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_20 xor elements.fold(0) { acc, element -> acc xor !element.value.hash } }
    }

    data class ListOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_21 xor elements.fold(0) { acc, element -> acc xor !element.value.hash } }
    }

    data class CompoundOf(val elements: LinkedHashMap<KString, Entry>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_22 xor elements.values.fold(0) { acc, (name, element) -> acc xor name.hashCode() xor !element.value.hash } }

        data class Entry(val name: Name, val element: Lazy<VTerm>)
    }

    data class TupleOf(val elements: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_23 xor elements.fold(0) { acc, element -> acc xor !element.value.hash } }
    }

    data class RefOf(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_24 xor !element.value.hash }
    }

    data class Refl(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_25)
    }

    data class FunOf(val params: KList<Name>, val body: Term, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_26 xor params.fold(0) { acc, param -> acc xor param.hashCode() } /* xor !body.hash */ }
    }

    data class Apply(val function: VTerm, val arguments: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_27 xor !function.hash xor arguments.fold(0) { acc, argument -> acc xor !argument.value.hash } }
    }

    data class CodeOf(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_28 xor !element.value.hash }
    }

    data class Splice(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_29 xor !element.value.hash }
    }

    data class Singleton(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_30 xor !element.value.hash }
    }

    data class Or(val variants: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy {
            PRIME_31 xor variants.fold(0) { acc, variant -> acc xor !variant.value.hash }
        }
    }

    data class And(val variants: KList<Lazy<VTerm>>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy {
            PRIME_32 xor variants.fold(0) { acc, variant -> acc xor !variant.value.hash }
        }
    }

    data class Unit(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_33)
    }

    data class Bool(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_34)
    }

    data class Byte(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_35)
    }

    data class Short(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_36)
    }

    data class Int(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_37)
    }

    data class Long(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_38)
    }

    data class Float(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_39)
    }

    data class Double(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_40)
    }

    data class String(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_41)
    }

    data class ByteArray(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_42)
    }

    data class IntArray(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_43)
    }

    data class LongArray(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_44)
    }

    data class List(val element: Lazy<VTerm>, val size: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_45 xor !element.value.hash xor !size.value.hash }
    }

    data class Compound(val elements: LinkedHashMap<KString, Entry>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(0) // TODO

        data class Entry(val relevant: KBoolean, val name: Name, val type: Lazy<VTerm>, val id: Id?)
    }

    data class Tuple(val elements: KList<Term.Tuple.Entry>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(0) // TODO
    }

    data class Ref(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_48 xor !element.value.hash }
    }

    data class Eq(val left: Lazy<VTerm>, val right: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_49 xor !left.value.hash xor !right.value.hash }
    }

    data class Fun(val params: KList<Param>, val resultant: Term, val effs: KSet<Eff>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_50 xor params.fold(0) { acc, param -> acc xor param.hashCode() } /* TODO: xor resultant.hash() xor effs.hash() */ }
    }

    data class Code(val element: Lazy<VTerm>, override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazy { PRIME_51 xor !element.value.hash }
    }

    data class Type(override val id: Id? = null) : VTerm() {
        override val hash: Lazy<KInt> = lazyOf(PRIME_52)
    }
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
    data class CompoundOf(val elements: KList<Pair<Name, Pat>>, override val id: Id) : Pat()
    data class TupleOf(val elements: KList<Pat>, override val id: Id) : Pat()
    data class RefOf(val element: Pat, override val id: Id) : Pat()
    data class Refl(override val id: Id) : Pat()
}

sealed class Eff {
    data class Name(val name: KString) : Eff()
}
