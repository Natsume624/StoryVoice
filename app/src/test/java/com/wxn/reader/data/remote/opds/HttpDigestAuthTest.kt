package com.wxn.reader.data.remote.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-16-plan-opds-http-digest-auth-support.md §4.7
 * 覆盖：RFC 2617 §3.5 固定向量、挑战解析边界、RFC 2069 分支、quoted-string 转义、
 * uri 编码请求行参与 HA2、MD5 基础正确性。
 */
class HttpDigestAuthTest {

    // calibre 9.11 实测挑战形态
    private val calibreChallenge =
        "Digest realm=\"calibre\", nonce=\"aBcD1234\", algorithm=\"MD5\", qop=\"auth\""

    // ---------- RFC 2617 §3.5 官方向量 ----------

    @Test
    fun `rfc2617 vector - qop list auth,auth-int picks auth and response matches rfc value`() {
        // ⚠️ RFC 2617 §3.5 原文自相矛盾：Authorization 示例 nonce 尾号 093、计算段 094。
        // 本测试采用与 Authorization 示例一致的 093 版（业界通用期望值 6629fae…，
        // r4 已用独立实现复算：若误用 094 版，结果为 3d93b8a77fe22c06695df8c81d3568e2）。
        val challenge = HttpDigestAuth.parseChallenge(
            "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
                "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", " +
                "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        )!!
        val auth = HttpDigestAuth.buildAuthorization(
            challenge, "GET", "/dir/index.html",
            "Mufasa", "Circle Of Life", cnonce = "0a4f113b"
        )
        assertEquals(
            "6629fae49393a05397450978507c4ef1",
            Regex("response=\"([^\"]+)\"").find(auth)!!.groupValues[1]
        )
        assertTrue(auth.contains("username=\"Mufasa\""))
        assertTrue(auth.contains("realm=\"testrealm@host.com\""))
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
        assertTrue(auth.contains("uri=\"/dir/index.html\""))
        assertTrue(auth.contains("algorithm=MD5"))
        assertTrue(auth.contains("qop=auth, nc=00000001, cnonce=\"0a4f113b\""))
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun `rfc2617 vector - intermediate HA1 and HA2 match rfc published values`() {
        assertEquals(
            "939e7578ed9e3c518a452acee763bce9",
            HttpDigestAuth.md5Hex("Mufasa:testrealm@host.com:Circle Of Life")
        )
        assertEquals(
            "39aff3a2bab6126f332b942af96d3366",
            HttpDigestAuth.md5Hex("GET:/dir/index.html")
        )
    }

    // ---------- 挑战解析边界 ----------

    @Test
    fun `parse accepts calibre style challenge`() {
        val challenge = HttpDigestAuth.parseChallenge(calibreChallenge)!!
        assertEquals("calibre", challenge.realm)
        assertEquals("aBcD1234", challenge.nonce)
        assertEquals("auth", challenge.qop)
        assertEquals("MD5", challenge.algorithm)
        assertNull(challenge.opaque)
    }

    @Test
    fun `parse returns null for non digest scheme`() {
        assertNull(HttpDigestAuth.parseChallenge("Basic realm=\"calibre\""))
    }

    @Test
    fun `parse returns null for sha-256 algorithm`() {
        assertNull(
            HttpDigestAuth.parseChallenge(
                "Digest realm=\"r\", nonce=\"n\", algorithm=\"SHA-256\", qop=\"auth\""
            )
        )
    }

    @Test
    fun `parse returns null for qop auth-int only`() {
        assertNull(
            HttpDigestAuth.parseChallenge("Digest realm=\"r\", nonce=\"n\", qop=\"auth-int\"")
        )
    }

    @Test
    fun `parse returns null when nonce missing`() {
        assertNull(HttpDigestAuth.parseChallenge("Digest realm=\"r\""))
    }

    @Test
    fun `parse returns null when realm missing`() {
        assertNull(HttpDigestAuth.parseChallenge("Digest nonce=\"n\""))
    }

    @Test
    fun `parse is case insensitive for scheme and param keys`() {
        val challenge = HttpDigestAuth.parseChallenge("digest REALM=\"r\", NONCE=\"n\", QOP=\"auth\"")!!
        assertEquals("r", challenge.realm)
        assertEquals("n", challenge.nonce)
        assertEquals("auth", challenge.qop)
    }

    @Test
    fun `parse handles unquoted token values`() {
        val challenge = HttpDigestAuth.parseChallenge("Digest realm=\"r\", nonce=n123, algorithm=MD5, qop=auth")!!
        assertEquals("n123", challenge.nonce)
        assertEquals("MD5", challenge.algorithm)
        assertEquals("auth", challenge.qop)
    }

    @Test
    fun `parse handles escaped quotes inside quoted value`() {
        val challenge = HttpDigestAuth.parseChallenge("Digest realm=\"a\\\"b\", nonce=\"n\"")!!
        assertEquals("a\"b", challenge.realm)
    }

    @Test
    fun `parse handles comma inside quoted qop list`() {
        val challenge = HttpDigestAuth.parseChallenge("Digest realm=\"r\", nonce=\"n\", qop=\"auth,auth-int\"")!!
        assertEquals("auth", challenge.qop)
    }

    // ---------- RFC 2069 无 qop 分支 ----------

    @Test
    fun `rfc2069 branch - build without qop omits nc cnonce qop and computes legacy response`() {
        val challenge = HttpDigestAuth.parseChallenge("Digest realm=\"r\", nonce=\"n123\"")!!
        assertNull(challenge.qop)
        val auth = HttpDigestAuth.buildAuthorization(challenge, "GET", "/x", "u", "p", cnonce = "ignored")
        assertFalse(auth.contains("qop="))
        assertFalse(auth.contains("nc="))
        assertFalse(auth.contains("cnonce="))
        val ha1 = HttpDigestAuth.md5Hex("u:r:p")
        val ha2 = HttpDigestAuth.md5Hex("GET:/x")
        val expected = HttpDigestAuth.md5Hex("$ha1:n123:$ha2")
        assertTrue(auth.contains("response=\"$expected\""))
    }

    // ---------- uri 编码请求行参与 HA2 ----------

    @Test
    fun `uri is echoed verbatim and encoded query changes the digest`() {
        val encodedUri = "/opds/search?query=%E4%B8%89%E4%BD%93"
        val encoded = HttpDigestAuth.buildAuthorization(
            HttpDigestAuth.parseChallenge(calibreChallenge)!!,
            "GET", encodedUri, "wxn", "wxn", cnonce = "cn"
        )
        assertTrue(encoded.contains("uri=\"$encodedUri\""))

        val decoded = HttpDigestAuth.buildAuthorization(
            HttpDigestAuth.parseChallenge(calibreChallenge)!!,
            "GET", "/opds/search?query=三体", "wxn", "wxn", cnonce = "cn"
        )
        assertNotEquals(
            Regex("response=\"([^\"]+)\"").find(encoded)!!.groupValues[1],
            Regex("response=\"([^\"]+)\"").find(decoded)!!.groupValues[1]
        )
    }

    // ---------- quoted-string 转义 ----------

    @Test
    fun `username with quote and backslash is escaped`() {
        val auth = HttpDigestAuth.buildAuthorization(
            HttpDigestAuth.parseChallenge(calibreChallenge)!!,
            "GET", "/opds/", "a\"b\\c", "p", cnonce = "cn"
        )
        assertTrue(auth.startsWith("Digest username=\"a\\\"b\\\\c\", "))
    }

    @Test
    fun `server supplied nonce and opaque are escaped`() {
        val auth = HttpDigestAuth.buildAuthorization(
            HttpDigestAuth.parseChallenge("Digest realm=\"r\", nonce=\"n\\\"q\", opaque=\"o\\\"p\"")!!,
            "GET", "/opds/", "u", "p", cnonce = "cn"
        )
        assertTrue(auth.contains("nonce=\"n\\\"q\""))
        assertTrue(auth.contains("opaque=\"o\\\"p\""))
    }

    // ---------- md5Hex 基础正确性 ----------

    @Test
    fun `md5Hex known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HttpDigestAuth.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", HttpDigestAuth.md5Hex("abc"))
        assertEquals("5d41402abc4b2a76b9719d911017c592", HttpDigestAuth.md5Hex("hello"))
        // UTF-8 中文（独立实现复算）
        assertEquals("a7bac2239fcdcb3a067903d8077c4a07", HttpDigestAuth.md5Hex("中文"))
    }
}
