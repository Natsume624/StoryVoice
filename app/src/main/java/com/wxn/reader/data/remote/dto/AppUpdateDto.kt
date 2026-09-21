package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 版本更新信息（/api/v1/app-update 响应的 data 字段）。
 * 响应信封复用 [BaseResponse]（success/code/message/data），本文件只定义 data 结构。
 */
@Serializable
data class AppUpdateInfo(
    @SerialName("latestVersionCode") val latestVersionCode: Int,
    @SerialName("latestVersionName") val latestVersionName: String,
    /** 轻量多语言更新日志："default" 必有；可选键为 BCP-47 标签，zh-TW 为独立键 */
    @SerialName("releaseNotes") val releaseNotes: Map<String, String> = emptyMap(),
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
    /** Play 来源不含此字段 → 缺字段 + 默认值，kotlinx 解析天然兼容 */
    @SerialName("downloadUrls") val downloadUrls: Map<String, String> = emptyMap(),
)
