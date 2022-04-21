package mce.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class Advancement(
    val parent: @Serializable(with = ResourceLocation.Serializer::class) ResourceLocation? = null,
    // TODO: display
    val rewards: AdvancementRewards? = null,
    val criteria: Map<String, Criterion>,
    val requirements: List<List<String>> = emptyList(),
)

@Serializable
data class AdvancementRewards(
    val experience: Int = 0,
    val loot: List<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
    val recipes: List<@Serializable(with = ResourceLocation.Serializer::class) ResourceLocation> = emptyList(),
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
