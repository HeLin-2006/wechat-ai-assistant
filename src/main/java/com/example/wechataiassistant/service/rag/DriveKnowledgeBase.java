package com.example.wechataiassistant.service.rag;

import java.util.List;
import org.springframework.stereotype.Component;

/** 自驾游知识库：供自驾路书 Agent 的 RAG 子任务使用。 */
@Component
public class DriveKnowledgeBase implements KnowledgeBase {

    @Override
    public List<RagDocument> all() {
        return List.of(
            doc("plateau", "高原行车安全",
                List.of("高原", "海拔", "缺氧", "高原反应"),
                "高海拔地区注意：提前准备氧气瓶和抗高反药物；抵达当天避免剧烈运动和洗头；"
                    + "如有头疼、气短等症状及时吸氧并休息；车队同行保持沟通，勿单人贸然深入。"),

            doc("fatigue", "长途驾驶疲劳",
                List.of("疲劳", "休息", "服务区", "长途", "换着开"),
                "连续驾驶不超过 2~4 小时，务必到服务区休息 15 分钟以上；长途建议两人轮换驾驶；"
                    + "夜间和午后是疲劳高发时段，感觉困倦立即找安全地点停车休息，不要硬撑。"),

            doc("vehicle-check", "出行前车辆检查",
                List.of("车辆", "检查", "保养", "胎压", "刹车"),
                "出发前检查：轮胎胎压与备胎、刹车系统、机油与冷却液、雨刮与玻璃水、"
                    + "灯光、证件（驾驶证/行驶证/保险单）。山区路段建议加满油再出发。"),

            doc("toll-fuel", "油费与过路费",
                List.of("油费", "过路费", "加油", "高速收费"),
                "高速过路费约 0.4~0.6 元/公里；油耗按车型约 7~9L/百公里，油价约 7.5~8.5 元/L；"
                    + "山区加油站间隔较远，建议油量低于 1/3 就找加油站补充。"),

            doc("mountain-road", "山区道路驾驶",
                List.of("山路", "弯道", "落石", "盘山"),
                "山区弯道减速鸣笛、不越线超车；落石多发路段快速通过不停留；"
                    + "长下坡用发动机制动（低挡位）避免刹车过热；夜间山路尽量不走。"),

            doc("weather-road", "天气与封路",
                List.of("封路", "管制", "冰雪", "暴雨", "大雾"),
                "出发前查询沿途天气与交通管制信息；雨雪/大雾天减速慢行并开启雾灯；"
                    + "如遇封路听从交警指挥，通过导航实时路况或当地公众号获取最新信息。"));
    }

    private static RagDocument doc(String id, String title, List<String> keywords, String content) {
        return new RagDocument(id, title, keywords, content);
    }
}
