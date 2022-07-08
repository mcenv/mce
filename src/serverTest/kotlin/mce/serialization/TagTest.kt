package mce.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mce.minecraft.ResourceLocation
import mce.minecraft.tags.Entry
import mce.minecraft.tags.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

class TagTest {
    @Test
    fun serializeEmpty() {
        val expected = """{"values":[]}"""
        val actual = Json.encodeToString(serializer(), Tag(listOf()))
        assertEquals(expected, actual)
    }

    @Test
    fun serializeReplace() {
        val expected = """{"values":[],"replace":true}"""
        val actual = Json.encodeToString(serializer(), Tag(listOf(), true))
        assertEquals(expected, actual)
    }

    @Test
    fun serializeValue() {
        val expected = """{"values":["a"]}"""
        val actual = Json.encodeToString(serializer(), Tag(listOf(Entry(ResourceLocation("a")))))
        assertEquals(expected, actual)
    }

    @Test
    fun serializeValueRequired() {
        val expected = """{"values":[{"id":"a","required":false}]}"""
        val actual = Json.encodeToString(serializer(), Tag(listOf(Entry(ResourceLocation("a"), false))))
        assertEquals(expected, actual)
    }

    @Test
    fun serializeValues() {
        val expected = """{"values":["a",{"id":"b","required":false}]}"""
        val actual = Json.encodeToString(serializer(), Tag(listOf(Entry(ResourceLocation("a")), Entry(ResourceLocation("b"), false))))
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeEmpty() {
        val expected = Tag(emptyList())
        val actual = Json.decodeFromString<Tag>(serializer(), """{"values":[]}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeReplace() {
        val expected = Tag(emptyList(), true)
        val actual = Json.decodeFromString<Tag>(serializer(), """{"values":[],"replace":true}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeValue() {
        val expected = Tag(listOf(Entry(ResourceLocation("a"))))
        val actual = Json.decodeFromString<Tag>(serializer(), """{"values":["a"]}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeValueRequired() {
        val expected = Tag(listOf(Entry(ResourceLocation("a"), false)))
        val actual = Json.decodeFromString<Tag>(serializer(), """{"values":[{"id":"a","required":false}]}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeValues() {
        val expected = Tag(listOf(Entry(ResourceLocation("a")), Entry(ResourceLocation("b"), false)))
        val actual = Json.decodeFromString<Tag>(serializer(), """{"values":["a",{"id":"b","required":false}]}""")
        assertEquals(expected, actual)
    }
}
