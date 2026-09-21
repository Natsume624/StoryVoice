# StoryVoice / 拾声

StoryVoice 是一款 Android 本地阅读与听书应用。它支持导入 EPUB、PDF、MOBI、AZW3、FB2、TXT、Markdown、HTML 和常见有声书格式，并可在阅读文字的同时使用离线神经网络语音朗读。

## 这一版的重点

- 内置“台湾故事姐姐”参考音色：甜美、柔软、较明显的台湾腔，面向睡前故事和儿童故事。
- 语音合成在手机本地完成，不使用阿里云、Android 系统 TTS 或云端大模型。
- 首次使用离线 AI 朗读时需下载约 165 MB 的 ZipVoice 模型，之后无需联网。
- 支持阅读进度、继续阅读、最近阅读、书架与自定义合集。
- 支持后台朗读、定时停止、语速调节和音色/模型切换。

## 使用方法

1. 从 [Releases](https://github.com/Natsume624/StoryVoice/releases) 下载并安装最新 APK。
2. 导入一本 EPUB 或 PDF。
3. 打开“设置 → 朗读引擎”，选择“离线神经网络语音”。
4. 进入“语音模型”，下载并选择“台湾故事姐姐（离线）”。
5. 返回阅读页，点击播放按钮开始听书。

模型仅在第一次下载时需要网络。参考音频随应用安装包提供，书籍内容与合成音频不会上传到语音服务。

## 开源说明

StoryVoice 基于 [HandyReader](https://github.com/EucWang/HandyReader) `v1.24.260917`（commit `4c8b5671b28320d1d4773056131e5f0b01fa7f57`）修改，并继续按 GPL-3.0 许可证发布。

离线语音使用 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 与 [ZipVoice](https://github.com/k2-fsa/ZipVoice)。ZipVoice 模型和代码的许可证信息会随发布资产及应用的开源许可页一同提供。

## 构建

需要 JDK 17、Android SDK 36、Build Tools 36.0.0 与 NDK 29.0.13599879：

```bash
./gradlew :app:assembleDebug
```

APK 输出位于 `app/build/outputs/apk/debug/`。

## 许可证

[GNU General Public License v3.0](LICENSE)
