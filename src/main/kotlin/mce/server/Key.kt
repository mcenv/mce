package mce.server

import mce.graph.Core
import mce.graph.Surface

sealed class Key<T> {
    abstract val name: String

    data class Item(override val name: String) : Key<Surface.Item>()
    data class Elaborated(override val name: String) : Key<Core.Output>()
}
