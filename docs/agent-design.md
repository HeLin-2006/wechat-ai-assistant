# 🤖 长任务 Agent 设计文档：智能出行规划师

> 设计目标：用户只输入**一句最终目标**，Agent 自主拆解 ≥3 个不同子任务、调用多种工具/技能/RAG、执行完整闭环，最终输出**一份完整成品**（而非零散问答）。

---

## 1. 场景概述

### 业务场景：智能出行规划师

用户输入一句话目标（例）：
- 「做一份上海三日游完整出行方案」
- 「帮我规划一次杭州周末两天一夜的行程」
- 「给我一份成都五天的深度游计划」

Agent 自主完成整套闭环：
1. **确定出行日期与节假日**（当前日期 + 节日查询）
2. **查询目的地天气**（逐日预报）
3. **查询日出日落**（安排行程时间段）
4. **估算预算**（交通/住宿/餐饮汇总计算）
5. **检索出行知识**（行李清单、订票注意、安全提示）
6. **生成行程海报**（配图）
7. **汇总成完整方案文档**（分章节成品）

### 为什么选这个场景

| 维度 | 符合性 |
|---|---|
| 一句话触发 | ✅ 「做一份上海三日游方案」即启动 |
| ≥3 个不同子任务 | ✅ 6 个子任务，跨 6 类能力 |
| 多工具 + Skill + RAG | ✅ 工具(天气/时间/坐标/日出/生图) + 技能(节日/计算) + RAG(旅行知识库) |
| 完整闭环成品 | ✅ 输出一份分章节的完整出行方案文档（+ 海报图） |

---

## 2. 用户视角（一句话输入 → 成品输出）

```
用户：「做一份上海三日游完整出行方案」
        │
        ▼
┌─────────────────────────────────────────────┐
│         Agent 自主规划与执行（无需用户分步）      │
└─────────────────────────────────────────────┘
        │
        ▼
📋 上海三日游完整出行方案（成品）
  一、出行日期与节假日
  二、每日天气与穿搭建议
  三、每日行程安排（含日出日落）
  四、预算明细（总计 X 元）
  五、行李清单与注意事项
  六、行程海报 🖼️
  七、温馨提示
```

---

## 3. 目标拆解（Agent 的自主计划）

Planner 把用户目标拆成如下计划（JSON 结构化，供 Executor 依赖驱动执行）：

```json
{
  "goal": "做一份上海三日游完整出行方案",
  "destination": "上海",
  "days": 3,
  "subtasks": [
    {"id": 1, "title": "确定当前日期与出行时段节假日",
     "capability": "TOOL+SKILL", "action": "get_current_time + holiday_query", "dependsOn": []},
    {"id": 2, "title": "查询目的地三日天气",
     "capability": "TOOL", "action": "get_weather(city=上海, when=week)", "dependsOn": [1]},
    {"id": 3, "title": "查询日出日落用于行程时段安排",
     "capability": "TOOL_CHAIN", "action": "get_city_coordinates → get_sunrise_sunset", "dependsOn": [1]},
    {"id": 4, "title": "估算预算（交通+住宿+餐饮）",
     "capability": "SKILL", "action": "calculator 逐项计算并汇总", "dependsOn": [2]},
    {"id": 5, "title": "检索出行注意事项与行李清单",
     "capability": "RAG", "action": "travel-kb 关键词检索", "dependsOn": []},
    {"id": 6, "title": "根据天气生成行程穿搭建议",
     "capability": "LLM", "action": "结合子任务2结果生成建议", "dependsOn": [2]},
    {"id": 7, "title": "生成行程海报配图",
     "capability": "TOOL", "action": "generate_image(prompt=上海城市风光海报)", "dependsOn": [2]}
  ]
}
```

**依赖图**：`1 → (2, 3) → (4, 6, 7)`；`5` 独立并行。

---

## 4. 模块划分（架构）

```
service/agent/                        ★ 新增 Agent 层（编排器）
├── TravelAgentService.java           门面：goal → 成品文档
├── AgentPlanner.java                 LLM 拆解目标 → AgentPlan（JSON）
├── AgentExecutor.java                依赖驱动地执行每个子任务
├── AgentAssembler.java               汇总子任务结果 → 分章节成品
├── AgentPlan.java                    计划数据结构（record）
├── AgentPlanSubtask.java             子任务结构（id/title/capability/action/dependsOn）
└── AgentResult.java                  单步执行结果（subtaskId, ok, summary, data）
```

### 4.1 各模块职责

| 模块 | 职责 | 关键点 |
|---|---|---|
| **TravelAgentService** | 编排入口：Plan → Execute → Assemble | 全流程异常兜底；超时控制 |
| **AgentPlanner** | 把一句话目标转成结构化计划 | 用 LLM 输出 JSON；校验 ≥3 子任务；校验依赖无环 |
| **AgentExecutor** | 按依赖顺序执行子任务 | 按 capability 分发到 工具/Skill/RAG/LLM 四类执行器；单步失败不中断整体 |
| **AgentAssembler** | 汇总为完整成品 | 按固定章节模板拼装；缺数据的章节给占位说明 |
| **AgentPlan / Subtask / Result** | 数据结构 | record 不可变，便于测试与日志 |

### 4.2 四类能力执行器（复用已有组件）

```
AgentExecutor
  ├─ 工具执行器   → ToolRegistry.execute（复用：get_weather/get_current_time/
  │                  get_city_coordinates/get_sunrise_sunset/generate_image）
  ├─ 技能执行器   → SkillService.executeIfMatched（复用：holiday_query/calculator）
  ├─ RAG 执行器   → RagService.retrieve + buildEnhancedPrompt
  │                  （知识库扩展为旅行主题，见 §5）
  └─ LLM 建议器   → LlmClient.chat（生成穿搭建议、行程串联文案等）
```

### 4.3 复用与新增清单

| 组件 | 类型 | 说明 |
|---|---|---|
| `ToolRegistry` + 6 个 Tool | 复用 | 时间/天气/坐标/日出/生图 |
| `SkillService` + 2 个 Skill | 复用 | 节日查询/计算器 |
| `RagService` + 知识库 | 复用+扩展 | 新增 `TravelKnowledgeBase`（行李/订票/安全等 6+ 篇文档） |
| `LlmClient.chat` | 复用 | Planner 拆解 + Assembler 成文 |
| `AgentPlanner/Executor/Assembler` | **新增** | Agent 编排核心 |
| `TravelKnowledgeBase` | **新增** | 旅行主题 RAG 知识库 |

---

## 5. RAG 知识库扩展（旅行主题）

新增 `TravelKnowledgeBase`（接入现有 RagService，按关键词检索）：

| 文档 | 关键词 | 内容要点 |
|---|---|---|
| 行李清单 | 行李/带什么/准备 | 证件、充电宝、雨具、常用药… |
| 订票注意事项 | 订票/车票/机票/退改 | 提前订、实名制、退改规则… |
| 酒店预订 | 酒店/住宿/预订 | 位置选择、入住时间、评分… |
| 出行安全 | 安全/注意/防盗 | 财物、紧急电话、行程分享… |
| 景点预约 | 预约/门票/排队 | 热门景点需提前预约、限流… |
| 穿搭建议 | 穿搭/衣服/天气穿衣 | 按温度/降水推荐衣物 |

> 示例触发：「带什么行李」「订票要注意什么」→ 检索命中 → 增强 Prompt → LLM 生成具体建议章节。

---

## 6. 完整闭环流程

```
① 用户输入一句话目标
② AgentPlanner：LLM 生成结构化计划（≥3 子任务，带依赖）
③ AgentExecutor：按依赖拓扑序执行
    ├─ 步骤1 get_current_time + holiday_query ──→ 日期/节日
    ├─ 步骤2 get_weather(上海,3天) ───────────────→ 逐日天气
    ├─ 步骤3 get_city_coordinates → get_sunrise_sunset → 日出日落（链式）
    ├─ 步骤4 calculator 预算汇总 ────────────────→ 总预算
    ├─ 步骤5 RAG(旅行知识库) 检索 ────────────────→ 行李/注意
    ├─ 步骤6 LLM 依据天气生成穿搭/行程建议 ────────→ 建议文本
    └─ 步骤7 generate_image 生成海报 ─────────────→ 海报图片
④ AgentAssembler：按章节模板拼装全部结果
⑤ 输出成品：分章节 Markdown 文档（+ 海报图片发送给用户）
```

---

## 7. 成品格式（Agent 最终输出）

```
📋 【目的地】N 日游完整出行方案
  生成时间：xxxx-xx-xx（星期X）

一、出行日期与节假日
  · 出行日期：X月X日~X月X日（X天）
  · 期间节日：X（如国庆节，还有 N 天）；建议错峰提示

二、每日天气与穿搭建议
  · Day1（X月X日）：多云 26~32℃ → 建议：短袖+防晒+雨具
  · Day2 ……

三、每日行程安排（含日出日落）
  · 日出 05:22 / 日落 18:32
  · Day1 上午：… / 下午：… / 晚上：…（结合天气与日照时段）

四、预算明细
  · 交通：高铁往返 1100 元 + 市内 200 元
  · 住宿：2晚×500 = 1000 元
  · 餐饮：3天×150 = 450 元
  · 门票/其他：600 元
  · 总计：3350 元（calculator 校验）

五、行李清单与注意事项（RAG 增强）
  · 行李：身份证、充电宝、雨伞、常用药……
  · 注意：热门景点提前预约、财物安全……

六、行程海报
  · [generate_image 生成的图片，随文档发送]

七、温馨提示
  · 实时天气可能变化，出行前 1 天再次确认
```

---

## 8. 关键技术点与容错设计

| 设计点 | 方案 |
|---|---|
| **目标拆解结构化** | Planner 用 LLM 输出严格 JSON（校验字段、≥3 子任务、依赖无环），失败重试 1 次 |
| **依赖驱动执行** | Executor 按拓扑序执行，可并行执行无依赖子任务（线程池） |
| **单步失败不中断** | 每步 try-catch，失败记为 `ok=false`，Assembler 输出"该环节暂不可用"，整体照常出成品 |
| **工具调用复用** | 链式子任务（坐标→日出）直接复用 ToolCallService 的多轮能力 |
| **RAG 增强** | 旅行知识库命中 → 增强 Prompt → LLM 输出具体建议 |
| **Skill 确定性** | 节日/计算用 Skill（快、免费、可校验），不占用 LLM |
| **轮数/时长上限** | 总执行时长上限（如 60s），超时返回已完成的章节 + 提示 |
| **成品一致性** | Assembler 用固定章节模板 + 各步骤 JSON 数据填充，保证结构稳定 |

---

## 9. 扩展性设计

| 扩展方向 | 做法 |
|---|---|
| 换场景（如「健身计划」「婚礼筹备」） | 新增对应 KnowledgeBase + 调整 Assembler 模板，Agent 骨架不变 |
| 加子任务类型 | 在 Executor 的 capability 分发里加一类执行器即可 |
| 深度决策（如"按预算推荐酒店"） | 子任务 action 指向 LLM 决策器 + RAG 数据 |

---

## 10. 与现有路由的衔接

Agent 作为**最高优先级意图**接入现有消息路由：

```
用户消息
 → ① Agent 目标识别？（出行规划类关键词：方案/计划/攻略/行程…）→ TravelAgentService
 → ② Skill 关键词 → Skill
 → ③ RAG 关键词 → 增强 LLM
 → ④ 意图识别（天气/生图/语音/指令）
 → ⑤ LLM 兜底闲聊
```

> 说明：Agent 是"长任务编排"，与"单轮工具调用"不同——它先规划再执行再汇总，一次输出完整成品。
