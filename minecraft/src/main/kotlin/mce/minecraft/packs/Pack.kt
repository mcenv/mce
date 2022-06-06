package mce.minecraft.packs

import mce.minecraft.ResourceLocation
import mce.minecraft.advancements.Advancement

// TODO: fill in [Any]
data class Pack(
    val metadata: PackMetadata,
    val functions: Map<ResourceLocation, Any> = emptyMap(),
    val advancements: Map<ResourceLocation, Advancement> = emptyMap(),
    val recipes: Map<ResourceLocation, Any> = emptyMap(),
    val itemModifiers: Map<ResourceLocation, Any> = emptyMap(),
    val lootTables: Map<ResourceLocation, Any> = emptyMap(),
    val predicates: Map<ResourceLocation, Any> = emptyMap(),
    val tagsBlocks: Map<ResourceLocation, Any> = emptyMap(),
    val tagsEntityTypes: Map<ResourceLocation, Any> = emptyMap(),
    val tagsFluids: Map<ResourceLocation, Any> = emptyMap(),
    val tagsGameEvents: Map<ResourceLocation, Any> = emptyMap(),
    val tagsItems: Map<ResourceLocation, Any> = emptyMap(),
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
)
