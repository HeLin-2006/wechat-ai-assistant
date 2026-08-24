package com.example.wechataiassistant.service.skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 技能注册表与调度：Spring 自动收集所有 {@link Skill} 实现，
 * 按关键词匹配用户消息，命中即执行（确定性输出，不经过 LLM）。
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final Map<String, Skill> skills;

    public SkillService(List<Skill> skillList) {
        this.skills = skillList.stream().collect(Collectors.toMap(Skill::name, Function.identity()));
        log.info("已注册技能 {} 个: {}", skills.size(), skills.keySet());
    }

    public List<Skill> list() {
        return List.copyOf(skills.values());
    }

    /**
     * 按关键词匹配并执行技能；未命中返回空。
     *
     * @return 技能回复文本（Optional.empty 表示没有技能命中）
     */
    public Optional<String> executeIfMatched(String content, String userId) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String text = content.trim();
        for (Skill skill : skills.values()) {
            for (String keyword : skill.keywords()) {
                if (text.contains(keyword)) {
                    try {
                        String reply = skill.execute(userId, text);
                        log.info("🧩 技能 [{}] 命中关键词 [{}]，已执行", skill.name(), keyword);
                        return Optional.ofNullable(reply);
                    } catch (Exception e) {
                        log.error("技能 [{}] 执行失败", skill.name(), e);
                        return Optional.of("技能执行出错：" + e.getMessage());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
