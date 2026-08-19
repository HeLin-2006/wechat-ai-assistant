package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.ai.ConversationMemory;
import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工具调用（Function Calling）循环：
 *
 * <pre>
 * 第 1 轮: 用户消息 + tools 描述 → LLM
 *    ├─ LLM 直接回答（无 tool_calls）→ 返回最终答案
 *    └─ LLM 要求调用工具（返回 tool_calls）
 *           ↓ 执行工具，把结果回传
 * 第 2 轮: 用户消息 + assistant(tool_calls) + tool(结果) → LLM
 *    └─ 循环直到 LLM 给出最终答案（上限 llm.tool-max-rounds 轮）
 * </pre>
 */
@Service
public class ToolCallService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallService.class);

    private final LlmClient llm;
    private final LlmProperties props;
    private final ToolRegistry registry;
    private final ConversationMemory memory;

    public ToolCallService(LlmClient llm, LlmProperties props, ToolRegistry registry, ConversationMemory memory) {
        this.llm = llm;
        this.props = props;
        this.registry = registry;
        this.memory = memory;
    }

    /**
     * 处理一条用户消息：带上历史上下文和工具，运行工具调用循环，返回最终回答。
     */
    public String respond(String userId, String content, ToolContext ctx) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : memory.history(userId)) {
            messages.add(plainMessage(m.role(), m.content()));
        }
        messages.add(plainMessage("user", content));

        int maxRounds = Math.max(props.getToolMaxRounds(), 1);
        for (int round = 1; round <= maxRounds; round++) {
            log.info("🔧 工具调用第 {} 轮", round);
            LlmClient.ChatResult result = llm.chatWithTools(messages, registry.toolsJson());

            if (!result.hasToolCalls()) {
                String finalAnswer = result.content();
                if (finalAnswer != null && !finalAnswer.isBlank()) {
                    return finalAnswer;
                }
                // 模型返回空内容（推理占用过多 token 等偶发情况）：提示后重试一次
                log.warn("⚠️ 模型返回空内容，提示重试");
                messages.add(plainMessage("user", "（你刚才的回复是空的，请直接给出最终回答，不要调用工具）"));
                continue;
            }

            // 1) 把 assistant 的 tool_calls 原样回传（LLM 协议要求）
            messages.add(assistantToolCallMessage(result.toolCalls()));

            // 2) 逐个执行工具，把结果作为 role=tool 消息追加
            for (LlmClient.ToolCall tc : result.toolCalls()) {
                log.info("🔧 调用工具: {} args={}", tc.name(), tc.arguments());
                String out = registry.execute(tc, ctx);
                log.info("🔧 工具结果: {}", out);
                messages.add(toolResultMessage(tc.id(), out));
            }
        }
        log.warn("工具调用超过 {} 轮上限，停止", maxRounds);
        return "抱歉，这个问题太复杂了，我没能处理完。";
    }

    // ------------------------------------------------------------------
    // 消息构造（OpenAI 兼容消息格式）
    // ------------------------------------------------------------------

    private static Map<String, Object> plainMessage(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /** assistant 的 tool_calls 消息：必须原样回传 id/type/function。 */
    private static Map<String, Object> assistantToolCallMessage(List<LlmClient.ToolCall> calls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        m.put("content", null);
        List<Map<String, Object>> tcs = new ArrayList<>();
        for (LlmClient.ToolCall tc : calls) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tc.name());
            fn.put("arguments", tc.arguments());
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", tc.id());
            call.put("type", "function");
            call.put("function", fn);
            tcs.add(call);
        }
        m.put("tool_calls", tcs);
        return m;
    }

    /** 工具执行结果消息：用 tool_call_id 关联到对应的工具调用。 */
    private static Map<String, Object> toolResultMessage(String toolCallId, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", toolCallId);
        m.put("content", result);
        return m;
    }
}
