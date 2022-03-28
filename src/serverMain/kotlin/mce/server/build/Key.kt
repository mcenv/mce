package mce.server.build

import mce.phase.backend.defun.Defun
import mce.phase.backend.gen.Gen
import mce.phase.backend.pack.Pack
import mce.phase.backend.stage.Stage
import mce.phase.frontend.elab.Elab
import mce.phase.frontend.zonk.Zonk
import mce.phase.frontend.parse.Item as SItem

sealed class Key<V> {
    abstract val name: String

    data class Source(override val name: String) : Key<String>()
    data class SurfaceItem(override val name: String) : Key<SItem>()
    data class ElabResult(override val name: String) : Key<Elab.Result>()
    data class ZonkResult(override val name: String) : Key<Zonk.Result>()
    data class StageResult(override val name: String) : Key<Stage.Result>()
    data class DefunResult(override val name: String) : Key<Defun.Result>()
    data class PackResult(override val name: String) : Key<Pack.Result>()
    data class GenResult(override val name: String) : Key<Gen.Result>()
}
