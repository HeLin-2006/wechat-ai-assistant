package com.example.wechataiassistant.service.tool;

import java.util.Map;

/**
 * 工具（函数）抽象：大模型通过 Function Calling 调用它。
 *
 * <p>一个工具 = 一个可被 LLM 调用的函数，包含：</p>
 * <ul>
 *   <li>{@link #name()}：函数名（LLM 用它来调用）</li>
 *   <li>{@link #description()}：函数说明（LLM 靠它决定何时调用）</li>
 *   <li>{@link #parametersSchema()}：参数 JSON Schema（描述参数怎么填）</li>
 *   <li>{@link #execute(Object, ToolContext)}：真正执行并返回结果文本</li>
 * </ul>
 */
public interface Tool {

    /** 函数名（英文，给 LLM 调用用），如 get_weather。 */
    String name();

    /** 函数说明（自然语言，给 LLM 理解用）。 */
    String description();

    /**
     * 参数 JSON Schema（JSON 字符串）：
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "city":  { "type": "string", "description": "城市名称，如 北京" }
     *   },
     *   "required": ["city"]
     * }
     * </pre>
     */
    String parametersSchema();

    /** 执行工具。args 是 LLM 按 Schema 生成的参数；ctx 携带上下文（用户、发送器等）。 */
    String execute(Map<String, Object> args, ToolContext ctx);
}
