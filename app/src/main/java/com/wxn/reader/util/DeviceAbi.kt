package com.wxn.reader.util

import android.os.Build

/**
 * 设备 ABI 判定：依据系统报告的硬件能力（Build.SUPPORTED_ABIS），
 * 而非当前安装包/进程的 ABI——用户可能在 64 位设备上装着 v7a 旧包，
 * 更新时应推荐 arm64 包升级（与 Google Play 商店选包口径一致）。
 *
 * abis 参数仅供单测注入；生产调用一律走默认值。
 */
object DeviceAbi {

    const val ARM64 = "arm64-v8a"
    const val ARM32 = "armeabi-v7a"

    /** 设备首选 ARM ABI：具备 64 位能力时一律返回 arm64-v8a（不依赖数组顺序），否则 armeabi-v7a。 */
    fun preferredAbi(abis: Array<String> = Build.SUPPORTED_ABIS): String =
        if (abis.any { it == ARM64 }) ARM64 else ARM32

    /** 设备是否具备 ARM 执行能力（x86/x86_64 纯模拟器为 false，走"不支持"提示）。 */
    fun isArmSupported(abis: Array<String> = Build.SUPPORTED_ABIS): Boolean =
        abis.any { it == ARM64 || it == ARM32 }
}
