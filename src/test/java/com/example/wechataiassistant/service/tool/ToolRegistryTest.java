package com.example.wechataiassistant.service.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.wechataiassistant.service.llm.LlmClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ToolRegistry registry(List<Tool> tools) {
        return new ToolRegistry(tools, mapper);
    }

    @Test
    void toolsJsonHasOpenAiFormat() throws Exception {
        ToolRegistry r = registry(List.of(new CurrentTimeTool()));
        JsonNode arr = mapper.readTree(r.toolsJson());
        assertTrue(arr.isArray());
        JsonNode fn = arr.get(0).path("function");
        assertEquals("function", arr.get(0).path("type").asText());
        assertEquals("get_current_time", fn.path("name").asText());
        assertNotNull(fn.path("description").asText());
        assertEquals("object", fn.path("parameters").path("type").asText());
    }

    @Test
    void executeTimeTool() {
        ToolRegistry r = registry(List.of(new CurrentTimeTool()));
        String out = r.execute(new LlmClient.ToolCall("id1", "get_current_time", "{}"), null);
        assertTrue(out.contains("年"), "应包含日期: " + out);
        assertTrue(out.contains("星期"), "应包含星期: " + out);
    }

    @Test
    void executeUnknownToolReturnsFriendlyError() {
        ToolRegistry r = registry(List.of(new CurrentTimeTool()));
        String out = r.execute(new LlmClient.ToolCall("id2", "no_such_tool", "{}"), null);
        assertTrue(out.contains("没有找到工具"));
    }

    @Test
    void executeBadArgsDoesNotCrash() {
        ToolRegistry r = registry(List.of(new CurrentTimeTool()));
        String out = r.execute(new LlmClient.ToolCall("id3", "get_current_time", "not-json{{{"), null);
        assertTrue(out.contains("年"), "参数解析失败应回退空参数并正常执行: " + out);
        assertFalse(out.contains("Exception"));
    }

    @Test
    void parseArgsFromJson() {
        ToolRegistry r = registry(List.of(new WeatherTool(null)));
        // 只验证 execute 在 WeatherService 为 null 时的容错（不 NPE）
        String out = r.execute(new LlmClient.ToolCall("id4", "get_weather", "{\"city\":\"北京\"}"), null);
        assertTrue(out.contains("失败") || out.contains("异常"));
    }
}
