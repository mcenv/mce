package mce.phase

import mce.fetch
import mce.phase.frontend.Elab
import mce.server.Key
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class ElabBenchmark {
    private fun elaborate(name: String): Elab.Result = fetch(Key.ElabResult(name))

    @Benchmark
    fun elaborate(): Elab.Result = elaborate("elaborate")
}
