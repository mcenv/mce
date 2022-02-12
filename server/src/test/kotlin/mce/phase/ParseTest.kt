package mce.phase

import mce.fetch
import mce.graph.Surface
import mce.server.Key
import kotlin.test.Test

class ParseTest {
    private fun parse(name: String, vararg imports: String): Surface.Item = fetch(Key.SurfaceItem(name), *imports)

    @Test
    fun parseAll() {
        parse("parse")
    }
}
