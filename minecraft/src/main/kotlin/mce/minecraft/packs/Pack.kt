package mce.minecraft.packs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mce.minecraft.ResourceLocation
import mce.minecraft.advancements.Advancement
import mce.minecraft.commands.CommandFunction
import mce.minecraft.tags.Tag
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// TODO: fill in [Any]
data class Pack(
    val metadata: PackMetadata,
    val functions: Map<ResourceLocation, CommandFunction> = emptyMap(),
    val advancements: Map<ResourceLocation, Advancement> = emptyMap(),
    val recipes: Map<ResourceLocation, Any> = emptyMap(),
    val itemModifiers: Map<ResourceLocation, Any> = emptyMap(),
    val lootTables: Map<ResourceLocation, Any> = emptyMap(),
    val predicates: Map<ResourceLocation, Any> = emptyMap(),
    val tagsFunctions: Map<ResourceLocation, Tag> = emptyMap(),
    val tagsBlocks: Map<ResourceLocation, Tag> = emptyMap(),
    val tagsEntityTypes: Map<ResourceLocation, Tag> = emptyMap(),
    val tagsFluids: Map<ResourceLocation, Tag> = emptyMap(),
    val tagsGameEvents: Map<ResourceLocation, Tag> = emptyMap(),
    val tagsItems: Map<ResourceLocation, Tag> = emptyMap(),
    val dimensionType: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenBiome: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenNoiseSettings: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenConfiguredCarver: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenConfiguredFeature: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenPlacedFeature: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenStructure: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenStructureSet: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenProcessorList: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenTemplatePool: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenNoise: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenDensityFunction: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenWorldPreset: Map<ResourceLocation, Any> = emptyMap(),
    val worldgenFlatLevelGeneratorPreset: Map<ResourceLocation, Any> = emptyMap(),
    val chatType: Map<ResourceLocation, Any> = emptyMap(),
) {
    fun gen(output: ZipOutputStream): Unit = with(output) {
        putNextEntry(ZipEntry("pack.mcmeta"))
        Json.encodeToStream(metadata, this)
        closeEntry()

        functions.forEach { (name, function) ->
            putNextEntry(ZipEntry("data/${name.namespace}/functions/${name.path}.mcfunction"))
            function.gen(output)
            closeEntry()
        }

        advancements.forEach(json("advancements"))
        recipes.forEach(json("recipes"))
        itemModifiers.forEach(json("item_modifiers"))
        lootTables.forEach(json("loot_tables"))
        predicates.forEach(json("predicates"))
        tagsFunctions.forEach(json("tags/functions"))
        tagsBlocks.forEach(json("tags/blocks"))
        tagsEntityTypes.forEach(json("tags/entity_types"))
        tagsFluids.forEach(json("tags/fluids"))
        tagsGameEvents.forEach(json("tags/game_events"))
        tagsItems.forEach(json("tags/items"))
        dimensionType.forEach(json("dimension_type"))
        worldgenBiome.forEach(json("worldgen/biome"))
        worldgenNoiseSettings.forEach(json("worldgen/noise_settings"))
        worldgenConfiguredCarver.forEach(json("worldgen/configured_carver"))
        worldgenConfiguredFeature.forEach(json("worldgen/configured_feature"))
        worldgenPlacedFeature.forEach(json("worldgen/placed_feature"))
        worldgenStructure.forEach(json("worldgen/structure"))
        worldgenStructureSet.forEach(json("worldgen/structure_set"))
        worldgenProcessorList.forEach(json("worldgen/processor_list"))
        worldgenTemplatePool.forEach(json("worldgen/template_pool"))
        worldgenNoise.forEach(json("worldgen/noise"))
        worldgenDensityFunction.forEach(json("worldgen/density_function"))
        worldgenWorldPreset.forEach(json("worldgen/world_preset"))
        worldgenFlatLevelGeneratorPreset.forEach(json("worldgen/flat_level_generator_preset"))
        chatType.forEach(json("chat_type"))
    }

    private inline fun <reified T> ZipOutputStream.json(type: String): (Map.Entry<ResourceLocation, T>) -> Unit = { (name, value) ->
        putNextEntry(ZipEntry("data/${name.namespace}/$type/${name.path}.json"))
        Json.encodeToStream(value, this)
        closeEntry()
    }
}
