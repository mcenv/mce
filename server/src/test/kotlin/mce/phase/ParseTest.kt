package mce.phase

import mce.read
import kotlin.test.Test

class ParseTest {
    @Test
    fun parseAll() {
        Parse("parse", read("/parse.mce"))
    }
}
