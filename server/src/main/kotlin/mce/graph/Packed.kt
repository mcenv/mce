package mce.graph

import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

typealias NbtPath = KList<Packed.NbtNode>

object Packed {
    data class Datapack(
        val functions: Map<KString, Function>
    )

    data class Function(
        val name: KString,
        val commands: KList<Command>
    )

    sealed class Command {
        data class StoreData(val target: ResourceLocation, val path: NbtPath, val type: StoreType, val scale: KDouble, val command: Command) : Command()
        data class GetData(val target: ResourceLocation, val path: NbtPath? = null) : Command()
        data class GetNumeric(val target: ResourceLocation, val path: NbtPath, val scale: KDouble) : Command()
        data class RemoveData(val target: ResourceLocation, val path: NbtPath) : Command()
        data class InsertAtIndex(val target: ResourceLocation, val path: NbtPath, val index: KInt, val source: SourceProvider) : Command()
        data class SetData(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
        data class MergeData(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
        data class RunFunction(val name: ResourceLocation) : Command()
    }

    enum class StoreType {
        INT,
        FLOAT,
        SHORT,
        LONG,
        DOUBLE,
        BYTE
    }

    sealed class SourceProvider {
        data class Value(val value: Nbt) : SourceProvider()
        data class From(val source: ResourceLocation, val path: NbtPath? = null) : SourceProvider()
    }

    sealed class NbtNode {
        data class MatchRootObject(val pattern: Nbt.Compound) : NbtNode()
        data class MatchElement(val pattern: Nbt.Compound) : NbtNode()
        object AllElements : NbtNode()
        data class IndexedElement(val index: KInt) : NbtNode()
        data class MatchObject(val name: KString, val pattern: Nbt.Compound) : NbtNode()
        data class CompoundChild(val name: KString) : NbtNode()
    }

    sealed class Nbt {
        data class Byte(val data: KByte) : Nbt()
        data class Short(val data: KShort) : Nbt()
        data class Int(val data: KInt) : Nbt()
        data class Long(val data: KLong) : Nbt()
        data class Float(val data: KFloat) : Nbt()
        data class Double(val data: KDouble) : Nbt()
        data class ByteArray(val data: KList<KByte>) : Nbt()
        data class String(val data: KString) : Nbt()
        data class List(val elements: KList<Nbt>) : Nbt()
        data class Compound(val elements: Map<String, Nbt>) : Nbt()
        data class IntArray(val data: KList<KInt>) : Nbt()
        data class LongArray(val data: KList<KLong>) : Nbt()
    }

    data class ResourceLocation(
        val namespace: KString,
        val path: KString
    )
}
