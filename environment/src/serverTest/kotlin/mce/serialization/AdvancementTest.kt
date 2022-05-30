package mce.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mce.ast.pack.Advancement
import mce.ast.pack.AdvancementRewards
import mce.ast.pack.Criterion
import mce.minecraft.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class AdvancementTest {
    @Test
    fun serialize() {
        val expected = """{"criteria":{"a":{"trigger":"impossible"}}}"""
        val actual = Json.encodeToString(
            serializer(), Advancement(
                criteria = mapOf("a" to Criterion.Impossible),
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun serializeParent() {
        val expected = """{"parent":"a","criteria":{"a":{"trigger":"impossible"}}}"""
        val actual = Json.encodeToString(
            serializer(), Advancement(
                parent = ResourceLocation("a"),
                criteria = mapOf("a" to Criterion.Impossible)
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun serializeParentEmptyRewards() {
        val expected = """{"parent":"a","rewards":{},"criteria":{"a":{"trigger":"impossible"}}}"""
        val actual = Json.encodeToString(
            serializer(), Advancement(
                parent = ResourceLocation("a"),
                rewards = AdvancementRewards(),
                criteria = mapOf("a" to Criterion.Impossible)
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun serializeParentRewards() {
        val expected = """{"parent":"a","rewards":{"function":"a"},"criteria":{"a":{"trigger":"impossible"}}}"""
        val actual = Json.encodeToString(
            serializer(), Advancement(
                parent = ResourceLocation("a"),
                rewards = AdvancementRewards(
                    function = ResourceLocation("a"),
                ),
                criteria = mapOf("a" to Criterion.Impossible)
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun serializeParentEmptyRewardsRequirements() {
        val expected = """{"parent":"a","rewards":{"function":"a"},"criteria":{"a":{"trigger":"impossible"}},"requirements":[["a"]]}"""
        val actual = Json.encodeToString(
            serializer(), Advancement(
                parent = ResourceLocation("a"),
                rewards = AdvancementRewards(
                    function = ResourceLocation("a"),
                ),
                criteria = mapOf("a" to Criterion.Impossible),
                requirements = listOf(listOf("a")),
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expected = Advancement(
            criteria = mapOf("a" to Criterion.Impossible),
        )
        val actual = Json.decodeFromString<Advancement>(serializer(), """{"criteria":{"a":{"trigger":"impossible"}}}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeParent() {
        val expected = Advancement(
            parent = ResourceLocation("a"),
            criteria = mapOf("a" to Criterion.Impossible)
        )
        val actual = Json.decodeFromString<Advancement>(serializer(), """{"parent":"a","criteria":{"a":{"trigger":"impossible"}}}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeParentEmptyRewards() {
        val expected = Advancement(
            parent = ResourceLocation("a"),
            rewards = AdvancementRewards(),
            criteria = mapOf("a" to Criterion.Impossible)
        )
        val actual = Json.decodeFromString<Advancement>(serializer(), """{"parent":"a","rewards":{},"criteria":{"a":{"trigger":"impossible"}}}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeParentRewards() {
        val expected = Advancement(
            parent = ResourceLocation("a"),
            rewards = AdvancementRewards(
                function = ResourceLocation("a"),
            ),
            criteria = mapOf("a" to Criterion.Impossible)
        )
        val actual = Json.decodeFromString<Advancement>(serializer(), """{"parent":"a","rewards":{"function":"a"},"criteria":{"a":{"trigger":"impossible"}}}""")
        assertEquals(expected, actual)
    }

    @Test
    fun deserializeParentEmptyRewardsRequirements() {
        val expected = Advancement(
            parent = ResourceLocation("a"),
            rewards = AdvancementRewards(
                function = ResourceLocation("a"),
            ),
            criteria = mapOf("a" to Criterion.Impossible),
            requirements = listOf(listOf("a")),
        )
        val actual = Json.decodeFromString<Advancement>(serializer(), """{"parent":"a","rewards":{"function":"a"},"criteria":{"a":{"trigger":"impossible"}},"requirements":[["a"]]}""")
        assertEquals(expected, actual)
    }
}
