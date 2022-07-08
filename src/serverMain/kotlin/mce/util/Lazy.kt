package mce.util

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Lazy<T>.not(): T = value
