package mce.data

import mce.minecraft.ResourceLocation
import mce.minecraft.advancements.Advancement
import mce.minecraft.advancements.AdvancementRewards
import mce.minecraft.advancements.Criterion
import mce.minecraft.chat.LiteralComponent
import mce.minecraft.packs.Pack
import mce.minecraft.packs.PackMetadata
import mce.minecraft.packs.PackMetadataSection

// TODO: minify

fun createAdvancement(name: ResourceLocation, criterion: Criterion): Pair<ResourceLocation, Advancement> =
    name to Advancement(
        rewards = AdvancementRewards(
            function = name
        ),
        criteria = mapOf("" to criterion),
    )

val BREWED_POTION = createAdvancement(ResourceLocation("brewed_potion"), Criterion.BrewedPotion)
val ENCHANTED_ITEM = createAdvancement(ResourceLocation("enchanted_item"), Criterion.EnchantedItem)
val FILLED_BUCKET = createAdvancement(ResourceLocation("filled_bucket"), Criterion.FilledBucket)
val FISHING_ROD_HOOKED = createAdvancement(ResourceLocation("fishing_rod_hooked"), Criterion.FishingRodHooked)
val ITEM_USED_ON_BLOCK = createAdvancement(ResourceLocation("item_used_on_block"), Criterion.ItemUsedOnBlock)
val PLACED_BLOCK = createAdvancement(ResourceLocation("placed_block"), Criterion.PlacedBlock)
val PLAYER_GENERATES_CONTAINER_LOOT = createAdvancement(ResourceLocation("player_generates_container_loot"), Criterion.PlayerGeneratesContainerLoot)
val PLAYER_HURT_ENTITY = createAdvancement(ResourceLocation("player_hurt_entity"), Criterion.PlayerHurtEntity)
val PLAYER_INTERACTED_WITH_ENTITY = createAdvancement(ResourceLocation("player_interacted_with_entity"), Criterion.PlayerInteractedWithEntity)
val PLAYER_KILLED_ENTITY = createAdvancement(ResourceLocation("player_killed_entity"), Criterion.PlayerKilledEntity)
val SHOT_CROSSBOW = createAdvancement(ResourceLocation("shot_crossbow"), Criterion.ShotCrossbow)
val TAME_ANIMAL = createAdvancement(ResourceLocation("tame_animal"), Criterion.TameAnimal)
val USED_ENDER_EYE = createAdvancement(ResourceLocation("used_ender_eye"), Criterion.UsedEnderEye)
val VILLAGER_TRADE = createAdvancement(ResourceLocation("villager_trade"), Criterion.VillagerTrade)

val PACK = Pack(
    metadata = PackMetadata(
        pack = PackMetadataSection(
            description = LiteralComponent(""),
            packFormat = 10,
        )
    ),
    advancements = mapOf(
        BREWED_POTION,
        ENCHANTED_ITEM,
        FILLED_BUCKET,
        FISHING_ROD_HOOKED,
        ITEM_USED_ON_BLOCK,
        PLACED_BLOCK,
        PLAYER_GENERATES_CONTAINER_LOOT,
        PLAYER_HURT_ENTITY,
        PLAYER_INTERACTED_WITH_ENTITY,
        PLAYER_KILLED_ENTITY,
        SHOT_CROSSBOW,
        TAME_ANIMAL,
        USED_ENDER_EYE,
        VILLAGER_TRADE,
    )
)

fun main() {
    PACK.gen()
}
