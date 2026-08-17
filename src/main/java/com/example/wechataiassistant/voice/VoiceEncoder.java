package com.example.wechataiassistant.voice;

import com.example.wechataiassistant.service.llm.LlmProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把 TTS 生成的 mp3 转成微信可播放的 SILK 语音：
 * <pre>
 *   mp3 --ffmpeg--> PCM(s16le, 24000Hz, mono) --silk_encoder--> .silk
 * </pre>
 *
 * <p>依赖外部工具：ffmpeg（PATH 或 llm.voice.ffmpeg-path 配置）以及
 * silk-v3-encoder 编译出的 silk_encoder（llm.voice.silk-encoder-path 配置）。</p>
 */
@Component
public class VoiceEncoder {

    private static final Logger log = LoggerFactory.getLogger(VoiceEncoder.class);

    private final LlmProperties props;

    public VoiceEncoder(LlmProperties props) {
        this.props = props;
    }

    public record SilkResult(byte[] data, long playTimeMs) {}

    /** 编码结果：SILK 字节流与播放时长（毫秒）。 */
    public SilkResult toSilk(byte[] mp3) throws VoiceEncodeException {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("wechat-voice-");
            Path pcm = dir.resolve("audio.pcm");
            Path silk = dir.resolve("audio.silk");

            int sampleRate = props.getVoice().getSampleRate();

            run(
                new ProcessBuilder(
                    props.getVoice().getFfmpegPath(),
                    "-loglevel", "error",
                    "-i", "pipe:0",
                    "-f", "s16le",
                    "-ar", String.valueOf(sampleRate),
                    "-ac", "1",
                    pcm.toString(),
                    "-y"),
                mp3);

            run(
                new ProcessBuilder(
                    props.getVoice().getSilkEncoderPath(),
                    pcm.toString(),
                    silk.toString(),
                    "-Fs_API", String.valueOf(sampleRate),
                    "-Tencent"),
                null);

            long durationMs = Files.size(pcm) / (sampleRate * 2L / 1000);
            if (durationMs <= 0) {
                throw new VoiceEncodeException("编码后的语音时长为 0");
            }
            byte[] data = Files.readAllBytes(silk);
            if (data.length == 0) {
                throw new VoiceEncodeException("编码后的 SILK 数据为空");
            }
            log.info("语音编码成功: pcm={} bytes, silk={} bytes, duration={}ms", Files.size(pcm), data.length, durationMs);
            return new SilkResult(data, durationMs);
        } catch (VoiceEncodeException e) {
            throw e;
        } catch (IOException e) {
            throw new VoiceEncodeException(
                "语音编码工具执行失败: " + e.getMessage()
                    + "（请确认已安装 ffmpeg 与 silk_encoder，见 README）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VoiceEncodeException("语音编码被中断", e);
        } finally {
            if (dir != null) {
                try {
                    Files.deleteIfExists(dir.resolve("audio.silk"));
                    Files.deleteIfExists(dir.resolve("audio.pcm"));
                    Files.deleteIfExists(dir);
                } catch (IOException ignore) {
                    // 清理失败不影响主流程
                }
            }
        }
    }

    private void run(ProcessBuilder pb, byte[] stdin) throws IOException, InterruptedException, VoiceEncodeException {
        log.debug("执行命令: {}", String.join(" ", pb.command()));
        Process p = pb.start();
        if (stdin != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin);
            }
        } else {
            p.getOutputStream().close();
        }
        // 先排空 stderr，避免管道写满导致子进程阻塞
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            String msg = stderr.replaceAll("\\s+", " ").trim();
            throw new VoiceEncodeException(
                "命令退出码 " + code + ": " + (msg.length() <= 500 ? msg : msg.substring(0, 500)));
        }
    }
}
