package mce.serialization

import kotlinx.serialization.json.Json
import mce.ast.pack.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceLocationTest {
    @Test
    fun serialize() {
        val expected = """"a:b""""
        val actual = Json.encodeToString(ResourceLocation.Serializer, ResourceLocation("a", "b"))
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expected = ResourceLocation("a", "b")
        val actual = Json.decodeFromString(ResourceLocation.Serializer, """"a:b"""")
        assertEquals(expected, actual)
    }

    @Test
    fun serializeDefault() {
        val expected = """"a""""
        val actual = Json.encodeToString(ResourceLocation.Serializer, ResourceLocation(ResourceLocation.DEFAULT_NAMESPACE, "a"))
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeDefault() {
        val expected = ResourceLocation("a")
        val actual = Json.decodeFromString(ResourceLocation.Serializer, """"a"""")
        assertEquals(expected, actual)
    }
}
