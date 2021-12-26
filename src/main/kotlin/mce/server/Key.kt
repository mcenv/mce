package mce.server

import mce.graph.Core
import mce.graph.Erased
import mce.graph.Surface

sealed class Key<V> {
    abstract val name: String

    data class Item(override val name: String) : Key<Surface.Item>()
    data class Elaborated(override val name: String) : Key<Core.Output>()
    data class ErasedItem(override val name: String) : Key<Erased.Item>()
}
