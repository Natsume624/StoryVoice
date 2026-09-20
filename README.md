# 拾声 · StoryVoice

[![Android Build](https://github.com/Natsume624/StoryVoice/actions/workflows/android-build.yml/badge.svg)](https://github.com/Natsume624/StoryVoice/actions/workflows/android-build.yml)

一个面向 Android 的 AI 听书 MVP。它可以从系统文件选择器导入 EPUB 和文字版 PDF，提取章节/页面文字，并通过云端大模型生成接近真人的多角色有声书音频。

## 已实现

- EPUB：读取 OPF 书目、作者、章节顺序和 XHTML 正文
- PDF：逐页提取文本（基于 PDFBox Android）
- 本地书架：导入后的原文件和解析结果保存在应用私有目录
- AI 导演：大模型根据上下文拆分旁白与人物对白，并判断每段情绪
- 多角色配音：旁白和最多三名角色使用不同的云端音色
- 情感朗读：通过语音模型指令控制喜悦、悲伤、紧张、低语、庄重等表达
- 默认音色：Cindy 明显台湾国语口音，以甜美、柔软、轻轻带笑的幼儿园老师风格讲故事
- 音色切换：台湾故事姐姐、暖心姐姐、温柔女声、阳光男声和自动多角色
- 连续播放：按需生成、磁盘缓存、语速和情感强度调节
- 阅读器：正文、章节切换、播放进度和播放控制
- 同步伴读：朗读句高亮、自动跟随滚动、字号调节、护眼与夜间阅读
- 手机独立运行：在应用内配置阿里云百炼 API Key，直接通过 HTTPS 调用云端模型
- 睡眠定时：支持 15、30、45、60 分钟后自动停止
- 阅读进度：自动保存章节和滚动位置，再次打开时可选择继续或从头阅读
- 最近阅读：书架展示最近打开的书籍
- 自定义合集：创建多个合集，并自由添加或移除书籍

## 运行

1. 在阿里云百炼创建 API Key。
2. 安装 APK，导入一本书并进入阅读器。
3. 点击右上角设置，填写 API Key。北京默认空间可保持 `https://dashscope.aliyuncs.com/api/v1`；业务空间建议填写专属的 `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1`。
4. 保存后即可由手机通过 HTTPS 直接生成和播放语音，无需电脑或局域网服务。

API Key 使用 Android Keystore 加密，仅存放在应用私有数据中，不会写入安装包、书籍或 GitHub。直接在客户端使用个人 Key 适合个人使用；面向公众分发时仍应改用带身份认证和用量限制的服务端，避免共享密钥被滥用。仓库中的 `server` 目录继续保留，供此类正式部署使用。

如果命令行只安装了 Java 25，请在 Android Studio 中将 Gradle JDK 设为内置的 JDK 17/21。项目目录包含中文时已通过 `android.overridePathCheck=true` 允许构建。

## 当前边界

- 扫描版 PDF 没有文本层，当前会提示需要 OCR。
- 超长章节会在完整句子边界分块分析；跨分块时人物音色的一致性仍有提升空间。
- 当前版本按章节朗读，暂未加入后台媒体通知、锁屏控制和跨启动播放位置恢复。
- 应用应清楚告知最终用户：听到的语音由 AI 生成，并非真人录音。
