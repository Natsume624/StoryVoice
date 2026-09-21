package com.wxn.bookparser.impl

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 方案 docs/plans/2026-09-17-plan-opds-prc-mobi-open-stuck.md §6.4：
 * 防误删 tripwire——prc 被从集合移除时立即红灯。
 * 分发正确性由验收 A1/A2 端到端覆盖（MobiTextParser 依赖 JNI，无法在 JVM 单测中真实执行）。
 */
class MobiFamilyExtensionsTest {

    @Test
    fun containsFullMobiFamily() {
        assertEquals(setOf("mobi", "azw3", "prc"), MOBI_FAMILY_EXTENSIONS)
    }
}
