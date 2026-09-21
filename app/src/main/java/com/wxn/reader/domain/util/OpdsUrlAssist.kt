package com.wxn.reader.domain.util

import java.net.URI
import java.net.URISyntaxException

/**
 * OPDS 目录网址输入辅助：协议选择 + 域名/路径输入的拆解、组装与校验。
 *
 * 纯 JVM 实现（java.net.URI），不依赖 Android 框架，保证可单元测试。
 * host 与路径判定一律以 URI 解析结果为准，禁止对 hostPath 做裸字符串切分
 * （端口冒号、IPv6 字面量等边界无法用字符串切分正确处理）。
 */
object OpdsUrlAssist {

    const val SCHEME_HTTPS = "https"
    const val SCHEME_HTTP = "http"

    /** 无显式路径时自动补全的默认路径（多数自建 OPDS 服务的约定路径） */
    const val DEFAULT_PATH = "/opds"

    data class Parsed(
        /** 用户粘贴出的协议（仅 http/https）；输入不含合法协议头时为 null */
        val scheme: String?,
        /** 清洗后的 域名[:端口][/路径][?查询] */
        val hostPath: String,
        /** 输入是否可能合法（非法协议、仅协议头、空输入为 false） */
        val valid: Boolean
    )

    data class Composed(
        val url: String,
        /** 本次组装是否发生了 /opds 自动补全 */
        val pathAutoCompleted: Boolean
    )

    /** 拆解用户原始输入：清洗空白与零宽字符；识别并剥离粘贴的协议头 */
    fun parse(raw: String): Parsed {
        val cleaned = clean(raw)
        val separator = cleaned.indexOf("://")
        if (separator < 0) {
            return Parsed(scheme = null, hostPath = cleaned, valid = cleaned.isNotEmpty())
        }
        val candidate = cleaned.substring(0, separator).lowercase()
        val rest = cleaned.substring(separator + 3)
        val validScheme = candidate == SCHEME_HTTPS || candidate == SCHEME_HTTP
        // 非法协议（ftp:// 等）与仅协议头（https:// 后无 host）均判非法；
        // 非法协议的原文保留在 hostPath 之外，由调用方决定是否原样回显
        return Parsed(
            scheme = if (validScheme) candidate else null,
            hostPath = rest,
            valid = validScheme && rest.isNotEmpty()
        )
    }

    /**
     * 组装完整 URL；无显式路径时自动补 [DEFAULT_PATH]。
     * host 非法（空、含空白、无法解析、非域名/IP/localhost）时返回 null。
     */
    fun compose(scheme: String, raw: String): Composed? {
        val hostPath = parse(raw).hostPath
        if (hostPath.isEmpty()) return null
        val candidate = "${scheme.lowercase()}://$hostPath"
        val uri = try {
            URI(candidate)
        } catch (e: URISyntaxException) {
            return null
        }
        val host = uri.host ?: return null
        if (!isAcceptableHost(host)) return null
        // 根路径 "/" 视为无路径（用户输 "host/" 等价于只输 host）
        val hasPath = (!uri.path.isNullOrEmpty() && uri.path != "/") || !uri.rawQuery.isNullOrEmpty()
        return if (hasPath) {
            Composed(url = candidate, pathAutoCompleted = false)
        } else {
            Composed(url = candidate.trimEnd('/') + DEFAULT_PATH, pathAutoCompleted = true)
        }
    }

    /** 完整 URL 宽松校验：scheme ∈ {http, https} 且 host 为域名/localhost/IP 字面量 */
    fun isValid(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != SCHEME_HTTPS && scheme != SCHEME_HTTP) return false
        val host = uri.host ?: return false
        return isAcceptableHost(host)
    }

    /** 相邻重复路径段：/seg/seg 形态（seg 内不含 / ? #），第二段须以 / 或串尾收尾 */
    private val DUPLICATED_SEGMENT = Regex("""/([^/?#]+)/\1(?=/|$)""")

    /**
     * 折叠 URL 路径中第一处连续重复的路径段（仅一处、不迭代、大小写敏感）。
     * 用于兼容服务端畸形相对链接经 RFC 合并后产生的段重复，如：
     * /api/opds/{key}/{key}/recently-updated?p=2 → /api/opds/{key}/recently-updated?p=2
     * 无重复段时原样返回；query/fragment 原样保留。
     */
    fun collapseDuplicatedPathSegment(url: String): String {
        val cut = url.indexOfFirst { it == '?' || it == '#' }
        val head = if (cut < 0) url else url.substring(0, cut)
        val tail = if (cut < 0) "" else url.substring(cut)
        val match = DUPLICATED_SEGMENT.find(head) ?: return url
        val secondSegmentStart = match.range.first + 1 + match.groupValues[1].length
        return head.removeRange(secondSegmentStart, match.range.last + 1) + tail
    }

    private fun isAcceptableHost(host: String): Boolean {
        if (host.isEmpty() || host.contains(' ')) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        // IPv6 字面量（URI.host 含方括号）
        if (host.startsWith("[")) return true
        // 端口已被 URI 剥离到 port 字段，host 内不应再有冒号
        if (host.contains(':')) return false
        return host.contains('.')
    }

    private fun clean(raw: String): String =
        raw.filter { it !in ZERO_WIDTH_CHARS }.trim()

    private val ZERO_WIDTH_CHARS = charArrayOf('\u200B', '\u200C', '\u200D', '\uFEFF')
}
