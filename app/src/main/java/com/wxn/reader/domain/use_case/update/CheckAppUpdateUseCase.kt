package com.wxn.reader.domain.use_case.update

import android.os.Build
import com.wxn.reader.BuildConfig
import com.wxn.reader.data.remote.api.ApiBaseException
import com.wxn.reader.data.remote.api.ApiCode
import com.wxn.reader.data.remote.api.AppUpdateApi
import com.wxn.reader.util.DeviceAbi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 版本更新检查：组装 source/arch/versionCode → 请求 → 版本比较 → 渠道分流结果。
 * 版本比较在客户端完成（服务端仅按 source 记录与返回配置）。
 */
class CheckAppUpdateUseCase @Inject constructor(
    private val api: AppUpdateApi,
    private val isPlayChannel: Boolean,   // NetworkModule.provideIsPlayChannel
) {

    sealed class UpdateResult {
        /** 有更新。resolvedNotes 为按 D12 选择链选出的文案；null = 无匹配且无 default → UI 隐藏日志区 */
        data class Available(
            val latestVersionCode: Int,
            val latestVersionName: String,
            val resolvedNotes: String?,
            val forceUpdate: Boolean,      // v1 仅透传展示，不拦截
            val downloadUrl: String?,      // 非 Play：按设备 ABI 选出的直链；Play：恒 null
        ) : UpdateResult()

        data object UpToDate : UpdateResult()
        data object UnsupportedAbi : UpdateResult()   // x86/x86_64 纯模拟器
        data class Error(val isNetwork: Boolean) : UpdateResult()  // UI 映射两种文案
    }

    suspend operator fun invoke(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        appLanguage: String,              // AppPreferencesUtil.language，如 "en"/"zh"/"zh-TW"
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS,  // 仅供单测注入；生产走默认值
    ): UpdateResult {
        // 非 ARM 设备不发起请求
        if (!DeviceAbi.isArmSupported(supportedAbis)) return UpdateResult.UnsupportedAbi

        val source = if (isPlayChannel) "android-play" else "android-general"
        val response = try {
            api.checkUpdate(source, DeviceAbi.preferredAbi(supportedAbis), currentVersionCode)
        } catch (e: CancellationException) {
            throw e
        }

        val envelope = response.getOrNull()
        if (envelope == null) {
            val e = response.exceptionOrNull()
            val code = (e as? ApiBaseException)?.code
            // 超时/断网类 → 网络文案；HTTP 错误/解析失败等 → 服务文案
            val isNetwork = code == ApiCode.CODE_TIME_OUT ||
                    code == ApiCode.CODE_SERV_UNKOWN ||
                    code == ApiCode.CODE_NETWORK_ERROR
            return UpdateResult.Error(isNetwork)
        }

        val info = envelope.data ?: return UpdateResult.UpToDate   // data:null = 来源未配置
        if (info.latestVersionCode <= currentVersionCode) return UpdateResult.UpToDate

        // D12 选择链：当前语言精确匹配（zh-TW 独立键、绝不回退简体 zh）→ default → null
        val notes = info.releaseNotes[appLanguage]?.takeIf { it.isNotBlank() }
            ?: info.releaseNotes["default"]?.takeIf { it.isNotBlank() }

        if (isPlayChannel) {
            return UpdateResult.Available(
                latestVersionCode = info.latestVersionCode,
                latestVersionName = info.latestVersionName,
                resolvedNotes = notes,
                forceUpdate = info.forceUpdate,
                downloadUrl = null,
            )
        }
        val url = info.downloadUrls[DeviceAbi.preferredAbi(supportedAbis)]
            ?: return UpdateResult.Error(isNetwork = false)   // 防呆：服务端缺 ABI 直链，不静默
        return UpdateResult.Available(
            latestVersionCode = info.latestVersionCode,
            latestVersionName = info.latestVersionName,
            resolvedNotes = notes,
            forceUpdate = info.forceUpdate,
            downloadUrl = url,
        )
    }
}
