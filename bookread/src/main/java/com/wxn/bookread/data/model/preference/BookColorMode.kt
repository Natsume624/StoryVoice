package com.wxn.bookread.data.model.preference

/**
 * 「书籍字体颜色」三态开关（AS-1 P2，完全主题化 §3.7）。
 * 主题字段（第 18 员）：随主题存档/恢复/重置（reader_theme_configs），
 * per-book 模式下随快照冻结/隔离（per_book_theme_overrides）；
 * DataStore 的 BOOK_COLOR_MODE 键仅为工作态。
 */
enum class BookColorMode {
    /** 智能对比（默认）：作者颜色按「在当前阅读背景上是否可读」（WCAG 对比度 ≥3.0 + 低 alpha 守卫）判定，可读保留、不可读回退用户色 */
    SMART,
    /** 跟随主题：段级+span 级作者颜色一律不渲染，永远用户主题文字色（用户优先，旧语义） */
    THEME,
    /** 跟随书籍：作者颜色无条件渲染（跳过对比判定与低 alpha 守卫，逃生门） */
    BOOK
}
