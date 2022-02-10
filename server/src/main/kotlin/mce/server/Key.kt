package mce.server

import mce.graph.Staged
import mce.graph.Surface
import mce.phase.Elaborate

sealed class Key<V> {
    abstract val name: String

    data class Source(override val name: String) : Key<String>()
    data class SurfaceItem(override val name: String) : Key<Surface.Item>()
    data class ElaboratedOutput(override val name: String) : Key<Elaborate.Output>()
    data class StagedItem(override val name: String) : Key<Staged.Item>()
}
