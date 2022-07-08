package mce.pass.builtin

import mce.ast.core.VTerm
import mce.ast.pack.Command
import mce.pass.Normalizer
import mce.pass.builtin.src.*
import mce.pass.builtin.src.byte_array.size as byte_array_size
import mce.pass.builtin.src.int.to_string as int_to_string
import mce.pass.builtin.src.int_array.size as int_array_size
import mce.pass.builtin.src.list.size as list_size
import mce.pass.builtin.src.long_array.size as long_array_size
import mce.pass.builtin.src.string.`+` as string_concat

val builtins: Map<String, BuiltinDef> = listOf(
    identity,
    `+`,
    `-`,
    `×`,
    `÷`,
    `%`,
    `≡`,
    `≢`,
    int_to_string,
    string_concat,
    byte_array_size,
    int_array_size,
    long_array_size,
    list_size,
).associateBy { it.name }

val commuter: Comparator<VTerm> = Comparator { term1, term2 -> term1.hash.value compareTo term2.hash.value }

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
