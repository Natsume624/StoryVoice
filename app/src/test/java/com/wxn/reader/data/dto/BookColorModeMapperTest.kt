package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.BookColorMode
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.preference.ReaderThemePreset
import com.wxn.bookread.data.model.preference.applyTo
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AS-1 P2「书籍字体颜色」三态开关完全主题化（方案 §3.7）JVM 单测。
 *
 * 覆盖 bookColorMode 在主题数据链路上的全部转换点：
 * - prefs → 存档实体（[toReaderThemeConfigEntity]，枚举 name 字符串）
 * - 存档实体 → prefs（[toReaderThemeConfigEntity.toReaderPreferences]，损坏值回退 current）
 * - 预设 applyTo / 预设 → 存档实体（首次切无存档主题、重置回预设默认）
 * - per-book 快照双向（[toPerBookSnapshot]/[PerBookThemeOverrideEntity.toReaderPreferences]）
 * - [differsFrom] 纳入 bookColorMode 比对（改开关 = 主题已微调，* 号亮）
 *
 * 使用 Robolectric：[ReaderPreferences] 依赖 `android.graphics.Color`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookColorModeMapperTest {

    private fun preset(mode: BookColorMode = BookColorMode.SMART) = ReaderThemePreset(
        themeId = "default",
        backgroundColor = 0xFFF5EFDC.toInt(),
        textColor = 0xFF333333.toInt(),
        font = "serif",
        fontSize = 1.1,
        lineHeight = 1.6,
        paragraphSpacing = 0.7,
        pageHorizontalMargins = 1.4,
        pageVerticalMargins = 1.1,
        bookColorMode = mode,
    )

    private fun prefs(mode: BookColorMode): ReaderPreferences =
        ReaderPreferencesUtil.defaultPreferences.copy(bookColorMode = mode)

    // ════════════════════════════════════════════════════════════════════
    // prefs → 存档实体（saveCurrent 写列）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `prefs to archive entity writes enum name`() {
        val entity = prefs(BookColorMode.BOOK).toReaderThemeConfigEntity("default")
        assertEquals("BOOK", entity.bookColorMode)
    }

    // ════════════════════════════════════════════════════════════════════
    // 存档实体 → prefs（switchTheme loadTarget 恢复）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `archive restores mode on theme switch`() {
        val archive = prefs(BookColorMode.THEME).toReaderThemeConfigEntity("default")
        val restored = archive.toReaderPreferences(prefs(BookColorMode.SMART))
        assertEquals(BookColorMode.THEME, restored.bookColorMode)
    }

    @Test
    fun `archive corrupted mode falls back to current not default`() {
        val archive = prefs(BookColorMode.THEME).toReaderThemeConfigEntity("default")
            .copy(bookColorMode = "XX")
        val current = prefs(BookColorMode.BOOK)
        val restored = archive.toReaderPreferences(current)
        assertEquals("损坏值应回退 current 值（不静默翻转策略）", BookColorMode.BOOK, restored.bookColorMode)
    }

    // ════════════════════════════════════════════════════════════════════
    // 预设：applyTo（首次切无存档主题/主题重置）与预设 → 存档实体
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `applyTo preset mode overrides current`() {
        val applied = preset(BookColorMode.SMART).applyTo(prefs(BookColorMode.THEME))
        assertEquals(BookColorMode.SMART, applied.bookColorMode)
    }

    @Test
    fun `preset to archive entity carries mode name`() {
        val entity = preset(BookColorMode.BOOK).toReaderThemeConfigEntity()
        assertEquals("BOOK", entity.bookColorMode)
    }

    // ════════════════════════════════════════════════════════════════════
    // per-book 快照双向（freeze/saveSnapshot + loadSnapshot）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `per-book snapshot round-trips mode`() {
        val snapshot = prefs(BookColorMode.THEME).toPerBookSnapshot(1L, "night")
        assertEquals("THEME", snapshot.bookColorMode)
        val restored = snapshot.toReaderPreferences(prefs(BookColorMode.BOOK))
        assertEquals("快照恢复应带回快照值而非全局值", BookColorMode.THEME, restored.bookColorMode)
    }

    @Test
    fun `per-book snapshot corrupted mode falls back to current`() {
        val snapshot = prefs(BookColorMode.THEME).toPerBookSnapshot(1L, "night")
            .copy(bookColorMode = "GARBAGE")
        val restored = snapshot.toReaderPreferences(prefs(BookColorMode.BOOK))
        assertEquals(BookColorMode.BOOK, restored.bookColorMode)
    }

    // ════════════════════════════════════════════════════════════════════
    // differsFrom 纳入 bookColorMode（RK-13：改开关 * 号亮）
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `differsFrom only bookColorMode differs is true`() {
        val entity = preset(BookColorMode.THEME).toReaderThemeConfigEntity()
        assertTrue("仅开关不同也应判已微调", entity.differsFrom(preset(BookColorMode.SMART)))
    }

    @Test
    fun `differsFrom identical including mode is false`() {
        val entity = preset(BookColorMode.SMART).toReaderThemeConfigEntity()
        assertFalse(entity.differsFrom(preset(BookColorMode.SMART)))
    }
}
