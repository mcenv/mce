package mce.emulator

import mce.ast.Packed.ResourceLocation

class NbtStorage(
    private val storage: MutableMap<ResourceLocation, CompoundNbt> = mutableMapOf(),
) : MutableMap<ResourceLocation, CompoundNbt> by storage {
    override fun get(key: ResourceLocation): CompoundNbt =
        storage.computeIfAbsent(key) { CompoundNbt(mutableMapOf()) }

    override fun put(key: ResourceLocation, value: CompoundNbt): CompoundNbt? =
        if (value.isEmpty()) storage.remove(key) else storage.put(key, value)
}
