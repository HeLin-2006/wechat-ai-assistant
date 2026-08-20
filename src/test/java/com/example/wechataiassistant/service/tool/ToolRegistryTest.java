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

    @Test
    void chainToolsParameterValidation() {
        // 链式工具的参数校验在调用网络前触发
        ToolRegistry r1 = registry(List.of(new GetCityCoordinatesTool(null)));
        String out1 = r1.execute(new LlmClient.ToolCall("c1", "get_city_coordinates", "{}"), null);
        assertTrue(out1.contains("参数错误"), "缺 city 应报参数错误: " + out1);

        ToolRegistry r2 = registry(List.of(new GetSunriseSunsetTool(null)));
        String out2 = r2.execute(new LlmClient.ToolCall("c2", "get_sunrise_sunset", "{\"latitude\":999,\"longitude\":121}"), null);
        assertTrue(out2.contains("参数错误") || out2.contains("超出合法范围"), "非法坐标应报参数错误: " + out2);
    }

    @Test
    void schemaHasRequiredForChainTools() throws Exception {
        ToolRegistry r = registry(List.of(new GetSunriseSunsetTool(null)));
        JsonNode fn = mapper.readTree(r.toolsJson()).get(0).path("function");
        JsonNode required = fn.path("parameters").path("required");
        assertTrue(required.toString().contains("latitude"), "latitude 应为必填: " + required);
        assertTrue(required.toString().contains("longitude"), "longitude 应为必填: " + required);
    }
}
