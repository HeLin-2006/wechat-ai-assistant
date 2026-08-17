# wechat-ai-assistant

基于 **Spring Boot 4 + wechat-ilink-sdk** 的微信 AI 助手：接入微信官方 iLink Bot 协议（扫码登录、消息收发），并连接大模型实现 **文本 / 图片 / 语音** 三类智能回复。

## 功能总览

| 能力 | 说明 |
|---|---|
| 🔐 微信连接 | [wechat-ilink-sdk](https://github.com/lith0924/wechat-ilink-sdk-java) 二维码登录，登录态持久化，重启免扫码 |
| 📨 收发消息 | 心跳轮询收消息；支持发送文本 / 图片 / 语音 / 文件 / 视频 |
| 💬 文本回复 | OpenAI 兼容 Chat Completions |
| 🖼️ 图片理解 | 收到图片自动走视觉模型描述内容 |
| 🎨 图片生成 | 发「画一只猫」/「/img 一只猫」→ 生成图片并发送 |
| 🎤 语音 | 收到语音自动转文字；「/语音 你好」→ TTS 合成语音回复（mp3→SILK） |
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

### 4. 语音回复（可选，需要本地编码工具）

语音回复链路：`LLM TTS (mp3) → ffmpeg → PCM → silk_encoder → 微信语音消息`。

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
