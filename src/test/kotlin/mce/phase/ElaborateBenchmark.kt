package mce.phase

import mce.fetch
import mce.phase.frontend.Elaborate
import mce.server.Key
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class ElaborateBenchmark {
    private fun elaborate(name: String): Elaborate.Result = fetch(Key.ElaborateResult(name))

    @Benchmark
    fun elaborate(): Elaborate.Result = elaborate("elaborate")
}
