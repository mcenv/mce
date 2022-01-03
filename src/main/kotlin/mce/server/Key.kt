package mce.server

import mce.graph.Surface
import mce.phase.Elaborate

sealed class Key<V> {
    abstract val name: String

    data class Item(override val name: String) : Key<Surface.Item>()
    data class Elaborated(override val name: String) : Key<Elaborate.Output>()
}
