package mce.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer
import mce.ast.surface.Term
import mce.pass.freshId
import mce.util.ByteArrayInputStream
import mce.util.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializeTest {
    @Test
    @ExperimentalSerializationApi
    fun identity() {
        val expected = Term.Anno(Term.IntOf(0, freshId()), Term.Int(freshId()), freshId())
        val output = ByteArrayOutputStream()
        MceEncoder(output).encodeSerializableValue<Term>(serializer(), expected)
        val input = ByteArrayInputStream(output.toByteArray())
        val actual = MceDecoder(input).decodeSerializableValue<Term>(serializer())
        assertEquals(expected, actual)
    }
}
