package com.wxn.reader.data.remote.opds

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * HTTP Digest 认证（RFC 2617/7616）挑战解析与 Authorization 头值生成。
 * 仅支持 algorithm=MD5、qop=auth（含 RFC 2069 无 qop 兼容分支）；
 * 其他形态解析返回 null，由调用方放弃协商走错误链路。
 * 纯 JVM 实现，可单元测试。
 */
object HttpDigestAuth {

    data class Challenge(
        val realm: String,
        val nonce: String,
        val opaque: String?,      // 挑战携带则必须原样回传
        val qop: String?,         // 规范化为 "auth"；null = RFC 2069 分支
        val algorithm: String     // 恒为 "MD5"（非 MD5 在 parse 阶段即拒绝）
    )

    /** 解析 WWW-Authenticate 挑战；非 Digest / 非 MD5 / 缺 realm|nonce 返回 null */
    fun parseChallenge(header: String): Challenge? {
        if (!header.substringBefore(' ').trim().equals("Digest", ignoreCase = true)) return null
        val params = parseAuthParams(header.substringAfter(' ', "").trim())
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val algorithm = params["algorithm"]?.uppercase() ?: "MD5"
        if (algorithm != "MD5") return null
        val qopRaw = params["qop"]?.lowercase()
        val qop = when {
            qopRaw == null -> null                                   // RFC 2069
            qopRaw.split(',').map { it.trim() }.contains("auth") -> "auth"  // auth-int 不支持
            else -> return null
        }
        return Challenge(realm = realm, nonce = nonce, opaque = params["opaque"], qop = qop, algorithm = algorithm)
    }

    /** 生成 Digest Authorization 头值。uri 必须与请求行一致（encodedPath + "?query"） */
    fun buildAuthorization(
        challenge: Challenge,
        method: String,
        uri: String,
        username: String,
        password: String,
        cnonce: String = newCnonce(),
        nc: String = NC
    ): String {
        val ha1 = md5Hex("$username:${challenge.realm}:$password")
        val ha2 = md5Hex("$method:$uri")
        val response = if (challenge.qop != null) {
            md5Hex("$ha1:${challenge.nonce}:$nc:$cnonce:${challenge.qop}:$ha2")
        } else {
            md5Hex("$ha1:${challenge.nonce}:$ha2")
        }
        val user = escapeQuotedString(username)
        return buildString {
            append("Digest username=\"$user\", realm=\"${escapeQuotedString(challenge.realm)}\"")
            append(", nonce=\"${escapeQuotedString(challenge.nonce)}\", uri=\"$uri\", algorithm=${challenge.algorithm}")
            if (challenge.qop != null) append(", qop=${challenge.qop}, nc=$nc, cnonce=\"$cnonce\"")
            append(", response=\"$response\"")
            challenge.opaque?.let { append(", opaque=\"${escapeQuotedString(it)}\"") }
        }
    }

    fun newCnonce(): String =
        ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** RFC 2617 quoted-string 转义：反斜杠与双引号 */
    internal fun escapeQuotedString(s: String): String =
        buildString { s.forEach { c -> if (c == '\\' || c == '"') append('\\'); append(c) } }

    internal fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /**
     * 解析 auth-param 列表："k1="v1", k2=v2"（支持带引号值与 \" 转义、无引号 token）。
     * 状态机逐段推进：找 '=' → 按引号/裸 token 取值 → 跳 ',' → 循环。
     */
    internal fun parseAuthParams(src: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        var i = 0
        while (i < src.length) {
            while (i < src.length && (src[i] == ' ' || src[i] == ',')) i++
            val eq = src.indexOf('=', i)
            if (eq < 0) break
            val key = src.substring(i, eq).trim().lowercase()
            var j = eq + 1
            while (j < src.length && src[j] == ' ') j++
            val value: String
            if (j < src.length && src[j] == '"') {
                j++
                val sb = StringBuilder()
                while (j < src.length && src[j] != '"') {
                    if (src[j] == '\\' && j + 1 < src.length) j++   // 转义下一个字符
                    sb.append(src[j]); j++
                }
                value = sb.toString()
                j++ // 跳过收尾引号
            } else {
                val end = src.indexOf(',', j).let { if (it < 0) src.length else it }
                value = src.substring(j, end).trim()
                j = end
            }
            params[key] = value
            val comma = src.indexOf(',', j)
            i = if (comma < 0) src.length else comma + 1
        }
        return params
    }

    private const val NC = "00000001"
}
