package com.wxn.reader.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration_11_12 instrumentation 测试（AS-1 P2 完全主题化方案 §3.7.2/§3.7.4，DB v12）。
 *
 * 前史：本文件原为 TXT 统一字节偏移方案的 11→12 测试，该方案实施时已把 txtCharset
 * 折叠进 `Migration_10_11`（当前 DB version=11），旧引用悬空、androidTest 源集不可编译。
 * 本次按方案 §3.7.4 第 2 条**重写**为 bookColorMode 迁移校验（顺带修复既有编译损坏）。
 *
 * **覆盖**：
 * 1. schema 一致性（[runMigrationsAndValidate] 自动比对 12.json：列名/类型/DEFAULT/FK/索引/PK 全核对）
 * 2. `reader_theme_configs.bookColorMode` 与 `per_book_theme_overrides.bookColorMode` 列存在，
 *    存量行迁移后为 'SMART'（TEXT NOT NULL DEFAULT 'SMART'）
 * 3. v11 存量数据（主题存档行 + per-book 快照行）完整保留（迁移不应擦除已有数据）
 * 4. 迁移后 bookColorMode 可写（updateBookColorMode/saveSnapshot 写列路径依赖）
 *
 * 说明：txtCharset 的存在性由 `createDatabase(name, 11)` 起建隐式覆盖（11.json 已含该列），
 * 其行为断言由既有 [Migration10To11Test] 承担，本文件不重复。
 *
 * 运行：`gradlew.bat :app:connectedDebugAndroidTest --tests "*Migration11To12Test*"`（需真机/模拟器）
 */
@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    private val dbName = "migration-11-12-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        openFactory = FrameworkSQLiteOpenHelperFactory(),
        specs = emptyList()
    )

    @Test
    fun migrate11To12_schemaConsistent_andLegacyRowsPreserved() {
        // 1. 建 v11 库（按 11.json 自动建表），插入存量数据：
        //    books 1 行（FK 依赖）+ reader_theme_configs 1 行 + per_book_theme_overrides 1 行
        //    v11 双主题表均无 bookColorMode 列（迁移前）
        helper.createDatabase(dbName, 11).apply {
            execSQL(
                """INSERT INTO books (id, uri, fileType, title, authors, wordCount, locator,
                       progression, deleted, rating, isFavorite, readingTime, scrollIndex, scrollOffset,
                       cachedDir, crc, importStatus, source, metaHlcL, metaHlcC, metaHlcDevice,
                       userHlcL, userHlcC, userHlcDevice, syncHlcL, syncHlcC, syncHlcDevice)
                   VALUES (1, 'uri', 'EPUB', 'test_book', '', 0, '', 0.0, 0, 0.0, 0, 0, 0, 0,
                       '', 0, 0, '', 0, 0, '', 0, 0, '', 0, 0, '')"""
            )
            execSQL(
                """INSERT INTO reader_theme_configs (
                       themeId, backgroundColor, textColor, backgroundImage, font, fontVariant,
                       fontSize, lineHeight, letterSpacing, paragraphIndent, paragraphSpacing,
                       pageHorizontalMargins, pageVerticalMargins, titleSize, titleTopSpacing,
                       titleBottomSpacing, updatedAt)
                   VALUES ('default', -328969, -13882324, '', 'sans_serif', 'regular',
                       1.0, 1.5, 0.0, 2.0, 0.6,
                       1.5, 1.2, 1.0, 18.0,
                       15.0, 1700000000000)"""
            )
            execSQL(
                """INSERT INTO per_book_theme_overrides (
                       bookId, themeId, fontSize, lineHeight, letterSpacing,
                       pageHorizontalMargins, pageVerticalMargins, paragraphIndent, paragraphSpacing,
                       textColor, backgroundColor, backgroundImage, font, fontVariant,
                       titleSize, titleTopSpacing, titleBottomSpacing, createdAt, updatedAt)
                   VALUES (1, 'default', 1.2, 1.6, 0.0,
                       1.5, 1.2, 2.0, 0.6,
                       -1, -1, '', 'serif', 'regular',
                       1.0, 18.0, 15.0, 1700000000000, 1700000000001)"""
            )
            close()
        }

        // 2. 执行迁移 11→12 + 自动比对 12.json
        //    runMigrationsAndValidate 核对迁移后 schema 与 12.json 完全一致：
        //    列名/类型/DEFAULT/FK/索引/PK 任何不一致都会抛 IllegalStateException
        val db = helper.runMigrationsAndValidate(
            dbName, 12, true,
            AppDatabase.Migration_11_12
        )

        // 3. reader_theme_configs：存量行数据完整保留 + 新列默认 'SMART'
        db.query(
            """SELECT backgroundColor, textColor, fontSize, lineHeight, bookColorMode
               FROM reader_theme_configs WHERE themeId = 'default'"""
        ).use {
            assertTrue("v11 主题存档行应保留", it.moveToFirst())
            assertEquals("backgroundColor 应保留", -328969, it.getInt(0))
            assertEquals("textColor 应保留", -13882324, it.getInt(1))
            assertEquals("fontSize 应保留", 1.0, it.getDouble(2), 0.0001)
            assertEquals("lineHeight 应保留", 1.5, it.getDouble(3), 0.0001)
            assertEquals("存量行 bookColorMode 应为 DEFAULT 'SMART'", "SMART", it.getString(4))
        }

        // 4. per_book_theme_overrides：存量行数据完整保留 + 新列默认 'SMART'
        db.query(
            """SELECT font, fontSize, bookColorMode
               FROM per_book_theme_overrides WHERE bookId = 1 AND themeId = 'default'"""
        ).use {
            assertTrue("v11 per-book 快照行应保留", it.moveToFirst())
            assertEquals("font 应保留", "serif", it.getString(0))
            assertEquals("fontSize 应保留", 1.2, it.getDouble(1), 0.0001)
            assertEquals("存量行 bookColorMode 应为 DEFAULT 'SMART'", "SMART", it.getString(2))
        }

        // 5. 验证 bookColorMode 列可写（updateBookColorMode / saveSnapshot 写列路径依赖）
        db.execSQL("UPDATE reader_theme_configs SET bookColorMode = 'BOOK' WHERE themeId = 'default'")
        db.execSQL("UPDATE per_book_theme_overrides SET bookColorMode = 'THEME' WHERE bookId = 1")
        db.query("SELECT bookColorMode FROM reader_theme_configs WHERE themeId = 'default'").use {
            assertTrue(it.moveToFirst())
            assertEquals("BOOK", it.getString(0))
        }
        db.query("SELECT bookColorMode FROM per_book_theme_overrides WHERE bookId = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("THEME", it.getString(0))
        }

        db.close()
    }
}
