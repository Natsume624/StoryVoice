import http from "node:http";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.DASHSCOPE_API_KEY;
const dashscopeBase = (process.env.DASHSCOPE_BASE_URL || "https://dashscope.aliyuncs.com/api/v1").replace(/\/$/, "");
const compatibleBase = dashscopeBase.replace(/\/api\/v1$/, "/compatible-mode/v1");
const plannerModel = process.env.STORY_PLANNER_MODEL || "qwen3.8-flash";
const ttsModel = process.env.STORY_TTS_MODEL || "qwen3-tts-instruct-flash";
const omniModel = process.env.STORY_OMNI_MODEL || "qwen3.5-omni-plus";

const speakerVoices = {
  narrator: "Ethan",
  character_1: "Serena",
  character_2: "Cherry",
  character_3: "Chelsie"
};

const selectableVoices = new Set(["Cindy", "Tina", "Serena", "Ethan", "Cherry", "Chelsie", "auto"]);
const omniVoices = new Set(["Cindy", "Tina"]);

const emotionDirections = {
  neutral: "自然、克制，像专业有声书演员",
  warm: "温暖亲切，带轻微笑意",
  joy: "愉悦明亮，但不要夸张",
  sad: "低沉悲伤，适当放慢并留出呼吸感",
  tense: "紧张克制，节奏稍快，制造悬念",
  angry: "压抑的愤怒，有力量但不要喊叫",
  whisper: "轻声低语，营造靠近听众的私密感",
  solemn: "庄重沉稳，停顿清晰"
};

const segmentSchema = {
  type: "object",
  additionalProperties: false,
  required: ["segments"],
  properties: {
    segments: {
      type: "array",
      maxItems: 80,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["text", "speaker", "emotion"],
        properties: {
          text: { type: "string" },
          speaker: {
            type: "string",
            enum: ["narrator", "character_1", "character_2", "character_3"]
          },
          emotion: {
            type: "string",
            enum: ["neutral", "warm", "joy", "sad", "tense", "angry", "whisper", "solemn"]
          }
        }
      }
    }
  }
};

function json(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body)
  });
  res.end(body);
}

async function readJson(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (body.length > 1_000_000) throw new Error("请求内容过大");
  }
  return JSON.parse(body || "{}");
}

async function dashscope(url, init) {
  if (!apiKey) throw new Error("服务端尚未配置 DASHSCOPE_API_KEY");
  const response = await fetch(url, {
    ...init,
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      ...init.headers
    }
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`阿里云百炼 API 请求失败（${response.status}）：${detail.slice(0, 300)}`);
  }
  return response;
}

async function planStory(text) {
  if (!text || typeof text !== "string") throw new Error("缺少正文内容");
  if (text.length > 12_000) throw new Error("单次角色分析不能超过 12,000 字符");
  const instructions = [
    "你是中文有声书导演。把输入原文切成适合逐段配音的片段。",
    "必须逐字保留全部原文，不得改写、删减或新增内容。",
    "叙述使用 narrator；根据上下文把人物台词稳定映射到 character_1 到 character_3。",
    "每段尽量为完整句子且不超过 350 个汉字，并判断最合适的表演情绪。"
  ].join("\n");
  const response = await dashscope(`${compatibleBase}/chat/completions`, {
    method: "POST",
    body: JSON.stringify({
      model: plannerModel,
      messages: [
        { role: "system", content: instructions },
        { role: "user", content: text }
      ],
      enable_thinking: false,
      response_format: {
        type: "json_schema",
        json_schema: {
          name: "audiobook_segments",
          strict: true,
          schema: segmentSchema
        }
      }
    })
  });
  const result = await response.json();
  const content = result?.choices?.[0]?.message?.content;
  if (!content) throw new Error("通义千问没有返回有效的角色分析结果");
  return JSON.parse(content);
}

async function createSpeech({ text, speaker, emotion, expressiveness, voice: requestedVoice }) {
  if (!text || typeof text !== "string") throw new Error("缺少朗读文本");
  if (text.length > 600) throw new Error("单段朗读文本不能超过 600 字符");
  const selected = selectableVoices.has(requestedVoice) ? requestedVoice : "Cindy";
  const voice = selected === "auto" ? (speakerVoices[speaker] || speakerVoices.narrator) : selected;
  const direction = emotionDirections[emotion] || emotionDirections.neutral;
  const strength = Math.round(Math.min(1, Math.max(0, Number(expressiveness) || 0.75)) * 100);
  const storyDirection = [
    "像一位非常有耐心的幼儿园老师，在睡前给小朋友讲故事。",
    "整体温柔、亲切、安全，语速稍慢，句尾柔和，停顿自然，避免播音腔和夸张表演。",
    `本段表演方向：${direction}。情感表现强度：${strength}%。`,
    "逐字朗读原文，不改写、不省略，也不添加开场白或解释。"
  ].join("\n");

  if (omniVoices.has(voice)) {
    return createOmniSpeech(text, voice, storyDirection);
  }

  const response = await dashscope(`${dashscopeBase}/services/aigc/multimodal-generation/generation`, {
    method: "POST",
    body: JSON.stringify({
      model: ttsModel,
      input: {
        text,
        voice,
        language_type: "Chinese",
        instructions: storyDirection,
        optimize_instructions: true
      }
    })
  });
  const result = await response.json();
  const audioUrl = result?.output?.audio?.url;
  if (!audioUrl) throw new Error(`阿里云未返回音频地址：${result?.message || "未知错误"}`);
  const audio = await fetch(audioUrl);
  if (!audio.ok || !audio.body) throw new Error(`下载阿里云生成的音频失败（${audio.status}）`);
  return audio;
}

async function createOmniSpeech(text, voice, storyDirection) {
  const response = await dashscope(`${compatibleBase}/chat/completions`, {
    method: "POST",
    body: JSON.stringify({
      model: omniModel,
      messages: [
        {
          role: "system",
          content: `${storyDirection}\n${voice === "Cindy" ? [
            "使用明显、辨识度较高的台湾国语口音，像一位特别温柔的台湾幼稚园老师在讲睡前故事。",
            "台湾腔要比一般程度更突出：卷舌音轻一些，咬字柔软圆润，语调起伏有台湾口语韵律，句尾自然轻扬后柔柔收住。",
            "声音轻轻的、暖暖的，带自然笑意和柔和气息感，语速略慢。",
            "让小朋友感到安心、被陪伴，但不要夸张撒娇，不要使用刻意的夹子音。"
          ].join("\n") : "声音甜美温暖，像陪伴小朋友的故事老师。"}`
        },
        { role: "user", content: text }
      ],
      modalities: ["text", "audio"],
      audio: { voice, format: "wav" },
      stream: true,
      stream_options: { include_usage: true }
    })
  });

  const sse = await response.text();
  const chunks = [];
  for (const line of sse.split(/\r?\n/)) {
    if (!line.startsWith("data:")) continue;
    const data = line.slice(5).trim();
    if (!data || data === "[DONE]") continue;
    const event = JSON.parse(data);
    const audio = event?.choices?.[0]?.delta?.audio?.data;
    if (audio) chunks.push(Buffer.from(audio, "base64"));
  }
  if (!chunks.length) throw new Error("阿里云全模态模型没有返回音频数据");
  const raw = Buffer.concat(chunks);
  const wav = raw.subarray(0, 4).toString() === "RIFF" ? raw : pcmToWav(raw, 24_000, 1, 16);
  return new Response(wav, { headers: { "Content-Type": "audio/wav" } });
}

function pcmToWav(pcm, sampleRate, channels, bitsPerSample) {
  const header = Buffer.alloc(44);
  const byteRate = sampleRate * channels * bitsPerSample / 8;
  const blockAlign = channels * bitsPerSample / 8;
  header.write("RIFF", 0); header.writeUInt32LE(36 + pcm.length, 4); header.write("WAVE", 8);
  header.write("fmt ", 12); header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20);
  header.writeUInt16LE(channels, 22); header.writeUInt32LE(sampleRate, 24); header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(blockAlign, 32); header.writeUInt16LE(bitsPerSample, 34);
  header.write("data", 36); header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && req.url === "/health") {
      return json(res, 200, { ok: true, configured: Boolean(apiKey) });
    }
    if (req.method === "POST" && req.url === "/api/plan") {
      return json(res, 200, await planStory((await readJson(req)).text));
    }
    if (req.method === "POST" && req.url === "/api/speech") {
      const response = await createSpeech(await readJson(req));
      res.writeHead(200, {
        "Content-Type": response.headers.get("content-type") || "audio/wav",
        "Cache-Control": "private, max-age=31536000, immutable"
      });
      for await (const chunk of response.body) res.write(chunk);
      return res.end();
    }
    json(res, 404, { error: "接口不存在" });
  } catch (error) {
    console.error(error);
    json(res, 500, { error: error instanceof Error ? error.message : "服务器错误" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`StoryVoice narration server listening on http://0.0.0.0:${port}`);
});
