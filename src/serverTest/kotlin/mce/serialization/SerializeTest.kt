package mce.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import mce.ast.surface.Term
import mce.pass.freshId
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalSerializationApi
class SerializeTest {
    @Test
    fun identity() {
        val expected = Term.Anno(Term.IntOf(0, freshId()), Term.Int(freshId()), freshId())
        val output = Mce.encodeToByteArray<Term>(serializer(), expected)
        val actual = Mce.decodeFromByteArray<Term>(serializer(), output)
        assertEquals(expected, actual)
    }

    @Serializable
    object A

    @Test
    fun obj() {
        val expected = A
        val output = Mce.encodeToByteArray<A>(serializer(), expected)
        val actual = Mce.decodeFromByteArray<A>(serializer(), output)
        assertEquals(expected, actual)
    }

    @Serializable
    sealed class B {
        @Serializable
        object B1 : B()
    }

    @Test
    fun polyObj() {
        val expected = B.B1
        val output = Mce.encodeToByteArray<B>(serializer(), expected)
        val actual = Mce.decodeFromByteArray<B>(serializer(), output)
        assertEquals(expected, actual)
    }
}
