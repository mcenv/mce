package mce.phase

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import mce.fetch
import mce.phase.backend.defun.Defun
import mce.phase.backend.gen.Gen
import mce.phase.backend.pack.Pack
import mce.phase.backend.stage.Stage
import mce.phase.frontend.decode.Item
import mce.phase.frontend.elab.Elab
import mce.phase.frontend.zonk.Zonk
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
