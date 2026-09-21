package com.wxn.reader.data.remote.opds

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 方案 docs/plans/2026-09-17-plan-opds-duplicate-entry-id-crash.md §4
 * T1-T4 + T7（解析层）：Fix 1b 空 id 条目合成确定性 id；策略 B 重复 id 条目忠实保留不去重。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Robolectric 4.14.1 最高支持 SDK 34；compileSdk=36 需显式锁定（同 BackupImporterTest 既有做法）
class OpdsFeedParserTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun parse(xml: String) =
        OpdsFeedParser.parse(context, xml, catalogId = 0, baseUrl = "http://test/").entries

    private fun feed(entriesXml: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <id>series-1</id>
          <title>Series</title>
          $entriesXml
        </feed>
    """.trimIndent()

    /** T1：缺失 <id> 的条目合成带 urn:handyreader:synthetic: 前缀的非空 id */
    @Test
    fun t1_blankIdEntryGetsSyntheticId() {
        val entries = parse(feed("<entry><title>No Id Book</title></entry>"))
        assertEquals(1, entries.size)
        assertTrue(entries[0].id.startsWith("urn:handyreader:synthetic:"))
    }

    /** T2：合成 id 确定性——同一条目两次解析 id 相同；不同内容条目 id 不同 */
    @Test
    fun t2_syntheticIdDeterministicAndDistinct() {
        val xml = feed("<entry><title>No Id Book</title></entry>")
        assertEquals(parse(xml)[0].id, parse(xml)[0].id)

        val two = parse(
            feed(
                "<entry><title>Book A</title></entry>" +
                    "<entry><title>Book B</title></entry>"
            )
        )
        assertTrue(two[0].id != two[1].id)
    }

    /** T3：已有 <id> 的条目保持原样，不做合成 */
    @Test
    fun t3_existingIdPreserved() {
        val entries = parse(feed("<entry><id>urn:kavita:book:1</id><title>Book</title></entry>"))
        assertEquals("urn:kavita:book:1", entries[0].id)
    }

    /** T4：Kavita 真实形态——Continue 条目与章节条目同 id=44，策略 B 全量保留两条且顺序不变 */
    @Test
    fun t4_duplicateIdEntriesBothPreserved() {
        val xml = feed(
            "<entry>" +
                "<id>44</id>" +
                "<title>Continue Reading from: Book</title>" +
                "<link rel=\"http://opds-spec.org/acquisition/open-access\" type=\"application/epub+zip\" " +
                "href=\"/api/opds/x/series/1/volume/1/chapter/1/download/a.epub\" " +
                "p5:count=\"8\" xmlns:p5=\"http://vaemendis.net/opds-pse/ns\"/>" +
                "</entry>" +
                "<entry>" +
                "<id>44</id>" +
                "<title>Book</title>" +
                "<link rel=\"http://opds-spec.org/acquisition/open-access\" type=\"application/epub+zip\" " +
                "href=\"/api/opds/x/series/1/volume/1/chapter/1/download/a.epub\" " +
                "p5:count=\"8\" p5:lastRead=\"2\" p5:lastReadDate=\"2026-09-17T04:29:08\" " +
                "xmlns:p5=\"http://vaemendis.net/opds-pse/ns\"/>" +
                "</entry>"
        )
        val entries = parse(xml)
        assertEquals(2, entries.size)
        assertEquals(listOf("44", "44"), entries.map { it.id })
        assertEquals(listOf("Continue Reading from: Book", "Book"), entries.map { it.title })
    }

    /** T7（解析层）：空内容 feed 抛 OpdsParseException，由 ViewModel 映射为 ParseError 而非崩溃 */
    @Test(expected = OpdsParseException::class)
    fun t7_emptyFeedThrowsParseException() {
        parse("")
    }

    // ---------- resolveTemplateUrl / isPrivateHost ----------
    // 方案 docs/plans/2026-09-17-plan-opds-search-url-normalize-and-private-host.md §3

    /** P1：相对模板 + 绝对 base → 绝对模板，{searchTerms} 完整还原 */
    @Test
    fun p1_relativeTemplateResolvedAgainstBase() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://192.168.1.5:5000", "/api/opds/K/series?query={searchTerms}"
        )
        assertEquals("http://192.168.1.5:5000/api/opds/K/series?query={searchTerms}", resolved)
    }

    /** P2：绝对 https 模板原样透传 */
    @Test
    fun p2_absoluteHttpsTemplatePassthrough() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://ignored/opds", "https://a.b/search?query={searchTerms}"
        )
        assertEquals("https://a.b/search?query={searchTerms}", resolved)
    }

    /** P3：公网 http base → 模板升 https（既有升级规则回归锚点） */
    @Test
    fun p3_publicHttpBaseUpgradesTemplateToHttps() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://example.com/opds", "/search?query={searchTerms}"
        )
        assertEquals("https://example.com/search?query={searchTerms}", resolved)
    }

    /** P4：裸主机名 base → 保持 http（Fix B 行为锚点） */
    @Test
    fun p4_dotlessHostBaseStaysHttp() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://mynas:8080/opds", "/search?query={searchTerms}"
        )
        assertEquals("http://mynas:8080/search?query={searchTerms}", resolved)
    }

    /** P5：192.168.x base → 保持 http（回归锚点） */
    @Test
    fun p5_privateIpBaseStaysHttp() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://192.168.1.5:8080/opds", "/search?query={searchTerms}"
        )
        assertEquals("http://192.168.1.5:8080/search?query={searchTerms}", resolved)
    }

    /** P6：base 为空 → 模板原样返回，不抛异常 */
    @Test
    fun p6_blankBaseReturnsTemplateAsIs() {
        val template = "/search?query={searchTerms}"
        assertEquals(template, OpdsFeedParser.resolveTemplateUrl(context, null, template))
        assertEquals(template, OpdsFeedParser.resolveTemplateUrl(context, "", template))
    }

    /** P7：多占位符模板（{searchTerms}+{startIndex}+{count}）还原后均完整保留 */
    @Test
    fun p7_multiPlaceholderTemplateFullyRestored() {
        val resolved = OpdsFeedParser.resolveTemplateUrl(
            context, "http://192.168.1.5:8080/opds",
            "/search?query={searchTerms}&startIndex={startIndex}&count={count}"
        )
        assertEquals(
            "http://192.168.1.5:8080/search?query={searchTerms}&startIndex={startIndex}&count={count}",
            resolved
        )
    }
}
