package com.wxn.bookparser.impl

/**
 * MOBI 家族扩展名（MOBI7/KF8/Palm 变体），TextParserImpl 与 FileParserImpl 的
 * when 分发共用同一集合，防止 6 处分支漏改。
 * prc 与 mobi/azw3 同为 libmobi 可解析的 PDB 容器（古腾堡 kf8 下载即 *.kf8.images.prc）。
 */
internal val MOBI_FAMILY_EXTENSIONS = setOf("mobi", "azw3", "prc")
