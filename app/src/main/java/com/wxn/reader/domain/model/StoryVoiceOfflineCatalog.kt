package com.wxn.reader.domain.model

import com.wxn.base.bean.MODEL_TYPE_ZIPVOICE
import com.wxn.reader.util.tts.data.Speaker

/**
 * StoryVoice's first-party offline voice. The model archive is published with
 * the matching app release and is only needed once; synthesis then stays fully
 * on-device.
 */
object StoryVoiceOfflineCatalog {
    const val MODEL_NAME = "taiwan-story-voice"

    val taiwanStoryVoice = TTSModelData(
        name = MODEL_NAME,
        url = "https://github.com/Natsume624/StoryVoice/releases/download/v0.3.0/StoryVoice-ZipVoice-zh-TW.zip",
        type = MODEL_TYPE_ZIPVOICE,
        locale = "zh-TW",
        size = "约 165 MB",
        base = emptyList(),
        processSpeed = 0.55f,
        quality = 0.9f,
        speakers_num = 1,
        speakers = listOf(
            Speaker(
                id = "taiwan-story-sister-offline",
                index = 0,
                name = "台湾故事姐姐（离线）",
                gender = "Female",
                locale = "zh-TW",
                description = "甜美、柔软、台湾腔，像幼稚园老师讲睡前故事",
                active = true,
                sampleVoice = ""
            )
        ),
        license = "Apache-2.0",
        licenseUrl = "https://github.com/k2-fsa/ZipVoice/blob/master/LICENSE",
        remark = "首次下载模型后无需联网，不使用阿里云或任何云端语音服务。"
    )
}
