package mce.server

import mce.phase.backend.Defun
import mce.phase.backend.Stage
import mce.phase.frontend.Elaborate
import mce.phase.frontend.Zonk
import mce.ast.pack.Datapack as PDatapack
import mce.ast.surface.Item as SItem

sealed class Key<V> {
    abstract val name: String

    data class Source(override val name: String) : Key<String>()
    data class SurfaceItem(override val name: String) : Key<SItem>()
    data class ElaborateResult(override val name: String) : Key<Elaborate.Result>()
    data class ZonkResult(override val name: String) : Key<Zonk.Result>()
    data class StageResult(override val name: String) : Key<Stage.Result>()
    data class DefunctionalizeResult(override val name: String) : Key<Defun.Result>()
    data class Datapack(override val name: String) : Key<PDatapack>()
}
