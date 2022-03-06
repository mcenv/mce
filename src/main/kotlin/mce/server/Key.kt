package mce.server

import mce.graph.Packed
import mce.graph.Surface
import mce.phase.front.Elaborate
import mce.phase.front.Zonk
import mce.phase.middle.Defunctionalize
import mce.phase.middle.Stage

sealed class Key<V> {
    abstract val name: String

    data class Source(override val name: String) : Key<String>()
    data class SurfaceItem(override val name: String) : Key<Surface.Item>()
    data class ElaborateResult(override val name: String) : Key<Elaborate.Result>()
    data class ZonkResult(override val name: String) : Key<Zonk.Result>()
    data class StageResult(override val name: String) : Key<Stage.Result>()
    data class DefunctionalizeResult(override val name: String) : Key<Defunctionalize.Result>()
    data class Datapack(override val name: String) : Key<Packed.Datapack>()
}
