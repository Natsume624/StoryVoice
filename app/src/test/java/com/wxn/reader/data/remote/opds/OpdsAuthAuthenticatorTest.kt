package com.wxn.reader.data.remote.opds

import okhttp3.Credentials
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-16-plan-opds-http-digest-auth-support.md §4.7
 * Authenticator 七分支：无 tag / 407 / 跨主机 / 已带 Authorization / Basic / Digest / 未知 scheme，
 * 另覆盖 Digest uri 使用编码请求行。
 */
class OpdsAuthAuthenticatorTest {

    private val authenticator = OpdsAuthAuthenticator()

    private val calibreDigestChallenge =
        "Digest realm=\"calibre\", nonce=\"aBcD1234\", algorithm=\"MD5\", qop=\"auth\", opaque=\"OPQ\""

    private fun request(
        url: String = "https://calibre.example/opds/",
        credential: OpdsRequestCredential? = null,
        authorization: String? = null
    ): Request {
        val builder = Request.Builder().url(url)
        authorization?.let { builder.header("Authorization", it) }
        credential?.let { builder.tag(OpdsRequestCredential::class.java, it) }
        return builder.build()
    }

    private fun response(request: Request, code: Int = 401, challenge: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 401) "Unauthorized" else "Proxy Authentication Required")
        challenge?.let { builder.header("WWW-Authenticate", it) }
        return builder.build()
    }

    private fun credentialFor(host: String = "calibre.example") =
        OpdsRequestCredential("wxn", "wxn", host = host)

    // ---------- 1) 无 tag 请求 → 旁观 ----------

    @Test
    fun `no tag request returns null`() {
        val result = authenticator.authenticate(null, response(request(credential = null), challenge = calibreDigestChallenge))
        assertNull(result)
    }

    // ---------- 2) Basic 挑战 ----------

    @Test
    fun `basic challenge produces basic authorization header`() {
        val req = request(credential = credentialFor())
        val result = authenticator.authenticate(null, response(req, challenge = "Basic realm=\"calibre\""))!!
        assertEquals(
            Credentials.basic("wxn", "wxn", Charsets.UTF_8),
            result.header("Authorization")
        )
    }

    // ---------- 3) Digest 挑战（calibre 实测形态） ----------

    @Test
    fun `digest challenge produces digest authorization with nc cnonce response and opaque`() {
        val req = request(credential = credentialFor())
        val result = authenticator.authenticate(null, response(req, challenge = calibreDigestChallenge))!!
        val auth = result.header("Authorization")!!
        assertTrue(auth.startsWith("Digest "))
        assertTrue(auth.contains("username=\"wxn\""))
        assertTrue(auth.contains("realm=\"calibre\""))
        assertTrue(auth.contains("nonce=\"aBcD1234\""))
        assertTrue(auth.contains("uri=\"/opds/\""))
        assertTrue(auth.contains("qop=auth, nc=00000001, cnonce=\""))
        assertTrue(Regex("response=\"[0-9a-f]{32}\"").containsMatchIn(auth))
        assertTrue(auth.contains("opaque=\"OPQ\""))
    }

    // ---------- 4) 未知 scheme → 放弃 ----------

    @Test
    fun `unknown scheme bearer returns null`() {
        val req = request(credential = credentialFor())
        val result = authenticator.authenticate(null, response(req, challenge = "Bearer realm=\"x\""))
        assertNull(result)
    }

    // ---------- 5) 已带 Authorization 仍 401 → 放弃（防环） ----------

    @Test
    fun `request already carrying authorization returns null`() {
        val req = request(
            credential = credentialFor(),
            authorization = "Digest username=\"wxn\", response=\"deadbeef\""
        )
        val result = authenticator.authenticate(null, response(req, challenge = calibreDigestChallenge))
        assertNull(result)
    }

    // ---------- 6) 跨主机 → 旁观（凭据主机绑定） ----------

    @Test
    fun `cross host request does not leak credentials`() {
        val req = request(
            url = "https://evil.example/redirected",
            credential = credentialFor(host = "calibre.example")
        )
        val result = authenticator.authenticate(null, response(req, challenge = calibreDigestChallenge))
        assertNull(result)
    }

    @Test
    fun `host comparison is case insensitive`() {
        val req = request(
            url = "https://CALIBRE.EXAMPLE/opds/",
            credential = credentialFor(host = "calibre.example")
        )
        val result = authenticator.authenticate(null, response(req, challenge = calibreDigestChallenge))
        assertTrue(result != null)
    }

    // ---------- 7) 407 代理认证 → 旁观 ----------

    @Test
    fun `proxy 407 challenge returns null`() {
        val req = request(credential = credentialFor())
        val result = authenticator.authenticate(
            null,
            response(req, code = 407, challenge = "Basic realm=\"proxy\"")
        )
        assertNull(result)
    }

    // ---------- Digest uri 使用编码请求行 ----------

    @Test
    fun `digest uri uses encoded path and query`() {
        val req = request(
            url = "https://calibre.example/opds/search?query=%E4%B8%89%E4%BD%93&sort=title",
            credential = credentialFor()
        )
        val result = authenticator.authenticate(null, response(req, challenge = calibreDigestChallenge))!!
        val auth = result.header("Authorization")!!
        assertTrue(auth.contains("uri=\"/opds/search?query=%E4%B8%89%E4%BD%93&sort=title\""))
    }

    // ---------- 不可解析的 Digest 挑战 → 放弃 ----------

    @Test
    fun `unparseable digest challenge returns null`() {
        val req = request(credential = credentialFor())
        val result = authenticator.authenticate(null, response(req, challenge = "Digest realm=\"calibre\""))
        assertNull(result)
    }
}
