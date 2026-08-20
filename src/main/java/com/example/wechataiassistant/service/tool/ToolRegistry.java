package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.llm.LlmClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具注册表：Spring 启动时自动收集所有 {@link Tool} 实现，
 * 负责把工具列表序列化成 LLM 的 tools 参数，并按名字执行。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ObjectMapper mapper;
    private final Map<String, Tool> tools;

    /** Spring 会把所有 Tool 类型的 Bean 自动注入到这个 List 里（依赖注入 + 多态）。 */
    public ToolRegistry(List<Tool> toolList, ObjectMapper mapper) {
        this.mapper = mapper;
        this.tools = toolList.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
        log.info("已注册工具 {} 个: {}", tools.size(), String.join(", ", tools.keySet()));
    }

    public List<Tool> list() {
        return List.copyOf(tools.values());
    }

    /** 工具描述缓存：工具集在运行时不变，只需序列化一次。 */
    private volatile String cachedToolsJson;

    /**
     * 生成 LLM 请求用的 tools 参数（OpenAI 兼容格式）：
     * <pre>
     * [{"type":"function","function":{"name":"...","description":"...","parameters":{...}}}]
     * </pre>
     */
    public String toolsJson() {
        String cached = cachedToolsJson;
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> arr =
            tools.values().stream()
                .map(
                    t -> {
                        Map<String, Object> fn = new LinkedHashMap<>();
                        fn.put("name", t.name());
                        fn.put("description", t.description());
                        fn.put("parameters", parseSchema(t.parametersSchema()));
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("type", "function");
                        entry.put("function", fn);
                        return entry;
                    })
                .toList();
        try {
            String json = mapper.writeValueAsString(arr);
            cachedToolsJson = json;
            return json;
        } catch (Exception e) {
            throw new IllegalStateException("生成 tools 参数失败", e);
        }
    }

    /** 执行工具；任何异常都转成结果文本，不让异常打断 LLM 对话。 */
    public String execute(LlmClient.ToolCall call, ToolContext ctx) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return "没有找到工具: " + call.name() + "，请不要编造工具结果，直接回答用户。";
        }
        try {
            Map<String, Object> args = parseArgs(call.arguments());
            return tool.execute(args, ctx);
        } catch (IllegalArgumentException e) {
            // 参数类错误：提示 LLM 修正参数或直接回答
            log.warn("工具 {} 参数错误: {}", call.name(), e.getMessage());
            return "工具 " + call.name() + " 参数错误: " + e.getMessage() + "（请修正参数后重试，或直接回答用户）";
        } catch (Exception e) {
            // 系统类错误：提示 LLM 向用户说明并给替代建议
            log.error("工具 {} 执行失败", call.name(), e);
            return "工具 " + call.name() + " 执行失败: " + e.getMessage()
                + "（请向用户说明暂时无法完成，并给出替代建议）";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", arguments);
            return Map.of();
        }
    }

    private Object parseSchema(String schema) {
        try {
            return mapper.readValue(schema, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("工具 JSON Schema 无效: " + schema, e);
        }
    }
}
