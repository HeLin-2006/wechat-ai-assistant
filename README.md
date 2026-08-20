# wechat-ai-assistant

基于 **Spring Boot 4 + wechat-ilink-sdk** 的微信 AI 助手：接入微信官方 iLink Bot 协议（扫码登录、消息收发），并连接大模型实现 **文本 / 图片 / 语音** 三类智能回复。

## 功能总览

| 能力 | 说明 |
|---|---|
| 🔐 微信连接 | [wechat-ilink-sdk](https://github.com/lith0924/wechat-ilink-sdk-java) 二维码登录，登录态持久化，重启免扫码 |
| 📨 收发消息 | 心跳轮询收消息；支持发送文本 / 图片 / 语音 / 文件 / 视频 |
| 🧠 意图识别 | 自动识别天气 / 生图 / 语音 / 指令 / 闲聊，按意图路由 |
| 🌤️ 天气查询 | 说「北京明天天气」→ 自动查和风天气（或 Open-Meteo）返回实时天气与预报 |
| 💬 文本回复 | OpenAI 兼容 Chat Completions |
| 🖼️ 图片理解 | 收到图片自动走视觉模型描述内容 |
| 🎨 图片生成 | 发「画一只猫」/「/img 一只猫」→ 生成图片并发送 |
| 🎤 语音 | 收到语音自动转文字；「/语音 你好」→ TTS 合成语音/音频文件回复 |
| 🧠 多轮记忆 | 每用户保留最近 N 轮上下文，/clear 清空 |

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.9+

### 2. 配置 API Key

在 `src/main/resources/application.properties` 中配置（**推荐用环境变量注入，避免 Key 进仓库**）：

```bash
# 文本对话（DeepSeek，够快够便宜）
export LLM_API_KEY=sk-deepseek-xxxx
export LLM_BASE_URL=https://api.deepseek.com

# 图片理解 / 图片生成 / 语音（推荐智谱，一个 Key 搞定，有免费模型）
export LLM_VISION_API_KEY=sk-zhipu-xxxx
export LLM_IMAGE_API_KEY=sk-zhipu-xxxx
export LLM_TTS_API_KEY=sk-zhipu-xxxx
```

> 智谱 Key 申请：https://open.bigmodel.cn/usercenter/apikeys （GLM-4V-Flash 视觉、cogview-3-flash 生图免费）

| 能力 | 推荐服务商 | 配置项（未配置时回退到主配置） |
|---|---|---|
| 文本对话 | DeepSeek `deepseek-chat` | `llm.api-key` / `llm.base-url` / `llm.chat-model` |
| 图片理解（视觉） | 智谱 `glm-4v-flash` | `llm.vision-api-key` / `llm.vision-base-url` / `llm.vision-model` |
| 图片生成 | 智谱 `cogview-3-flash` | `llm.image-api-key` / `llm.image-base-url` / `llm.image-model` |
| 语音合成 TTS | 智谱 `glm-tts` | `llm.tts-api-key` / `llm.tts-base-url` / `llm.tts-model` |
| 语音转写 ASR | 智谱 `glm-asr-2512` | `llm.asr-api-key` / `llm.asr-base-url` / `llm.asr-model` |

> 也可以全部用 OpenAI（`gpt-4o-mini` / `gpt-image-1` / `gpt-4o-mini-tts`），一个 Key 全能力。
> 配置后可用 `GET /wechat/llm-config` 查看生效配置（Key 打码），用 `GET /wechat/test/chat|image|tts` 单独验证每项能力。

### 3. 启动并扫码登录

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/wechat-ai-assistant-0.0.1-SNAPSHOT.jar
```

浏览器打开 **http://localhost:8080/wechat/**，用微信「扫一扫」扫码并确认登录。

登录成功后（状态持久化到 `wechat-session.json`），给机器人发消息即可自动得到 AI 回复。

> **怎么和机器人对话**：iLink 的设计就是**扫码绑定的那个微信账号直接跟机器人聊**——登录后，在微信里打开与机器人的对话（机器人 ID 形如 `xxx@im.bot`），直接发消息即可。无需第二个微信号。
> 若发现机器人不回复，先看控制台日志是否出现 `📩 消息: id=..., from=...`；若日志显示 `⏭️ 跳过` 且配置了 `wechat.ignore-self=true`，改成 `false`（默认即 false）后重启。

### 4. 天气查询（意图识别）

说天气相关的话，机器人自动识别意图并查天气（无需任何指令）：

| 你说 | 机器人返回 |
|---|---|
| 北京天气怎么样 | 北京当前天气（温度/天气现象/风速/湿度） |
| 深圳明天天气 | 明天预报（高温/低温/天气现象） |
| 上海这周天气 | 近 3 天预报 |
| 现在几度 | 默认城市（`weather.default-city`，默认北京）当前温度 |

天气数据源（`weather.*` 配置）：
- **和风天气（推荐，国内准确）**：到 https://dev.qweather.com 注册 → 控制台「项目管理」创建项目 → 添加 Key（免费版即可），把 Key 填入本地 `application-secret.properties` 的 `weather.api-key=`（或环境变量 `QWEATHER_API_KEY`）
- **Open-Meteo（免 Key 兜底）**：`weather.provider=open-meteo`，或 qweather 未配置 Key 时自动回退，同样支持中文城市名

自测：`GET /wechat/test/weather?city=北京&when=tomorrow`（when: today/tomorrow/dayafter/week）

### 5. 语音回复（可选，需要本地编码工具）

> ⚠️ **重要说明**：微信官方已调整协议，Bot 通过 `sendVoice` 发送的**语音气泡不再渲染**（接口正常返回，但用户看不到，见 [SDK issue #13](https://github.com/lith0924/wechat-ilink-sdk-java/issues/13)）。
> 因此本项目默认把 TTS 音频作为 **mp3/wav 音频文件**发送（微信端可直接点开播放），`llm.voice-bubble-enabled=true` 可尝试 SILK 气泡（当前不显示）。
> 入站语音识别不受影响（网关自带转写文本 / ASR）。

语音回复链路：`LLM TTS → 音频文件（默认）` 或 `LLM TTS (mp3) → ffmpeg → PCM → silk_encoder → 微信语音消息（气泡模式，可选）`。

安装依赖：

```bash
# macOS（ffmpeg）
brew install ffmpeg

# silk 编解码器（本机已编译安装到 /opt/homebrew/bin/silk_codec，
# 并提供 silk_encoder / silk_decoder 兼容命令；其他机器可参考：
#   git clone https://github.com/KasukuSakura/silk-codec.git
#   cd silk-codec/native && clang++ -O2 -std=c++14 \
#     -I jni_include/common -I jni_include/inc_mac -I src/interface -I src/silk \
#     -I src/bind -I <cxxopts路径> \
#     -o silk_codec src/silk_codec/main.cpp src/bind/*.cpp $(find src/silk -name '*.c') -lm
# 再按需配置 llm.voice.silk-encoder-path / silk-decoder-path)
```

未安装时功能自动降级：语音回复改为发送 mp3 音频文件，不影响其他功能。

## REST 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/wechat/` | 扫码登录页面（浏览器打开即可） |
| GET | `/wechat/login?force=false` | 获取登录二维码（图片 URL 或 data URI） |
| GET | `/wechat/status` | 登录状态 / 连接状态 / botId |
| GET | `/wechat/updates` | 手动拉取一次消息（调试） |
| POST | `/wechat/send/text` | `{"toUserId":"xxx@im.wechat","text":"hi"}` |
| POST | `/wechat/send/image` | `{"toUserId":"...","base64":"...","fileName":"a.png","caption":"图"}` |
| POST | `/wechat/send/voice` | `{"toUserId":"...","base64":"...","fileName":"a.silk","playTimeMs":3000,"sampleRate":24000}` |
| POST | `/wechat/clear-context` | `{"toUserId":"..."}` 清空该用户会话上下文 |

## 微信侧指令

| 指令 | 作用 |
|---|---|
| 直接发文字 | LLM 文本回复 |
| 发图片 | 视觉模型描述图片 |
| 发语音 | 自动转文字后回答 |
| `/img 描述` 或 `画描述` | 生成图片并发送 |
| `/语音 内容` 或 `/voice 内容` | 语音（TTS）回复；设置 `llm.voice-reply-enabled=true` 后每条都附带语音 |
| `/clear` | 清空对话上下文 |
| `/help` | 帮助 |

## 项目结构

```
src/main/java/com/example/wechataiassistant/
├── WechatAiAssistantApplication.java   # 启动类（@ConfigurationPropertiesScan）
├── config/WechatProperties.java        # wechat.* 配置
├── controller/
│   ├── HelloController.java
│   └── WechatBotController.java        # REST 接口 + 扫码页面
├── service/
│   ├── WechatBotService.java           # ILinkClient 门面：登录/发送/持久化
│   ├── llm/                            # OpenAI 兼容 LLM 客户端
│   │   ├── LlmProperties.java
│   │   ├── LlmClient.java              # chat/vision / 图片生成 / TTS
│   │   ├── ChatMessage.java
│   │   └── LlmException.java
│   └── ai/
│       ├── AiMessageHandler.java       # 消息监听 → 路由到 LLM / 指令
│       └── ConversationMemory.java     # 多轮上下文
└── voice/
    ├── VoiceEncoder.java               # mp3 → PCM → SILK
    └── VoiceEncodeException.java
```

## 注意事项

- 本项目连接的是微信官方 iLink Bot 协议（`https://ilinkai.weixin.qq.com`），请遵守微信平台规范，勿用于骚扰、营销等违规场景。
- `wechat-session.json` 含登录凭据，已加入 `.gitignore`，请勿提交。
- API Key 请通过环境变量注入，切勿写死在配置里提交到仓库。

### 工具调用（Function Calling）🧰

大模型在对话中可**自主决定调用工具**（OpenAI 兼容 `tools` 协议）：

| 工具 | 参数（JSON Schema） | 作用 |
|---|---|---|
| `get_current_time` | 无 | 返回当前日期时间与星期 |
| `get_weather` | `city`(可选) / `when`(枚举 today/tomorrow/dayafter/week) | 查天气（复用天气服务） |
| `generate_image` | `prompt`(必填) | 生成图片并直接发给用户 |
| `get_city_coordinates` | `city`(必填) | 城市 → 经纬度（链式调用第一步） |
| `get_sunrise_sunset` | `latitude`/`longitude`(必填) | 经纬度 → 今日日出日落（链式调用第二步） |

**链式调用（多步工具）示例**：问「上海今天几点日出日落」→
```
第1轮: LLM 调用 get_city_coordinates(city=上海)
      → 返回 latitude=31.2222, longitude=121.4581
第2轮: LLM 调用 get_sunrise_sunset(latitude=31.2222, longitude=121.4581)  ← 参数来自上一步结果
      → 返回 日出05:22 日落18:32
第3轮: LLM 组织最终回答
```
`ToolCallService` 自动处理多轮往返（每轮工具结果作为 `role=tool` 消息回传，最多 `llm.tool-max-rounds` 轮），后续步骤的输入可直接依赖前一步输出。

工作流程（`ToolCallService` 实现，最多 `llm.tool-max-rounds` 轮）：
```
用户消息 + 工具描述 → LLM
  ├─ 直接回答 → 返回
  └─ 要求调用工具 → 执行工具 → 结果回传 → LLM → 直到给出最终回答
```

自测：`GET /wechat/test/tools?text=现在几点了`；微信里直接问「现在几点了」「北京多少度」「上海日出时间」「画一只猫」即可触发。
新增工具：实现 `service/tool/Tool` 接口（name/description/parametersSchema/execute），加 `@Component` 即可自动注册。
