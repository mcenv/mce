package mce.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mce.ast.pack.PackMetadata
import mce.ast.pack.PackMetadataSection
import mce.ast.pack.ResourceFilterSection
import mce.ast.pack.ResourceLocationPattern
import kotlin.test.Test
import kotlin.test.assertEquals

class PackMetadataTest {
    @Test
    fun packMetadata() {
        val expected = """{"pack":{"description":"a","pack_format":10}}"""
        val actual = Json.encodeToString(
            serializer(),
            PackMetadata(
                PackMetadataSection(
                    "a",
                    10,
                ),
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun emptyPackFilters() {
        val expected = """{"pack":{"description":"a","pack_format":10},"filter":{"block":[]}}"""
        val actual = Json.encodeToString(
            serializer(),
            PackMetadata(
                PackMetadataSection(
                    "a",
                    10,
                ),
                ResourceFilterSection(
                    emptyList(),
                ),
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun packFilter() {
        val expected = """{"pack":{"description":"a","pack_format":10},"filter":{"block":[{"namespace":"a","path":"b"}]}}"""
        val actual = Json.encodeToString(
            serializer(),
            PackMetadata(
                PackMetadataSection(
                    "a",
                    10,
                ),
                ResourceFilterSection(
                    listOf(
                        ResourceLocationPattern(
                            "a",
                            "b",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun packFilters() {
        val expected = """{"pack":{"description":"a","pack_format":10},"filter":{"block":[{"namespace":"a","path":"b"},{"namespace":"c","path":"d"}]}}"""
        val actual = Json.encodeToString(
            serializer(),
            PackMetadata(
                PackMetadataSection(
                    "a",
                    10,
                ),
                ResourceFilterSection(
                    listOf(
                        ResourceLocationPattern(
                            "a",
                            "b",
                        ),
                        ResourceLocationPattern(
                            "c",
                            "d",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(expected, actual)
    }
}