package com.example.wechataiassistant.service.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容协议的 LLM 客户端，支持：
 * <ul>
 *   <li>文本对话：POST {base}/chat/completions</li>
 *   <li>图片理解（视觉）：POST {vision-base}/chat/completions（消息带 image_url）</li>
 *   <li>图片生成：POST {image-base}/images/generations</li>
 *   <li>语音合成：POST {tts-base}/audio/speech</li>
 *   <li>语音转文字：POST {asr-base}/audio/transcriptions（multipart）</li>
 * </ul>
 *
 * <p>文本、视觉、生图、TTS、ASR 均可独立配置 base-url 与 api-key
 * （未配置时回退到主 llm.base-url / llm.api-key），例如：
 * DeepSeek 管文本 + 智谱 BigModel 管视觉/生图/TTS。</p>
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmProperties props;
    private final ObjectMapper mapper;

    private final RestClient chatClient;
    private final RestClient visionClient;
    private final RestClient imageClient;
    private final RestClient ttsClient;
    private final RestClient asrClient;

    public LlmClient(LlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;

        this.chatClient = buildClient(props.getBaseUrl(), props.getApiKey());
        this.visionClient = buildClient(props.resolveVisionBaseUrl(), props.resolveVisionApiKey());
        this.imageClient = buildClient(props.resolveImageBaseUrl(), props.resolveImageApiKey());
        this.ttsClient = buildClient(props.resolveTtsBaseUrl(), props.resolveTtsApiKey());
        this.asrClient = buildClient(props.resolveAsrBaseUrl(), props.resolveAsrApiKey());
    }

    private static RestClient buildClient(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(factory)
            .build();
    }

    public boolean isConfigured() {
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    /** 文本对话（不带图片）。 */
    public String chat(List<ChatMessage> messages) {
        return chatInternal(chatClient, props.getChatModel(), messages, null);
    }

    /** 图片理解：把图片作为最后一条 user 消息的附件发给视觉模型。 */
    public String chatWithVision(List<ChatMessage> messages, String imageDataUri) {
        return chatInternal(visionClient, props.resolveVisionModel(), messages, imageDataUri);
    }

    private String chatInternal(
        RestClient client, String model, List<ChatMessage> messages, String imageDataUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, Object>> msgs = new ArrayList<>();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", props.getSystemPrompt());
        msgs.add(system);

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            boolean last = (i == messages.size() - 1);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role());
            if (last && imageDataUri != null && !imageDataUri.isBlank()) {
                List<Map<String, Object>> content = new ArrayList<>();
                content.add(Map.of("type", "text", "text", m.content()));
                content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUri)));
                msg.put("content", content);
            } else {
                msg.put("content", m.content());
            }
            msgs.add(msg);
        }
        body.put("messages", msgs);
        body.put("temperature", props.getChatTemperature());
        body.put("max_tokens", props.getChatMaxTokens());

        String json = post(client, "/chat/completions", body);
        try {
            JsonNode node = mapper.readTree(json);
            String content = node.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new LlmException("模型返回内容为空: " + abbreviate(json));
            }
            return content.trim();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析模型响应失败: " + e.getMessage(), e);
        }
    }

    /** 图片生成，返回图片字节（PNG/JPEG 由模型决定）。 */
    public byte[] generateImage(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getImageModel());
        body.put("prompt", prompt);
        body.put("n", 1);

        String json = post(imageClient, "/images/generations", body);
        try {
            JsonNode data = mapper.readTree(json).path("data").path(0);
            String b64 = data.path("b64_json").asText(null);
            if (b64 != null && !b64.isEmpty()) {
                return Base64.getDecoder().decode(b64);
            }
            String url = data.path("url").asText(null);
            if (url != null && !url.isEmpty()) {
                log.info("图片生成返回 url，开始下载: {}", url);
                try {
                    return imageClient.get().uri(url).retrieve().body(byte[].class);
                } catch (RestClientResponseException e) {
                    throw new LlmException("下载生成图片失败: " + e.getStatusCode(), e);
                }
            }
            throw new LlmException("图片生成响应缺少 data[0].b64_json / url: " + abbreviate(json));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析图片生成响应失败: " + e.getMessage(), e);
        }
    }

    /** 语音合成（TTS），返回音频字节（mp3/wav 由服务端决定）。 */
    public byte[] textToSpeech(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getTtsModel());
        body.put("input", text);
        body.put("voice", props.getTtsVoice());
        try {
            byte[] audio =
                ttsClient
                    .post()
                    .uri("/audio/speech")
                    .body(body)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new LlmException("TTS 返回音频为空");
            }
            return audio;
        } catch (RestClientResponseException e) {
            throw new LlmException(
                "TTS 接口返回 " + e.getStatusCode() + ": " + abbreviate(e.getResponseBodyAsString()), e);
        }
    }

    /** 语音转文字（ASR），返回转写文本。 */
    public String transcribe(byte[] audioBytes, String fileName) {
        if (props.resolveAsrModel().isBlank()) {
            throw new LlmException("未配置 llm.asr-model，无法转写语音");
        }
        ByteArrayResource resource =
            new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("model", props.resolveAsrModel());
        try {
            String json =
                asrClient
                    .post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(json);
            String text = node.path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new LlmException("ASR 返回文本为空: " + abbreviate(json));
            }
            return text.trim();
        } catch (RestClientResponseException e) {
            throw new LlmException(
                "ASR 接口返回 " + e.getStatusCode() + ": " + abbreviate(e.getResponseBodyAsString()), e);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析 ASR 响应失败: " + e.getMessage(), e);
        }
    }

    private String post(RestClient client, String path, Object body) {
        try {
            return client.post().uri(path).body(body).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new LlmException(
                "LLM 接口返回 " + e.getStatusCode() + ": " + abbreviate(e.getResponseBodyAsString()), e);
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 300 ? t : t.substring(0, 300) + "...";
    }
}
