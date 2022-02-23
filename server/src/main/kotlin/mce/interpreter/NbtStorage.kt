package mce.interpreter

class NbtStorage(private val storage: MutableMap<String, MutableNbt.Compound>) : MutableMap<String, MutableNbt.Compound> by storage {
    override fun get(key: String): MutableNbt.Compound =
        storage[key] ?: MutableNbt.Compound(mutableMapOf())

    override fun put(key: String, value: MutableNbt.Compound): MutableNbt.Compound? =
        if (value.isEmpty()) storage.remove(key) else storage.put(key, value)
}
