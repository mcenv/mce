package mce.ast.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.Byte as KByte
import kotlin.Double as KDouble
import kotlin.Float as KFloat
import kotlin.Int as KInt
import kotlin.Long as KLong
import kotlin.Short as KShort
import kotlin.String as KString
import kotlin.collections.List as KList

@Serializable
data class PackMetadata(
    val pack: PackMetadataSection,
    val filter: ResourceFilterSection? = null,
)

@Serializable
data class PackMetadataSection(
    val description: KString, // TODO: use [Component]
    @SerialName("pack_format") val packFormat: KInt,
)

@Serializable
data class ResourceFilterSection(
    val block: KList<ResourceLocationPattern>,
)

@Serializable
data class ResourceLocationPattern(
    val namespace: KString? = null,
    val path: KString? = null,
)

sealed class ResourceType(val directory: KString, val extension: KString) {
    object Recipes : ResourceType("recipes", "json")
    data class Tags(val path: KString) : ResourceType("tags/$path", "json")
    object Predicates : ResourceType("predicates", "json")
    object LootTables : ResourceType("loot_tables", "json")
    object ItemModifiers : ResourceType("item_modifiers", "json")
    object Advancements : ResourceType("advancements", "json")
    object Functions : ResourceType("functions", "mcfunction")
}

@Serializable
data class Tag(
    val values: KList<@Serializable(with = Entry.Serializer::class) Entry>,
    val replace: Boolean = false,
)

@Serializable
data class Entry(
    val id: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation,
    val required: Boolean = true,
) {
    object Serializer : JsonTransformingSerializer<Entry>(serializer()) {
        override fun transformSerialize(element: JsonElement): JsonElement =
            if (element.jsonObject["required"]?.jsonPrimitive?.boolean != false) {
                element.jsonObject["id"]!!
            } else {
                element
            }

        override fun transformDeserialize(element: JsonElement): JsonElement =
            when (element) {
                is JsonPrimitive -> buildJsonObject {
                    put("id", element.content)
                    put("required", true)
                }
                else -> element
            }
    }
}

@Serializable
data class Advancement(
    val parent: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
    // TODO: display
    val rewards: AdvancementRewards? = null,
    val criteria: Map<KString, Criterion>,
    val requirements: KList<KList<KString>> = emptyList(),
)

@Serializable
data class AdvancementRewards(
    val experience: KInt = 0,
    val loot: KList<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
    val recipes: KList<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
    val function: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
)

@Serializable
@JsonClassDiscriminator("trigger")
sealed interface Criterion {
    @Serializable
    @SerialName("impossible")
    object Impossible : Criterion

    @Serializable
    @SerialName("player_killed_entity")
    object PlayerKilledEntity : Criterion

    @Serializable
    @SerialName("entity_killed_player")
    object EntityKilledPlayer : Criterion

    @Serializable
    @SerialName("enter_block")
    object EnterBlock : Criterion

    @Serializable
    @SerialName("inventory_changed")
    object InventoryChange : Criterion

    @Serializable
    @SerialName("recipe_unlocked")
    object RecipeUnlocked : Criterion

    @Serializable
    @SerialName("player_hurt_entity")
    object PlayerHurtEntity : Criterion

    @Serializable
    @SerialName("entity_hurt_player")
    object EntityHurtPlayer : Criterion

    @Serializable
    @SerialName("enchanted_item")
    object EnchantedItem : Criterion

    @Serializable
    @SerialName("filled_bucket")
    object FilledBucket : Criterion

    @Serializable
    @SerialName("brewed_potion")
    object BrewedPotion : Criterion

    @Serializable
    @SerialName("construct_beacon")
    object ConstructBeacon : Criterion

    @Serializable
    @SerialName("used_ender_eye")
    object UsedEnderEye : Criterion

    @Serializable
    @SerialName("summoned_entity")
    object SummonedEntity : Criterion

    @Serializable
    @SerialName("bred_animals")
    object BredAnimals : Criterion

    @Serializable
    @SerialName("location")
    object Location : Criterion

    @Serializable
    @SerialName("slept_in_bed")
    object SleptInBed : Criterion

    @Serializable
    @SerialName("cured_zombie_villager")
    object CuredZombieVillager : Criterion

    @Serializable
    @SerialName("villager_trade")
    object VillagerTrade : Criterion

    @Serializable
    @SerialName("item_durability_changed")
    object ItemDurabilityChanged : Criterion

    @Serializable
    @SerialName("levitation")
    object Levitation : Criterion

    @Serializable
    @SerialName("changed_dimension")
    object ChangedDimension : Criterion

    @Serializable
    @SerialName("tick")
    object Tick : Criterion

    @Serializable
    @SerialName("tame_animal")
    object TameAnimal : Criterion

    @Serializable
    @SerialName("placed_block")
    object PlacedBlock : Criterion

    @Serializable
    @SerialName("consume_item")
    object ConsumeItem : Criterion

    @Serializable
    @SerialName("effects_changed")
    object EffectsChanged : Criterion

    @Serializable
    @SerialName("used_totem")
    object UsedTotem : Criterion

    @Serializable
    @SerialName("nether_travel")
    object NetherTravel : Criterion

    @Serializable
    @SerialName("fishing_rod_hooked")
    object FishingRodHooked : Criterion

    @Serializable
    @SerialName("channeled_lightning")
    object ChanneledLightning : Criterion

    @Serializable
    @SerialName("shot_crossbow")
    object ShotCrossbow : Criterion

    @Serializable
    @SerialName("killed_by_crossbow")
    object KilledByCrossbow : Criterion

    @Serializable
    @SerialName("hero_of_the_village")
    object HeroOfTheVillage : Criterion

    @Serializable
    @SerialName("voluntary_exile")
    object VoluntaryExile : Criterion

    @Serializable
    @SerialName("slide_down_block")
    object SlideDownBlock : Criterion

    @Serializable
    @SerialName("bee_nest_destroyed")
    object BeeNestDestroyed : Criterion

    @Serializable
    @SerialName("target_hit")
    object TargetHit : Criterion

    @Serializable
    @SerialName("item_used_on_block")
    object ItemUsedOnBlock : Criterion

    @Serializable
    @SerialName("player_generates_container_loot")
    object PlayerGeneratesContainerLoot : Criterion

    @Serializable
    @SerialName("thrown_item_picked_up_by_entity")
    object ThrownItemPickedUpByEntity : Criterion

    @Serializable
    @SerialName("thrown_item_picked_up_by_player")
    object ThrownItemPickedUpByPlayer : Criterion

    @Serializable
    @SerialName("player_interacted_with_entity")
    object PlayerInteractWithEntity : Criterion

    @Serializable
    @SerialName("started_riding")
    object StartRiding : Criterion

    @Serializable
    @SerialName("lightning_strike")
    object LightningStrike : Criterion

    @Serializable
    @SerialName("using_item")
    object UsingItem : Criterion

    @Serializable
    @SerialName("fall_from_height")
    object FallFromHeight : Criterion

    @Serializable
    @SerialName("ride_entity_in_lava")
    object RideEntityInLava : Criterion

    @Serializable
    @SerialName("kill_mob_near_sculk_catalyst")
    object KillMobNearSculkCatalyst : Criterion

    @Serializable
    @SerialName("allay_drop_item_on_block")
    object AllayDropItemOnBlock : Criterion

    @Serializable
    @SerialName("avoid_vibration")
    object AvoidVibration : Criterion
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

@Serializable
data class ResourceLocation(
    val namespace: KString,
    val path: KString,
) {
    constructor(path: KString) : this(DEFAULT_NAMESPACE, path)

    object Serializer : JsonTransformingSerializer<ResourceLocation>(serializer()) {
        override fun transformSerialize(element: JsonElement): JsonElement {
            val namespace = element.jsonObject["namespace"]!!.jsonPrimitive.content
            val path = element.jsonObject["path"]!!.jsonPrimitive.content
            return JsonPrimitive("${if (DEFAULT_NAMESPACE == namespace) "" else "${normalize(namespace)}:"}${normalize(path)}")
        }

        override fun transformDeserialize(element: JsonElement): JsonElement =
            buildJsonObject {
                val parts = element.jsonPrimitive.content.split(':')
                when (parts.size) {
                    1 -> {
                        put("namespace", DEFAULT_NAMESPACE)
                        put("path", denormalize(parts[0]))
                    }
                    else -> {
                        put("namespace", denormalize(parts[0]))
                        put("path", denormalize(parts[1]))
                    }
                }
            }
    }

    companion object {
        const val DEFAULT_NAMESPACE = "minecraft"

        fun normalize(string: KString): KString =
            string.map {
                when (it) {
                    '-' -> "--"
                    in 'a'..'z', in '0'..'9', '/', '.', '_' -> it.toString()
                    else -> "-${it.code.toString(16)}"
                }
            }.joinToString("")

        fun denormalize(string: KString): KString = string // TODO
    }
}
