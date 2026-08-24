package com.example.wechataiassistant.service.skill;

import java.util.List;

/**
 * 技能（Skill）：按关键词触发、确定性执行的轻量能力。
 *
 * <p>与 Tool（Function Calling）的区别：</p>
 * <ul>
 *   <li>Skill：关键词命中即执行，不经过 LLM，确定性输出（快、免费、稳定）</li>
 *   <li>Tool：由 LLM 在对话中自主决定是否调用</li>
 * </ul>
 *
 * <p>路由优先级：Skill 关键词 → RAG 增强 → LLM 兜底。</p>
 */
public interface Skill {

    /** 技能名。 */
    String name();

    /** 技能说明。 */
    String description();

    /** 触发关键词（命中任一即触发）。 */
    List<String> keywords();

    /**
     * 执行技能，返回回复文本。
     *
     * @param userId 用户 ID
     * @param content 用户消息原文
     */
    String execute(String userId, String content);
}
