package com.wxn.reader.presentation.mainReader.autoread

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FAB 落位/脱附/坐标解析纯函数用例（第二轮 P2 + 第三轮 P6，方案 §3.2/T4）：
 * 释放吸附判定（decideDock）/ 点按脱附（undockInwardX、undockTapTarget）/
 * 自由态坐标解析（resolveClampedX/Y——渲染、拖拽、点按三路径共用的功能语义）。
 *
 * px 取值按 3x 密度示例：56dp=168px、28dp=84px、24dp=72px、16dp=48px。
 */
class FabDockPolicyTest {

    // ===== 第二轮 P2：decideDock 释放吸附判定 =====

    @Test
    fun `中心靠近左缘吸附左侧`() {
        // 屏宽 1080，阈值 64：中心 30 距左缘 <64 → 吸左
        assertEquals(FabDockPolicy.SIDE_LEFT, FabDockPolicy.decideDock(30f, 1080f, 64f))
    }

    @Test
    fun `中心靠近右缘吸附右侧`() {
        // 中心 1080-30=1050 距右缘 <64 → 吸右
        assertEquals(FabDockPolicy.SIDE_RIGHT, FabDockPolicy.decideDock(1050f, 1080f, 64f))
    }

    @Test
    fun `中心在中间区域保持自由`() {
        assertEquals(FabDockPolicy.SIDE_NONE, FabDockPolicy.decideDock(540f, 1080f, 64f))
    }

    @Test
    fun `阈值边界_恰在阈值上自由_越界一点即吸附`() {
        // 中心恰 = threshold(64) → 自由；略小于 → 吸左
        assertEquals(FabDockPolicy.SIDE_NONE, FabDockPolicy.decideDock(64f, 1080f, 64f))
        assertEquals(FabDockPolicy.SIDE_LEFT, FabDockPolicy.decideDock(63.9f, 1080f, 64f))
        // 中心恰 = parentW - threshold(1080-64=1016) → 自由；略大于 → 吸右
        assertEquals(FabDockPolicy.SIDE_NONE, FabDockPolicy.decideDock(1016f, 1080f, 64f))
        assertEquals(FabDockPolicy.SIDE_RIGHT, FabDockPolicy.decideDock(1016.1f, 1080f, 64f))
    }

    @Test
    fun `父宽非法一律自由`() {
        assertEquals(FabDockPolicy.SIDE_NONE, FabDockPolicy.decideDock(0f, 0f, 64f))
        assertEquals(FabDockPolicy.SIDE_NONE, FabDockPolicy.decideDock(0f, -10f, 64f))
    }

    // ===== 第三轮 P6：undockInwardX 点按脱附目标 x =====

    @Test
    fun `点按脱附x_右缘向内收进_终点距缘24dp`() {
        // 右吸附：1080 - 168 - 72 = 840（FAB 右缘距屏缘 72px=24dp）
        assertEquals(840f, FabDockPolicy.undockInwardX(FabDockPolicy.SIDE_RIGHT, 1080f, 168f, 72f), 1e-4f)
    }

    @Test
    fun `点按脱附x_左缘内收_SIDE_NONE防御分支同走else`() {
        assertEquals(72f, FabDockPolicy.undockInwardX(FabDockPolicy.SIDE_LEFT, 1080f, 168f, 72f), 1e-4f)
        assertEquals(72f, FabDockPolicy.undockInwardX(FabDockPolicy.SIDE_NONE, 1080f, 168f, 72f), 1e-4f)
    }

    @Test
    fun `点按脱附x_窄屏coerce不越界`() {
        // 极窄屏 200px：右 (200-168-72)coerceAtLeast(0)=0；左 min(72, 200-168=32)=32
        assertEquals(0f, FabDockPolicy.undockInwardX(FabDockPolicy.SIDE_RIGHT, 200f, 168f, 72f), 1e-4f)
        assertEquals(32f, FabDockPolicy.undockInwardX(FabDockPolicy.SIDE_LEFT, 200f, 168f, 72f), 1e-4f)
    }

    // ===== 第三轮 P6 功能语义：resolveClampedX/Y（渲染/拖拽/点按三路径共用） =====

    @Test
    fun `自由态x解析_NaN落默认位_越界clamp_界内透传`() {
        // NaN → 默认物理右下角：1080-168-72=840
        assertEquals(840f, FabDockPolicy.resolveClampedX(Float.NaN, 1080f, 168f, 72f), 1e-4f)
        // 左越界 → 0；右越界 → 父宽-尺寸=912；界内 → 原值
        assertEquals(0f, FabDockPolicy.resolveClampedX(-50f, 1080f, 168f, 72f), 1e-4f)
        assertEquals(912f, FabDockPolicy.resolveClampedX(2000f, 1080f, 168f, 72f), 1e-4f)
        assertEquals(300f, FabDockPolicy.resolveClampedX(300f, 1080f, 168f, 72f), 1e-4f)
    }

    @Test
    fun `自由态y解析_NaN落默认位_越界clamp到16dp边距`() {
        // NaN 默认 = 1920-168-72 = 1680；上越界 clamp 到 48；下越界 clamp 到 1920-168-48=1704
        assertEquals(1680f, FabDockPolicy.resolveClampedY(Float.NaN, 1920f, 168f, 48f, 72f), 1e-4f)
        assertEquals(48f, FabDockPolicy.resolveClampedY(10f, 1920f, 168f, 48f, 72f), 1e-4f)
        assertEquals(1704f, FabDockPolicy.resolveClampedY(1900f, 1920f, 168f, 48f, 72f), 1e-4f)
    }

    @Test
    fun `点按脱附目标_右缘吸附_x内收y保持_脱离吸附`() {
        val target = FabDockPolicy.undockTapTarget(
            FabDockUiState(offsetX = 900f, offsetY = 500f, dockSide = FabDockPolicy.SIDE_RIGHT),
            parentWidthPx = 1080f,
            parentHeightPx = 1920f,
            freeSizePx = 168f,
            inwardMarginPx = 72f,
            edgeMarginPx = 48f,
            defaultMarginPx = 72f,
        )
        assertEquals(FabDockUiState(840f, 500f, FabDockPolicy.SIDE_NONE), target)
    }

    @Test
    fun `点按脱附目标_窄屏与NaN偏移_防御路径收敛`() {
        // 理论不可达（吸附必经拖拽、偏移必为实值），防御性验证：x 窄屏收敛、y NaN 落默认位、脱离吸附
        val target = FabDockPolicy.undockTapTarget(
            FabDockUiState(offsetX = Float.NaN, offsetY = Float.NaN, dockSide = FabDockPolicy.SIDE_LEFT),
            parentWidthPx = 200f,
            parentHeightPx = 1920f,
            freeSizePx = 168f,
            inwardMarginPx = 72f,
            edgeMarginPx = 48f,
            defaultMarginPx = 72f,
        )
        assertEquals(32f, target.offsetX, 1e-4f)
        assertEquals(1920f - 168f - 72f, target.offsetY, 1e-4f)
        assertEquals(FabDockPolicy.SIDE_NONE, target.dockSide)
    }
}
