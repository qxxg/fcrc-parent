# 05 · 教学指南

> 这份文档不是“功能说明”，而是一条**可动手的阅读与实验路线**。目标是把两套技术真正讲透：
>
> - **Spring AI Alibaba Graph**：如何把审批流程写成可暂停、可恢复、可审计的状态图；
> - **AgentScope Java**：如何把模型、工具、权限、记忆、工作区、子智能体组装成一个有边界的智能体。

---

## 1. 学习目标

完成本指南后，你应该能回答：

1. Graph 的 `StateGraph`、节点、边、条件边、状态键策略分别解决什么问题？
2. 为什么人工复核要用 `interruptBefore` + checkpoint，而不是阻塞线程？
3. 服务重启后，工作流为什么还能从暂停点恢复？
4. Agent 的 `Model`、`Toolkit`、`PermissionContext`、`Middleware`、`StateStore`、`Workspace` 各自负责什么？
5. `Msg` 与 `AgentEvent` 有什么区别？`Channel` 在架构中处于什么位置？
6. 如何保证不同用户、不同会话的上下文不串？
7. 如何让 AI“能查、能算、能建议，但不能审批”？
8. 记忆、上下文压缩、技能、Plan Mode、子智能体分别什么时候用？
9. 沙箱和权限白名单是什么关系？为什么有了沙箱仍需要权限？
10. 这套教学代码距离生产还差什么？

---

## 2. 推荐阅读顺序

| 顺序 | 文件 | 重点 |
| --- | --- | --- |
| 1 | `README.md` | 全局视图、启动方式 |
| 2 | `docs/02-requirements.md` | 业务规则与验收标准 |
| 3 | `docs/01-architecture.md` | 模块边界、时序、数据模型 |
| 4 | `fcrc-common` | 统一响应、错误码、领域契约 |
| 5 | `fcrc-customer` | 最典型的 CRUD + 脱敏 + 内部接口 |
| 6 | `fcrc-risk` / `fcrc-compliance` | 确定性规则引擎，理解“AI 不负责打分” |
| 7 | `fcrc-workflow` | **Graph 主战场** |
| 8 | `fcrc-agent` | **AgentScope 主战场** |
| 9 | `fcrc-gateway` + `static/` | 路由与驾驶舱 |
| 10 | `deploy/` | 从本机到服务器 |

建议边读边跑：每读完一个模块，执行对应测试，观察日志与 API。

---

## 3. 代码地图

```text
fcrc/
├── fcrc-common/                 统一契约：ApiResponse、PageResult、ErrorCode、领域 DTO
├── fcrc-service-parent/         业务服务共享依赖
├── fcrc-gateway/                网关 + 静态驾驶舱
│   └── src/main/resources/static/{index.html,app.js,styles.css}
├── fcrc-customer/               客户主数据、KYC、内部画像
├── fcrc-risk/                   风险评分引擎
├── fcrc-compliance/             合规筛查 + 制裁名单匹配
├── fcrc-workflow/               Graph 编排、checkpoint、HITL、事件
│   ├── graph/OnboardingGraphConfiguration.java
│   ├── graph/WorkflowState.java
│   ├── checkpoint/JpaCheckpointSaver.java
│   └── service/WorkflowRunService.java
├── fcrc-agent/                  AgentScope 智能体
│   ├── config/FcrcAgentConfiguration.java
│   ├── config/AgentProperties.java
│   ├── model/MockFcrcChatModel.java
│   ├── tool/FcrcBusinessTools.java
│   ├── tool/FcrcKnowledgeTools.java
│   ├── permission/FcrcPermissionRules.java
│   ├── middleware/FcrcGuardrailMiddleware.java
│   ├── workspace/WorkspaceInitializer.java
│   ├── subagent/SubagentConfiguration.java
│   └── service/AgentChatService.java
├── fcrc-case/                   合规案件与备注
├── fcrc-report/                 监管报告与 CSV
└── deploy/                      Docker、Nginx、MySQL 初始化、脚本
```

---

## 4. Spring AI Alibaba Graph 教学

### 4.1 一张图看懂

```text
START
  │
  ▼
load_customer
  ├──────────────┐
  ▼              ▼
risk_assess   compliance_screen      ← 并行
  └──────┬───────┘
         ▼
     aggregate                       ← 汇聚 + 决策
         │
   ┌─────┴─────┐
   ▼           ▼
auto_approve  manual_review          ← 条件边；manual_review 前中断
   │           │
   ▼           ├── APPROVED ──► finalize_approval ──► END
   │           └── REJECTED ──► finalize_rejection ─► END
   └──────────────────────────► finalize_approval ──► END
```

代码入口：`OnboardingGraphConfiguration.java`。

### 4.2 StateGraph 的四个核心概念

| 概念 | 在本项目中的体现 | 教学点 |
| --- | --- | --- |
| **State** | `WorkflowState` 的键 + `OverAllState` | 状态是 `Map<String,Object>`，但键必须集中定义 |
| **Node** | `load_customer`、`risk_assess` 等 8 个节点 | 节点是纯函数：读状态 → 返回增量更新 |
| **Edge** | `addEdge`、`addConditionalEdges` | 普通边固定流向，条件边根据状态路由 |
| **Compile** | `graph.compile(compileConfig)` | 编译后才可执行；checkpoint、中断都在编译配置里 |

### 4.3 状态键策略：最容易被忽略的坑

`WorkflowState.keyStrategyFactory()` 为每个键注册合并策略：

```java
strategies.put(key, KeyStrategy.REPLACE);        // 大多数键：新值覆盖旧值
strategies.put(AUDIT_TRAIL, KeyStrategy.APPEND); // 审计轨迹：追加，不覆盖
```

两个关键点：

1. **未注册的键会被忽略**。节点返回了值但下游读不到，先检查是否注册。
2. **并行节点不能依赖彼此的输出**。`compliance_screen` 故意传 `riskLevel=null`，因为它和 `risk_assess` 同时运行，此时风险等级还不存在。

> 这是并行图最常见的 bug：以为节点按代码顺序执行，实际上并行分支彼此不可见。

### 4.4 并行扇出与扇入

```java
graph.addEdge(LOAD_CUSTOMER, List.of(RISK_ASSESS, COMPLIANCE_SCREEN));
graph.addEdge(List.of(RISK_ASSESS, COMPLIANCE_SCREEN), AGGREGATE);
```

- 扇出：一个节点完成后，同时触发两个分支；
- 扇入：两个分支都完成后，才进入 `aggregate`；
- 好处：风险与合规互不依赖，缩短总耗时；
- 代价：状态合并、异常传播、超时控制更复杂。

### 4.5 条件边与决策分离

`aggregate` 只写 `preliminaryDecision` 与 `decisionReason`，不直接改客户状态：

```java
boolean auto = "PASS".equalsIgnoreCase(complianceResult)
        && score < 40
        && (profile == null || !profile.pep());
```

然后：

```java
graph.addConditionalEdges(AGGREGATE, asyncEdge(state ->
        "PASS".equals(...) ? AUTO_APPROVE : MANUAL_REVIEW));
```

为什么把“决策”和“执行”分开？

- 决策是纯状态计算，容易测试；
- 执行涉及远程调用（建案件、改客户、生成报告），可能失败；
- 分开后，失败重试的幂等边界更清晰。

### 4.6 人工中断：`interruptBefore`

```java
CompileConfig.builder()
    .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
    .interruptBefore(MANUAL_REVIEW)
    .build();
```

执行到 `manual_review` 前：

1. 当前状态被 `JpaCheckpointSaver` 写入 `workflow_checkpoints`；
2. `graph.invoke()` 返回；
3. `WorkflowRunService` 把运行状态置 `WAITING_REVIEW`；
4. 创建案件、回写客户状态、记录事件；
5. 调用结束，**线程释放**。

恢复：

```java
RunnableConfig config = RunnableConfig.builder()
        .threadId(runNo)
        .resume()
        .build();
graph.invoke(inputs, config);
```

`resume()` 是关键：没有它，图会从 `START` 重跑，已经完成的节点会重复执行。

### 4.7 Checkpoint 持久化

`JpaCheckpointSaver` 实现 `BaseCheckpointSaver`：

| 方法 | 作用 |
| --- | --- |
| `put` | 保存状态快照 + 下一个节点 |
| `get` | 按 threadId + checkpointId 读取 |
| `getTuple` | 读取最新检查点及元数据 |
| `list` | 列出某 threadId 的所有检查点 |
| `release` | 清理 threadId 的检查点 |

表结构：`workflow_checkpoints(thread_id, checkpoint_id, node_id, next_node_id, state_data, created_at)`。

教学实验：暂停后重启工作流服务，再提交复核——仍然成功，因为状态不在内存里。

### 4.8 事件与 SSE

每个节点都被 `tracked()` 包装：

```java
recorder.nodeStarted(runNo, node, label + "开始");
...
recorder.nodeCompleted(runNo, node, label + "完成", payload);
```

好处：

- 节点实现只关心业务；
- 事件格式统一；
- 新节点自动获得审计能力。

`WorkflowStreamService` 轮询事件表并推送 SSE：

- 先推 `snapshot`，前端立即渲染；
- 再按 id 增量推 `workflow-event`；
- 终态推 `done` 并关闭。

为什么不用“节点里直接操作 emitter”？

1. 节点可能跑在并行线程/未来独立 worker；
2. 事件已落库，新页面能补齐历史；
3. SSE 只是传输层，业务事件仍由数据库驱动。

### 4.9 Graph 实验

#### 实验 G1：查看图结构

```bash
curl http://localhost:8080/api/workflows/graph
```

把返回的 `mermaid` 粘贴到支持 Mermaid 的编辑器。观察节点与条件分支是否与代码一致。

#### 实验 G2：低风险自动通过

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"transactionAmount":80000,"transactionType":"DEPOSIT","crossBorder":false,"recentTransactionCount":2,"newRelationship":false,"highRiskProduct":false,"sourceOfFunds":"工资","businessPurpose":"储蓄","identityDocumentVerified":true,"beneficialOwnerVerified":false,"adverseMediaFlag":false}'
```

预期：`status=COMPLETED`，`currentNode=finalize_approval`，生成报告。

#### 实验 G3：高风险暂停与恢复

用客户 3（PEP）发起，金额大、跨境、负面舆情：

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{"customerId":3,"transactionAmount":2000000,"transactionType":"TRANSFER","crossBorder":true,"recentTransactionCount":40,"newRelationship":true,"highRiskProduct":true,"sourceOfFunds":"","businessPurpose":"跨境投资","identityDocumentVerified":true,"beneficialOwnerVerified":true,"adverseMediaFlag":true}'
```

预期：`WAITING_REVIEW`、`canResume=true`、生成 `caseNo`。

此时**重启 `fcrc-workflow`**，再查详情，仍为 `WAITING_REVIEW`。提交复核：

```bash
curl -X POST http://localhost:8080/api/workflows/<runNo>/review \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","reviewer":"teacher","comment":"课堂演示"}'
```

预期：`COMPLETED`，客户状态 `COMPLETED`，报告生成。

再次提交同一 runNo，预期 `40902`。

#### 实验 G4：观察事件时间线

```bash
curl http://localhost:8080/api/workflows/<runNo>/events
curl -N http://localhost:8080/api/workflows/<runNo>/events/stream
```

观察 `WORKFLOW_STARTED`、`NODE_COMPLETED`、`BRANCH_DECISION`、`HUMAN_REVIEW_REQUIRED`、`HUMAN_REVIEW_SUBMITTED`、`WORKFLOW_COMPLETED`。

#### 实验 G5：改规则做对比

把 `aggregate` 里的自动审批条件从 `score < 40` 改成 `score < 20`，重跑客户 1，观察结果从自动通过变为人工复核。

思考：规则变了，历史报告是否应该重算？为什么不能？

---

## 5. AgentScope Java 教学

### 5.1 总装图

```text
HarnessAgent
├── Model                 MockFcrcChatModel / DashScopeChatModel
├── Toolkit               FcrcBusinessTools + FcrcKnowledgeTools + Harness 内置工具
├── PermissionContext     默认拒绝 + 显式白名单
├── Middleware            FcrcGuardrailMiddleware（护栏 + 审计）
├── AgentStateStore       JsonFileAgentStateStore
├── Workspace             AGENTS.md / knowledge / skills / plans / memory
├── MemoryConfig          长期记忆
├── CompactionConfig      上下文压缩
├── Subagent              report-writer
└── Plan Mode             只读调研
        │
        ▼
GatewayBootstrap → ChatUiChannel → AgentController → 前端
```

代码入口：`FcrcAgentConfiguration.java`。

### 5.2 Harness：不是“又一个 Agent 类”

`HarnessAgent` 可以理解为**运行时外壳**：它把模型、工具、权限、状态、工作区、中间件、记忆、子智能体组合成一个可长期运行的智能体。

教学点：

- 不要把所有逻辑塞进一个 `Agent` 子类；
- 用 Builder 显式声明能力，代码即架构图；
- 能力开关集中在 `AgentProperties`，避免魔法值；
- 关闭不需要的能力（shell/web）比“事后拦截”更安全。

### 5.3 Model：模型只是“决策器”

| 模式 | 类 | 用途 |
| --- | --- | --- |
| `mock` | `MockFcrcChatModel` | 无 Key、无网络，确定性演示与测试 |
| `dashscope` | `DashScopeChatModel` | 真实 qwen，需要 `DASHSCOPE_API_KEY` |

Mock 模型的工作方式：

1. 最后一条消息是 `USER`：按关键词选择工具；
2. 最后一条消息是 `TOOL`：把结果整理成中文摘要；
3. 维护类提示（memory/compaction/summarize）：直接返回文本，不调工具；
4. 没有命中：返回帮助文本。

关键词映射（按优先级）：

| 关键词 | 工具 |
| --- | --- |
| 工作流 / 开户 / onboarding | `start_onboarding_workflow`（有数字 ID）或 `list_workflow_runs` |
| 合规 / 筛查 / 制裁 / 洗钱 / AML | `screen_customer_compliance` |
| 风险 / 评分 / risk | `assess_customer_risk` |
| 案件 / case | `list_cases` |
| 报告 / report | `list_reports` |
| 制度 / 知识 / PEP / 受益所有人 / 高风险国家 / 可疑交易 / 政策 | `search_fcrc_knowledge` |
| 客户 / customer | `get_customer_profile`（有数字 ID）或 `list_customers` |

教学点：

- 模型不直接访问数据库，工具才是“手”；
- 工具描述就是“提示词的一部分”，写清楚参数和边界；
- Mock 模型不是生产方案，但它让 ReAct 循环、权限、事件、状态全链路可测试。

### 5.4 Messages 与 Events：两个不同层次

| 概念 | 类型 | 作用 |
| --- | --- | --- |
| **Msg** | `io.agentscope.core.message.Msg` | 对话内容：角色（USER/ASSISTANT/TOOL/SYSTEM）+ 内容块 |
| **ContentBlock** | `TextBlock` / `ToolUseBlock` 等 | 一条消息可以由文本、工具调用等多块组成 |
| **AgentEvent** | `io.agentscope.core.event.AgentEvent` | 运行时事件：开始、模型调用、工具调用、工具结果、结束 |

`ChatUiChannel.send()` 返回 `Msg`，适合同步问答；`sendStream()` 返回 `Flux<AgentEvent>`，适合前端实时展示。

教学点：

- `Msg` 是“说了什么”；
- `AgentEvent` 是“正在做什么”；
- 把两者混为一谈，会导致前端不知道该渲染消息还是状态。

### 5.5 Channel：消息入口与传输抽象

```java
ChatUiChannel channel = gatewayBootstrap.chatUiChannel();
channel.send(SendOptions.of(userId, sessionId), message);
```

`ChatUiChannel` 负责：

- 把用户消息路由到指定 Agent；
- 维护会话；
- 返回同步结果或事件流。

`SendOptions.of(user, session)` 是会话隔离的关键。Controller 不需要知道 ReAct 循环、工具调用、状态持久化的细节。

教学点：

- Channel 是“适配器”，不是业务逻辑；
- 换成 WebSocket、Slack、钉钉，只需要换 Channel；
- 业务 API 不应直接依赖某个 UI 框架。

### 5.6 Session 与 AgentState：上下文到底存在哪

```java
@Bean
public AgentStateStore agentStateStore(AgentProperties properties) {
    return new JsonFileAgentStateStore(stateDir);
}
```

- 键：`(userId, sessionId)`；
- 值：消息历史、Plan Mode、权限模式等状态；
- 存储：`FCRC_AGENT_STATE` 指向的目录，默认 `./data/agent-state`；
- 重启后仍可恢复。

会话隔离实验：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"客户 1 的风险如何？","userId":"alice","sessionId":"s1"}'

curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"我刚才问了什么？","userId":"bob","sessionId":"s1"}'
```

Bob 不应该知道 Alice 的上下文。

### 5.7 Tools：工具就是 AI 的手

```java
@Tool(name = "assess_customer_risk",
      description = "对指定客户做风险评分……",
      readOnly = true)
public String assessCustomerRisk(
        @ToolParam(name = "customer_id", description = "客户主键 ID") Long customerId,
        ...) { ... }
```

设计原则：

1. **名字清晰**：动词 + 对象，模型才能选对；
2. **描述完整**：什么时候用、参数含义、返回什么；
3. **返回 JSON 字符串**：模型易读，前端易解析；
4. **异常转结构化错误**：`{"error":"..."}`，不要中断整轮对话；
5. **只读优先**：除启动工作流外，全部 `readOnly=true`；
6. **不注册审批工具**：这是合规红线。

工具执行链路：

```text
模型决定调用
  → 权限引擎检查
  → FcrcBusinessTools 方法
  → AgentDownstreamClient HTTP
  → 业务服务
  → JSON 结果回填模型
```

### 5.8 Permissions：默认拒绝，而不是默认允许

```java
PermissionContextState.builder()
    .mode(PermissionMode.DEFAULT)
    .addAllowRule(...)
    .addDenyRule(...)
    .build();
```

四层防护：

1. 构建期 `disableShellTool()` / `disableWebTools()`；
2. 权限白名单；
3. 业务 API 边界（不能直连数据库）；
4. 人工复核接口不在工具集中。

教学点：

- 权限不是“提示词里写一句不要做”；
- 权限是运行时代码检查；
- 最安全的能力是“根本不注册”。

### 5.9 Middleware：洋葱模型

`FcrcGuardrailMiddleware` 实现四个钩子：

| 钩子 | 时机 | 本项目用途 |
| --- | --- | --- |
| `onSystemPrompt` | 组装系统提示 | 追加合规红线 |
| `onAgent` | 整轮调用前后 | 记录用户、会话、耗时、事件数 |
| `onActing` | 工具执行前后 | 记录工具名（不记录 PII） |
| `onReasoning` | 模型推理前后 | 预留（可接内容审核） |

教学点：

- 横切关注点不要散落在业务代码；
- 中间件可以组合，顺序由 `order()` 控制；
- 日志只记录必要标识，不记录敏感数据。

### 5.10 Workspace：Agent 的办公桌

```text
data/agent-workspace/
├── AGENTS.md                   角色、边界、输出规范
├── MEMORY.md                   长期记忆摘要
├── README.md                   工作区说明
├── knowledge/                  制度库
│   ├── KNOWLEDGE.md
│   ├── high-risk-countries.md
│   ├── pep-due-diligence.md
│   └── suspicious-transaction.md
├── skills/
│   ├── kyc-review/SKILL.md
│   └── sanctions-escalation/SKILL.md
├── plans/                      Plan Mode 输出
└── memory/                     记忆账本
```

初始化是幂等的：已存在文件不覆盖。这样重启不会覆盖人工维护的制度。

教学点：

- 制度更新不需要重新训练模型；
- 检索结果可引用文件路径，答案可追溯；
- 工作区是“可控的文件边界”，不是整个服务器。

### 5.11 Memory 与 Compaction：上下文管理

| 能力 | 配置 | 作用 |
| --- | --- | --- |
| 长期记忆 | `MemoryConfig` | 跨会话保留稳定偏好/结论 |
| 上下文压缩 | `CompactionConfig(triggerMessages=40, keepMessages=12)` | 长对话时压缩历史，避免窗口溢出 |
| 会话隔离 | `(userId, sessionId)` | 不同用户/会话不共享 |
| 工作区记忆 | `MEMORY.md` + `memory/` | 可读、可审计的长期信息 |

教学点：

- 上下文不是“越多越好”，窗口有限且成本高；
- 压缩会丢失细节，重要结论应写入记忆或业务表；
- 记忆也可能被污染，生产需要审核与过期策略。

### 5.12 Filesystem 与 Sandbox

| 能力 | 默认 | 说明 |
| --- | --- | --- |
| 只读文件 | 允许 | `read_file`、`list_files`、`glob_files`、`grep_files` |
| 写文件 | 拒绝 | `write_file`、`edit_file` 在黑名单 |
| Shell | 不注册 | `disableShellTool()` |
| Docker 沙箱 | 关闭 | `FCRC_AGENT_SANDBOX=true` 时启用 `DockerFilesystemSpec` |

教学点：

- 沙箱解决“文件系统隔离”，不解决“权限越权”；
- 即使有沙箱，也不应该给 AI 写生产文件的能力；
- 沙箱需要 Docker，演示环境默认关闭，保证零依赖可跑。

### 5.13 Skills：可复用的工作方法

`skills/kyc-review/SKILL.md`：

```markdown
---
name: kyc-review
description: 对单个客户执行 KYC 完整性检查，输出缺失项、风险提示与下一步动作。
---

# KYC 复核技能
...
```

加载方式：`load_skill_through_path`。

教学点：

- 技能是“按需加载的提示词 + 流程”；
- 比把所有规则塞进系统提示更可维护；
- 技能也可能过时，需要版本与评审。

### 5.14 Subagent：分工，而不是复制

`report-writer` 子智能体：

```java
SubagentDeclaration.builder()
    .name("report-writer")
    .tools(List.of("list_customers", "get_customer_profile", ...))
    .persistSession(true)
    .inheritParentPermissions(true)
    .steps(8)
    .build();
```

用途：把“写合规报告”这种长文本、结构化任务从主对话中拆出去。

好处：

1. 独立上下文，不污染主对话；
2. 工具更窄；
3. 会话可持久化；
4. 继承父权限，不能越权。

### 5.15 Plan Mode：先调研，后执行

| 端点 | 作用 |
| --- | --- |
| `POST /api/agent/plan/enter` | 进入只读调研模式 |
| `POST /api/agent/plan/exit` | 退出 |
| `GET /api/agent/plan/status` | 查询状态 |

Plan Mode 下：

- 允许读文件、检索、分析；
- 允许 `plan_write` 写计划文件；
- 不允许执行变更操作；
- 退出后可进入执行阶段。

教学点：

- “先计划再执行”能显著降低误操作；
- 计划文件本身是审计材料；
- Plan Mode 不是权限系统，仍需白名单兜底。

### 5.16 AgentScope 实验

#### 实验 A1：能力清单

```bash
curl http://localhost:8080/api/agent/capabilities
```

检查：

- `modelMode=mock`；
- `deniedTools` 包含 `execute`、`write_file`、`web_search`；
- `humanInTheLoop` 明确说明真人复核。

#### 实验 A2：同步对话与工具调用

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"评估客户 1 的风险，金额 150 万，跨境","userId":"teacher","sessionId":"lab"}'
```

预期：Mock 模型选择 `assess_customer_risk`，返回风险分与因子。

#### 实验 A3：流式事件

```bash
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"查一下客户 3 的合规筛查","userId":"teacher","sessionId":"lab"}'
```

观察 `agent-event` 中的 `TOOL_CALL_*` 与 `TOOL_RESULT_*`。

#### 实验 A4：会话隔离

用不同 `userId` 问“我刚才问了什么？”，确认上下文不串。

#### 实验 A5：知识库检索

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"PEP 的尽职调查要求是什么？","userId":"teacher","sessionId":"lab"}'
```

预期：调用 `search_fcrc_knowledge`，返回 `knowledge/pep-due-diligence.md` 片段。

修改 `data/agent-workspace/knowledge/pep-due-diligence.md`，重启后再问，观察答案变化。注意：已存在文件不会被初始化器覆盖。

#### 实验 A6：会话管理

```bash
curl "http://localhost:8080/api/agent/sessions?userId=teacher"
curl -X POST "http://localhost:8080/api/agent/sessions/lab/clear?userId=teacher"
curl -X DELETE "http://localhost:8080/api/agent/sessions/lab?userId=teacher"
```

观察清空上下文与删除会话的区别。

#### 实验 A7：Plan Mode

```bash
curl -X POST "http://localhost:8080/api/agent/plan/enter?userId=teacher&sessionId=lab"
curl "http://localhost:8080/api/agent/plan/status?userId=teacher&sessionId=lab"
curl -X POST "http://localhost:8080/api/agent/plan/exit?userId=teacher&sessionId=lab"
```

#### 实验 A8：切换真实模型

```bash
export FCRC_AGENT_MODEL=dashscope
export DASHSCOPE_API_KEY=sk-xxxx
export FCRC_DASHSCOPE_MODEL=qwen-plus
mvn -q -pl fcrc-agent spring-boot:run
```

观察真实模型的工具选择、自然语言质量与延迟差异。

#### 实验 A9：权限拒绝

用真实模型要求“执行 shell 删除文件”或“写一个文件到 /tmp”。预期：模型要么拒绝，要么调用被权限引擎拦截。检查日志中的拒绝记录与 `capabilities` 的 `deniedTools`。

#### 实验 A10：子智能体

要求：“把客户 3 的风险、合规、案件整理成一份合规报告草稿。”

观察主智能体是否委派给 `report-writer`，以及子智能体的工具范围是否更窄。

---

## 6. 一次请求的完整生命周期

### 6.1 工作流请求

```text
浏览器
  → Nginx / 网关
  → WorkflowController.start
  → WorkflowRunService.start
      → 创建 run/threadId
      → graph.invoke(inputs)
          → load_customer → CustomerClient（内部画像）
          → 并行 risk_assess / compliance_screen
          → aggregate（决策）
          → auto_approve 或 创建案件 + 暂停
      → 同步运行表
      → 记录事件
  → 返回 WorkflowRunDetail
```

暂停后：

```text
真人 POST /review
  → 校验 WAITING_REVIEW
  → 记录复核人/意见
  → RunnableConfig.resume()
  → checkpoint 恢复
  → finalize_approval / finalize_rejection
  → 生成报告 + 回写客户
  → 运行表 COMPLETED / REJECTED
```

### 6.2 AI 对话请求

```text
浏览器
  → AgentController.chat/stream
  → AgentChatService
      → ChatUiChannel.send/sendStream(SendOptions.of(user, session))
          → HarnessAgent
              → Model 决策
              → Permission 检查
              → Tool 执行
                  → AgentDownstreamClient
                  → 业务服务 HTTP
              → 工具结果回填
              → 生成最终回答
      → Msg（同步）或 AgentEvent Flux（流式）
  → JSON / SSE 返回
```

关键边界：Agent **不直接访问数据库**，所有业务数据都经过业务 API。

---

## 7. 调试与常见问题

| 现象 | 可能原因 | 排查 |
| --- | --- | --- |
| Mock 模型只返回帮助文本 | 关键词未命中，或没有可用工具 | 换用“客户 1 的风险”等明确说法；检查 `capabilities` |
| 工具返回 `{"error":...}` | 下游服务未启动/端口不对 | 检查 `fcrc.clients.*` 与健康检查 |
| 会话上下文串了 | `userId`/`sessionId` 传错 | 检查 `SendOptions.of` 与前端参数 |
| SSE 没有事件 | 网关缓冲/超时 | 检查 Nginx `proxy_buffering off`、`proxy_read_timeout` |
| 工作流恢复失败 | checkpoint 不存在/状态不是 WAITING_REVIEW | 查 `workflow_checkpoints` 与运行状态 |
| 重复复核报 409 | 已提交过 | 正常行为，查事件时间线 |
| 启动时数据重复 | H2 文件残留 | 删除对应模块 `./data` 后重启 |
| 中文乱码 | 终端编码问题 | 文件是 UTF-8；用 `-Encoding utf8` 读取 |
| 端口冲突 | 之前的 Java 进程未退出 | 停止旧进程或改 `SERVER_PORT` |
| Docker 健康检查失败 | 镜像无 curl / 启动慢 | 查看 `docker compose logs`，调大 `start_period` |

### 7.1 Mock 模型为什么“不智能”

它是**确定性规则模型**，不是大语言模型。它存在的目的是让完整链路在无 Key、无网络时可测试：

- ReAct 循环；
- 工具选择；
- 权限检查；
- 事件流；
- 会话持久化。

真实效果请切换 `dashscope`。

### 7.2 为什么 Agent 的 `agentId` 和显示名不同

`HarnessAgent.getAgentId()` 返回内部 UUID；配置的展示名用 `getName()`。`capabilities` 同时返回配置值，避免混淆。

---

## 8. 课程设计建议

### 8.1 一次 90 分钟课程

| 时间 | 内容 |
| --- | --- |
| 0–10 min | 业务背景：KYC、AML、PEP、人工复核 |
| 10–25 min | 跑通低风险自动通过（实验 G2） |
| 25–45 min | 跑通高风险暂停与恢复（实验 G3/G4） |
| 45–65 min | Agent 能力清单、工具调用、流式事件（A1–A3） |
| 65–80 min | 权限、注入、HITL 边界（A9 + 文档 04） |
| 80–90 min | 改造练习与讨论 |

### 8.2 改造练习

1. 新增节点 `check_blacklist`，在合规筛查后执行；
2. 把自动审批阈值外置到 Nacos 配置；
3. 新增 `get_customer_risk_history` 工具；
4. 为报告增加 PDF 导出；
5. 给 Agent 增加“只读 SQL”工具，并讨论为什么危险；
6. 把 checkpoint 从 JPA 换成 Redis；
7. 为 `report-writer` 增加引用校验；
8. 把人工复核改成双人复核（四眼原则）。

### 8.3 讨论题

- 为什么风险评分不用大模型？
- 如果模型坚持说“已批准”，系统如何兜底？
- 记忆应该保存多久？如何删除？
- 子智能体失败时，主智能体应该如何恢复？
- 工作流和 Agent 的边界应该画在哪里？
- 如果监管要求“所有 AI 输出可解释”，你需要补什么？

---

## 9. 从教学到生产

| 主题 | 教学实现 | 生产建议 |
| --- | --- | --- |
| 认证 | 前端传 `userId` | OIDC/SSO + 网关注入身份 |
| 授权 | 无 RBAC | 角色/属性权限，复核人角色校验 |
| 模型 | 默认 Mock | 真实模型 + 评测 + 内容审核 |
| 记忆 | JSON 文件 | 加密存储、租户隔离、过期策略 |
| Checkpoint | MySQL | Redis/PG/对象存储 + 高可用 |
| 事件 | 轮询表 | Kafka/Redis PubSub |
| 名单 | 内置 CSV | 权威供应商 + 定期更新 |
| 报告 | 同步生成 | 异步任务 + 状态机 + 电子签 |
| 观测 | 日志 + Actuator | OTel + Prometheus + Grafana |
| 部署 | 单机 Compose | K8s + 多副本 + 滚动发布 |

---

## 10. 一句话总结

**Graph 让流程“有状态、可暂停、可恢复、可审计”；AgentScope 让 AI“有工具、有权限、有记忆、有边界”。两者结合，才是企业可用的合规智能体。**
