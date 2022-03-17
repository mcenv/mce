package mce.util

fun <K, V> Iterable<Pair<K, V>>.toLinkedHashMap(): LinkedHashMap<K, V> = LinkedHashMap<K, V>().also { it.putAll(this) }
