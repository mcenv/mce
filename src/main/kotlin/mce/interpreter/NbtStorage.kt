package mce.interpreter

class NbtStorage(private val storage: MutableMap<StringNbt, CompoundNbt>) : MutableMap<StringNbt, CompoundNbt> by storage {
    override fun get(key: StringNbt): CompoundNbt =
        storage[key] ?: CompoundNbt(mutableMapOf())

    override fun put(key: StringNbt, value: CompoundNbt): CompoundNbt? =
        if (value.isEmpty()) storage.remove(key) else storage.put(key, value)
}
