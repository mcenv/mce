package mce.pass

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import mce.ast.surface.Item
import mce.fetch
import mce.pass.backend.Defun
import mce.pass.backend.Gen
import mce.pass.backend.Pack
import mce.pass.backend.Stage
import mce.pass.frontend.Elab
import mce.pass.frontend.Zonk
import mce.server.build.Key

@State(Scope.Benchmark)
class Benchmarks {
    @Benchmark
    fun source(): String = fetch(Key.Source("elaborate"))

    @Benchmark
    fun surface(): Item = fetch(Key.SurfaceItem("elaborate"))

    @Benchmark
    fun elab(): Elab.Result = fetch(Key.ElabResult("elaborate"))

    @Benchmark
    fun zonk(): Zonk.Result = fetch(Key.ZonkResult("elaborate"))

    @Benchmark
    fun stage(): Stage.Result = fetch(Key.StageResult("elaborate"))

    @Benchmark
    fun defun(): Defun.Result = fetch(Key.DefunResult("elaborate"))

    @Benchmark
    fun pack(): Pack.Result = fetch(Key.PackResult("elaborate"))

    @Benchmark
    fun gen(): Gen.Result = fetch(Key.GenResult("elaborate"))
}
