package mce.data

import mce.minecraft.chat.LiteralComponent
import mce.minecraft.packs.Pack
import mce.minecraft.packs.PackMetadata
import mce.minecraft.packs.PackMetadataSection
import java.nio.file.Paths
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

// TODO: minify

val PACK = Pack(
    metadata = PackMetadata(
        pack = PackMetadataSection(
            description = LiteralComponent(""),
            packFormat = 10,
        )
    ),
    functions = mapOf(
        FUNCTION_BREWED_POTION,
        FUNCTION_ENCHANTED_ITEM,
        FUNCTION_FILLED_BUCKET,
        FUNCTION_FISHING_ROD_HOOKED,
        FUNCTION_ITEM_USED_ON_BLOCK,
        FUNCTION_PLACED_BLOCK,
        FUNCTION_PLAYER_GENERATES_CONTAINER_LOOT,
        FUNCTION_PLAYER_HURT_ENTITY,
        FUNCTION_PLAYER_INTERACTED_WITH_ENTITY,
        FUNCTION_PLAYER_KILLED_ENTITY,
        FUNCTION_SHOT_CROSSBOW,
        FUNCTION_TAME_ANIMAL,
        FUNCTION_USED_ENDER_EYE,
        FUNCTION_VILLAGER_TRADE,
    ),
    advancements = mapOf(
        ADVANCEMENT_BREWED_POTION,
        ADVANCEMENT_ENCHANTED_ITEM,
        ADVANCEMENT_FILLED_BUCKET,
        ADVANCEMENT_FISHING_ROD_HOOKED,
        ADVANCEMENT_ITEM_USED_ON_BLOCK,
        ADVANCEMENT_PLACED_BLOCK,
        ADVANCEMENT_PLAYER_GENERATES_CONTAINER_LOOT,
        ADVANCEMENT_PLAYER_HURT_ENTITY,
        ADVANCEMENT_PLAYER_INTERACTED_WITH_ENTITY,
        ADVANCEMENT_PLAYER_KILLED_ENTITY,
        ADVANCEMENT_SHOT_CROSSBOW,
        ADVANCEMENT_TAME_ANIMAL,
        ADVANCEMENT_USED_ENDER_EYE,
        ADVANCEMENT_VILLAGER_TRADE,
    ),
)

fun main() {
    ZipOutputStream(Paths.get("pack.zip").outputStream().buffered()).use {
        PACK.gen(it)
    }
}
