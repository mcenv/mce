package mce.ast.pack

import mce.minecraft.ResourceLocation
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

sealed class ResourceType(val directory: KString, val extension: KString) {
    object Recipes : ResourceType("recipes", "json")
    data class Tags(val path: KString) : ResourceType("tags/$path", "json")
    object Predicates : ResourceType("predicates", "json")
    object LootTables : ResourceType("loot_tables", "json")
    object ItemModifiers : ResourceType("item_modifiers", "json")
    object Advancements : ResourceType("advancements", "json")
    object Functions : ResourceType("functions", "mcfunction")
}

data class Function(
    val commands: KList<Command>,
)

sealed class Command {
    data class Raw(val body: KString) : Command()
    data class Execute(val execute: mce.ast.pack.Execute) : Command()
    data class CheckScore(val success: Boolean, val target: ScoreHolder, val targetObjective: Objective, val source: SourceComparator) : Command()
    data class CheckMatchingData(val success: Boolean, val source: ResourceLocation, val path: NbtPath) : Command()
    data class GetData(val target: ResourceLocation, val path: NbtPath? = null) : Command()
    data class GetNumeric(val target: ResourceLocation, val path: NbtPath, val scale: KDouble) : Command()
    data class RemoveData(val target: ResourceLocation, val path: NbtPath) : Command()
    data class InsertAtIndex(val target: ResourceLocation, val path: NbtPath, val index: KInt, val source: SourceProvider) : Command()
    data class Prepend(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
    data class Append(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
    data class SetData(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
    data class MergeData(val target: ResourceLocation, val path: NbtPath, val source: SourceProvider) : Command()
    data class RunFunction(val name: ResourceLocation) : Command()
    data class SetScore(val targets: ScoreHolder, val objective: Objective, val score: KInt) : Command()
    data class GetScore(val target: ScoreHolder, val objective: Objective) : Command()
    data class AddScore(val targets: ScoreHolder, val objective: Objective, val score: KInt) : Command()
    data class RemoveScore(val targets: ScoreHolder, val objective: Objective, val score: KInt) : Command()
    data class ResetScores(val targets: ScoreHolder) : Command()
    data class ResetScore(val targets: ScoreHolder, val objective: Objective) : Command()
    data class PerformOperation(val targets: ScoreHolder, val targetObjective: Objective, val operation: Operation, val source: ScoreHolder, val sourceObjective: Objective) : Command()
}

sealed class Execute {
    data class Run(val command: Command) : Execute()
    data class CheckScore(val success: Boolean, val target: ScoreHolder, val targetObjective: Objective, val source: SourceComparator, val execute: Execute) : Execute()
    data class CheckMatchingData(val success: Boolean, val source: ResourceLocation, val path: NbtPath, val execute: Execute) : Execute()
    data class StoreValue(val consumer: Consumer, val targets: ScoreHolder, val objective: Objective, val execute: Execute) : Execute()
    data class StoreData(val consumer: Consumer, val target: ResourceLocation, val path: NbtPath, val type: StoreType, val scale: KDouble, val execute: Execute) : Execute()
}

enum class StoreType {
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
}

sealed class SourceProvider {
    data class Value(val value: Nbt) : SourceProvider()
    data class From(val source: ResourceLocation, val path: NbtPath? = null) : SourceProvider()
}

data class NbtPath(val nodes: KList<NbtNode> = emptyList())

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
    data class ByteArray(val elements: KList<KByte>) : Nbt()
    data class String(val data: KString) : Nbt()
    data class List(val elements: KList<Nbt>) : Nbt()
    data class Compound(val elements: Map<KString, Nbt>) : Nbt()
    data class IntArray(val elements: KList<KInt>) : Nbt()
    data class LongArray(val elements: KList<KLong>) : Nbt()
}

enum class NbtType {
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BYTE_ARRAY,
    STRING,
    LIST,
    COMPOUND,
    INT_ARRAY,
    LONG_ARRAY,
}

@JvmInline
value class Objective(val name: KString)

@JvmInline
value class ScoreHolder(val name: KString)

enum class Operation {
    ASSIGN,
    PLUS_ASSIGN,
    MINUS_ASSIGN,
    TIMES_ASSIGN,
    DIV_ASSIGN,
    MOD_ASSIGN,
    MIN_ASSIGN,
    MAX_ASSIGN,
    SWAP,
}

sealed class SourceComparator {
    data class EqScore(val source: ScoreHolder, val sourceObjective: Objective) : SourceComparator()
    data class LtScore(val source: ScoreHolder, val sourceObjective: Objective) : SourceComparator()
    data class LeScore(val source: ScoreHolder, val sourceObjective: Objective) : SourceComparator()
    data class GtScore(val source: ScoreHolder, val sourceObjective: Objective) : SourceComparator()
    data class GeScore(val source: ScoreHolder, val sourceObjective: Objective) : SourceComparator()
    data class EqConst(val value: KInt) : SourceComparator()
    data class LeConst(val value: KInt) : SourceComparator()
    data class GeConst(val value: KInt) : SourceComparator()
}

enum class Consumer {
    RESULT,
    SUCCESS,
}
