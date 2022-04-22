package mce.server.build

import mce.pass.backend.Defun
import mce.pass.backend.Pack
import mce.pass.backend.Stage
import mce.pass.frontend.Elab
import mce.pass.frontend.Zonk
import mce.ast.surface.Item as SItem

sealed class Key<V> {
    data class Source(val name: String) : Key<String>()
    data class SurfaceItem(val name: String) : Key<SItem>()
    data class ElabResult(val name: String) : Key<Elab.Result>()
    data class ZonkResult(val name: String) : Key<Zonk.Result>()
    data class StageResult(val name: String) : Key<Stage.Result>()
    data class DefunResult(val name: String) : Key<Defun.Result>()
    data class PackResult(val name: String) : Key<Pack.Result>()
    object GenResult : Key<Unit>()
}
