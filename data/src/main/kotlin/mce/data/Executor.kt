package mce.data

import mce.minecraft.ResourceLocation
import mce.minecraft.advancements.Advancement
import mce.minecraft.advancements.AdvancementRewards
import mce.minecraft.advancements.Criterion
import mce.minecraft.commands.Command
import mce.minecraft.commands.CommandFunction

fun createFunction(name: ResourceLocation): Pair<ResourceLocation, CommandFunction> =
    name to CommandFunction(
        listOf(
            Command.Advancement.Perform(Command.Advancement.Action.REVOKE, "@s", Command.Advancement.Mode.Only(name)),
            Command.Function.RunFunction(ResourceLocation("poll_task")),
        ),
    )

fun createAdvancement(name: ResourceLocation, criterion: Criterion): Pair<ResourceLocation, Advancement> =
    name to Advancement(
        rewards = AdvancementRewards(
            function = name
        ),
        criteria = mapOf("" to criterion),
    )

val BREWED_POTION = ResourceLocation("brewed_potion")
val ENCHANTED_ITEM = ResourceLocation("enchanted_item")
val FILLED_BUCKET = ResourceLocation("filled_bucket")
val FISHING_ROD_HOOKED = ResourceLocation("fishing_rod_hooked")
val ITEM_USED_ON_BLOCK = ResourceLocation("item_used_on_block")
val PLACED_BLOCK = ResourceLocation("placed_block")
val PLAYER_GENERATES_CONTAINER_LOOT = ResourceLocation("player_generates_container_loot")
val PLAYER_HURT_ENTITY = ResourceLocation("player_hurt_entity")
val PLAYER_INTERACTED_WITH_ENTITY = ResourceLocation("player_interacted_with_entity")
val PLAYER_KILLED_ENTITY = ResourceLocation("player_killed_entity")
val SHOT_CROSSBOW = ResourceLocation("shot_crossbow")
val TAME_ANIMAL = ResourceLocation("tame_animal")
val USED_ENDER_EYE = ResourceLocation("used_ender_eye")
val VILLAGER_TRADE = ResourceLocation("villager_trade")

val FUNCTION_BREWED_POTION = createFunction(BREWED_POTION)
val FUNCTION_ENCHANTED_ITEM = createFunction(ENCHANTED_ITEM)
val FUNCTION_FILLED_BUCKET = createFunction(FILLED_BUCKET)
val FUNCTION_FISHING_ROD_HOOKED = createFunction(FISHING_ROD_HOOKED)
val FUNCTION_ITEM_USED_ON_BLOCK = createFunction(ITEM_USED_ON_BLOCK)
val FUNCTION_PLACED_BLOCK = createFunction(PLACED_BLOCK)
val FUNCTION_PLAYER_GENERATES_CONTAINER_LOOT = createFunction(PLAYER_GENERATES_CONTAINER_LOOT)
val FUNCTION_PLAYER_HURT_ENTITY = createFunction(PLAYER_HURT_ENTITY)
val FUNCTION_PLAYER_INTERACTED_WITH_ENTITY = createFunction(PLAYER_INTERACTED_WITH_ENTITY)
val FUNCTION_PLAYER_KILLED_ENTITY = createFunction(PLAYER_KILLED_ENTITY)
val FUNCTION_SHOT_CROSSBOW = createFunction(SHOT_CROSSBOW)
val FUNCTION_TAME_ANIMAL = createFunction(TAME_ANIMAL)
val FUNCTION_USED_ENDER_EYE = createFunction(USED_ENDER_EYE)
val FUNCTION_VILLAGER_TRADE = createFunction(VILLAGER_TRADE)

val ADVANCEMENT_BREWED_POTION = createAdvancement(BREWED_POTION, Criterion.BrewedPotion)
val ADVANCEMENT_ENCHANTED_ITEM = createAdvancement(ENCHANTED_ITEM, Criterion.EnchantedItem)
val ADVANCEMENT_FILLED_BUCKET = createAdvancement(FILLED_BUCKET, Criterion.FilledBucket)
val ADVANCEMENT_FISHING_ROD_HOOKED = createAdvancement(FISHING_ROD_HOOKED, Criterion.FishingRodHooked)
val ADVANCEMENT_ITEM_USED_ON_BLOCK = createAdvancement(ITEM_USED_ON_BLOCK, Criterion.ItemUsedOnBlock)
val ADVANCEMENT_PLACED_BLOCK = createAdvancement(PLACED_BLOCK, Criterion.PlacedBlock)
val ADVANCEMENT_PLAYER_GENERATES_CONTAINER_LOOT = createAdvancement(PLAYER_GENERATES_CONTAINER_LOOT, Criterion.PlayerGeneratesContainerLoot)
val ADVANCEMENT_PLAYER_HURT_ENTITY = createAdvancement(PLAYER_HURT_ENTITY, Criterion.PlayerHurtEntity)
val ADVANCEMENT_PLAYER_INTERACTED_WITH_ENTITY = createAdvancement(PLAYER_INTERACTED_WITH_ENTITY, Criterion.PlayerInteractedWithEntity)
val ADVANCEMENT_PLAYER_KILLED_ENTITY = createAdvancement(PLAYER_KILLED_ENTITY, Criterion.PlayerKilledEntity)
val ADVANCEMENT_SHOT_CROSSBOW = createAdvancement(SHOT_CROSSBOW, Criterion.ShotCrossbow)
val ADVANCEMENT_TAME_ANIMAL = createAdvancement(TAME_ANIMAL, Criterion.TameAnimal)
val ADVANCEMENT_USED_ENDER_EYE = createAdvancement(USED_ENDER_EYE, Criterion.UsedEnderEye)
val ADVANCEMENT_VILLAGER_TRADE = createAdvancement(VILLAGER_TRADE, Criterion.VillagerTrade)
