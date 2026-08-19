package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.llm.LlmClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具：生成图片并直接发送给用户（有副作用的工具）。
 * 参数 prompt（必填）。
 */
@Component
public class GenerateImageTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GenerateImageTool.class);

    private final LlmClient llm;

    public GenerateImageTool(LlmClient llm) {
        this.llm = llm;
    }

    private static final String SCHEMA =
        """
        {
          "type": "object",
          "properties": {
            "prompt": {
              "type": "string",
              "description": "图片内容的详细描述，例如：一只戴眼镜的橘猫坐在书桌前看书"
            }
          },
          "required": ["prompt"]
        }
        """;

    @Override
    public String name() {
        return "generate_image";
    }

    @Override
    public String description() {
        return "根据描述生成一张图片，并直接发送给用户。用户要求画图、生成图片、配图时调用。";
    }

    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Map<String, Object> args, ToolContext ctx) {
        String prompt = args.get("prompt") == null ? "" : String.valueOf(args.get("prompt")).trim();
        if (prompt.isEmpty()) {
            return "缺少 prompt 参数";
        }
        if (ctx == null || !ctx.hasSender()) {
            return "当前环境不支持发送图片";
        }
        byte[] image = llm.generateImage(prompt);
        ctx.sender().sendImage(image, "ai-image.png", "为你生成的图片：" + prompt);
        log.info("生图工具已发送图片 prompt={}", prompt);
        return "图片已生成并发送给用户，图片描述是：" + prompt;
    }
}
