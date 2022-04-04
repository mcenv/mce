package mce.pass.builtin

import mce.pass.Normalizer
import mce.pass.backend.pack.Command
import mce.pass.builtin.src.identity
import mce.pass.frontend.elab.VTerm
import mce.pass.builtin.src.byte_array.size as byte_array_size
import mce.pass.builtin.src.int.add as int_add
import mce.pass.builtin.src.int.div as int_div
import mce.pass.builtin.src.int.eq as int_eq
import mce.pass.builtin.src.int.mod as int_mod
import mce.pass.builtin.src.int.mul as int_mul
import mce.pass.builtin.src.int.ne as int_ne
import mce.pass.builtin.src.int.sub as int_sub
import mce.pass.builtin.src.int_array.size as int_array_size
import mce.pass.builtin.src.list.size as list_size
import mce.pass.builtin.src.long_array.size as long_array_size

val builtins: Map<String, BuiltinDef> = listOf(
    identity,
    int_eq,
    int_ne,
    int_add,
    int_sub,
    int_mul,
    int_div,
    int_mod,
    byte_array_size,
    int_array_size,
    long_array_size,
    list_size,
).associateBy { it.name }

val commuter: Comparator<VTerm> = Comparator { term1, term2 ->
    when {
        term1 is VTerm.Var && term2 is VTerm.Var -> term1.level.compareTo(term2.level)
        term1 is VTerm.Var -> 1
        term2 is VTerm.Var -> -1
        else -> 0
    }
}

abstract class BuiltinDef(val name: String) {
    abstract fun Normalizer.arguments(): List<Lazy<VTerm>>

    abstract fun Normalizer.evalOrNull(): VTerm?

    fun Normalizer.eval(): VTerm = evalOrNull() ?: VTerm.Def(name, arguments())

    abstract fun pack(): List<Command>
}

abstract class BuiltinDef1(name: String) : BuiltinDef(name) {
    override fun Normalizer.arguments(): List<Lazy<VTerm>> = listOf(lazyOf(lookup(size - 1)))

    override fun Normalizer.evalOrNull(): VTerm? = eval(lookup(size - 1))

    abstract fun eval(a: VTerm): VTerm?
}

abstract class BuiltinDef2(name: String) : BuiltinDef(name) {
    override fun Normalizer.arguments(): List<Lazy<VTerm>> = listOf(lazyOf(lookup(size - 2)), lazyOf(lookup(size - 1)))

    override fun Normalizer.evalOrNull(): VTerm? = eval(lookup(size - 2), lookup(size - 1))

    abstract fun eval(a: VTerm, b: VTerm): VTerm?
}

abstract class BuiltinDef3(name: String) : BuiltinDef(name) {
    override fun Normalizer.arguments(): List<Lazy<VTerm>> = listOf(lazyOf(lookup(size - 3)), lazyOf(lookup(size - 2)), lazyOf(lookup(size - 1)))

    override fun Normalizer.evalOrNull(): VTerm? = eval(lookup(size - 3), lookup(size - 2), lookup(size - 1))

    abstract fun eval(a: VTerm, b: VTerm, c: VTerm): VTerm?
}
