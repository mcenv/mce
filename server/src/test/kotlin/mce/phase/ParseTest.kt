package mce.phase

import kotlin.test.Test

class ParseTest {
    @Test
    fun parseAll() {
        Parse(
            """
            (definition f () false (list union (byte short int long float double string)) list_of (0b 1s 2 3l 4.0f 5.0d "6"))
        """
        )
    }
}
