package com.wxn.reader.data.remote.opds

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** 随请求传递的 OPDS 凭据（仅内存存在，经 OkHttp request tag 携带）；
 *  host = 签发凭据的目录主机，Authenticator 仅对同主机请求协商（防跨主机重定向凭据外泄） */
data class OpdsRequestCredential(val username: String, val password: String, val host: String)

class OpdsAuthAuthenticator : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 无 tag 请求（非 OPDS）立即旁观，行为与无 Authenticator 时一致
        val credential = response.request.tag(OpdsRequestCredential::class.java) ?: return null
        // 407 为代理认证（需 Proxy-Authorization），与源站认证无关，直接旁观
        if (response.code == 407) return null
        // 凭据主机绑定。tag 随 OkHttp 重定向保留，而 OkHttp 跨主机重定向会剥除 Authorization 头，
        // "已带 Authorization 即放弃"判据在该场景不设防——若不绑定主机，跨主机 401 挑战会把
        // 用户凭据发给第三方主机。此处强制只对签发凭据的主机协商。
        if (!response.request.url.host.equals(credential.host, ignoreCase = true)) return null
        // 已携带 Authorization 仍 401 = 凭据被拒，放弃（防密码错误时无限环）。
        // 不可用 priorResponse 链计数判据——重定向（3xx）后收到 401 时链已非空，
        // 会被误判为"已重试"而放弃协商；本判据只在确实尝试过认证时阻断。
        if (response.request.header("Authorization") != null) return null
        val challengeHeader = response.header("WWW-Authenticate") ?: return null
        return buildAuthorizedRequest(response.request, credential, challengeHeader)
    }

    companion object {
        /** 纯函数，便于 JVM 单测：按挑战 scheme 生成携带 Authorization 的新请求 */
        fun buildAuthorizedRequest(
            request: Request,
            credential: OpdsRequestCredential,
            challengeHeader: String
        ): Request? {
            val scheme = challengeHeader.substringBefore(' ').trim()
            return when {
                scheme.equals("Basic", ignoreCase = true) -> {
                    // okhttp3.Credentials 纯 JVM 实现，显式 UTF-8
                    request.newBuilder()
                        .header("Authorization", Credentials.basic(credential.username, credential.password, Charsets.UTF_8))
                        .build()
                }
                scheme.equals("Digest", ignoreCase = true) -> {
                    val challenge = HttpDigestAuth.parseChallenge(challengeHeader) ?: return null
                    // uri 必须与服务器收到的请求行一致——OkHttp 发送的是编码后的 path+query，
                    // 必须用 encodedQuery 而非解码 query（搜索词含空格/CJK 时解码值会导致摘要与请求行不匹配，校验必败）
                    val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                    val value = HttpDigestAuth.buildAuthorization(
                        challenge = challenge,
                        method = request.method,
                        uri = uri,
                        username = credential.username,
                        password = credential.password
                    )
                    request.newBuilder().header("Authorization", value).build()
                }
                else -> null   // 未知 scheme（如 Bearer/NTLM）→ 放弃，走错误链路
            }
        }
    }
}
