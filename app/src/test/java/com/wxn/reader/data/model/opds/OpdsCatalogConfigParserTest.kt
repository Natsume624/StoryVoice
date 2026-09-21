package com.wxn.reader.data.model.opds

import com.wxn.reader.domain.util.OpdsUrlAssist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-13-plan-opds-remove-wenyuange-and-add-catalog-url-assist.md §6.1（F1–F5）
 */
class OpdsCatalogConfigParserTest {

    private val fallback = OpdsCatalogConfigParser.FALLBACK_CATALOGS.catalogs

    @Test
    fun `fallback catalogs do not contain Wenyuange`() {
        assertFalse(fallback.any { it.id.equals("Wenyuange", ignoreCase = true) })
        assertFalse(fallback.any { it.url.contains("wenyuange.org") })
    }

    @Test
    fun `fallback catalog ids are unique and non-blank`() {
        assertTrue(fallback.isNotEmpty())
        val ids = fallback.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `fallback catalog urls are valid http or https`() {
        fallback.forEach { item ->
            assertTrue("invalid url: ${item.url}", OpdsUrlAssist.isValid(item.url))
        }
    }

    @Test
    fun `fallback contains the five expected sources`() {
        assertEquals(
            setOf("gutenberg", "manybooks", "unglue", "gallica", "lzzy"),
            fallback.map { it.id }.toSet()
        )
    }

    @Test
    fun `parse tolerates unknown json fields`() {
        val json = """
            {
              "version": 6,
              "updated": "2026-09-15 12:00:00",
              "futureField": true,
              "catalogs": [
                {"id":"a","name":"A","url":"https://a.example/opds","futureItemField":1}
              ]
            }
        """.trimIndent()
        val parsed = OpdsCatalogConfigParser.parse(json)
        assertEquals(1, parsed.catalogs.size)
        assertEquals("a", parsed.catalogs[0].id)
    }
}
