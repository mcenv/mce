package mce.serialization

import kotlinx.serialization.serializer
import mce.phase.freshId
import mce.phase.frontend.parse.Term
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializeTest {
    @Test
    fun identity() {
        val expected = Term.Anno(Term.IntOf(0, freshId()), Term.Int(freshId()), freshId())
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            MceEncoder(output).encodeSerializableValue<Term>(serializer(), expected)

            DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
                val actual = MceDecoder(input).decodeSerializableValue<Term>(serializer())
                assertEquals(expected, actual)
            }
        }
    }
}
