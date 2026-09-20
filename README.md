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

## 运行

1. 在阿里云百炼创建 API Key，并安装 Node.js 20 或更高版本。
2. 配置环境变量。PowerShell 示例：

   ```powershell
   $env:DASHSCOPE_API_KEY='你的百炼 API Key'
   $env:DASHSCOPE_BASE_URL='https://你的WorkspaceId.cn-beijing.maas.aliyuncs.com/api/v1'
   ```

   北京地域建议使用业务空间专属地址；API Key 必须与所选地域一致。也可参考 `server/.env.example` 中的国际站公共地址。
3. 启动后端：`node server.js`。
4. 使用 Android Studio 打开项目并等待 Gradle 同步完成。
5. 使用 Android 8.0（API 26）或更高版本的设备运行。

在真机上，点击阅读器右上角的设置按钮，填写电脑的局域网地址，例如 `http://192.168.50.149:8787`；手机与电脑需要连接同一网络，电脑防火墙需允许 8787 端口。模拟器使用 `http://10.0.2.2:8787`。生产环境应填写已部署的 HTTPS 地址，并在服务端加入用户认证、速率限制和费用配额。API 密钥只能配置在后端，不能写入 Android 工程。

如果命令行只安装了 Java 25，请在 Android Studio 中将 Gradle JDK 设为内置的 JDK 17/21。项目目录包含中文时已通过 `android.overridePathCheck=true` 允许构建。

## 当前边界

- 扫描版 PDF 没有文本层，当前会提示需要 OCR。
- 超长章节会在完整句子边界分块分析；跨分块时人物音色的一致性仍有提升空间。
- 当前版本按章节朗读，暂未加入后台媒体通知、锁屏控制和跨启动播放位置恢复。
- 应用应清楚告知最终用户：听到的语音由 AI 生成，并非真人录音。
