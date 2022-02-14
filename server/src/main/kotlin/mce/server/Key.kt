package mce.server

import mce.graph.Core
import mce.graph.Defunctionalized
import mce.graph.Surface
import mce.phase.Elaborate
import mce.phase.Zonk

sealed class Key<V> {
    abstract val name: String

    data class Source(override val name: String) : Key<String>()
    data class SurfaceItem(override val name: String) : Key<Surface.Item>()
    data class ElaboratedOutput(override val name: String) : Key<Elaborate.Output>()
    data class ZonkedOutput(override val name: String) : Key<Zonk.Output>()
    data class StagedItem(override val name: String) : Key<Core.Item>()
    data class DefunctionalizedItem(override val name: String) : Key<Defunctionalized.Item>()
}
