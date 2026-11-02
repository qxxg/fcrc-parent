# 03 · API 参考

## 1. 通用约定

### 1.1 基址

| 环境 | 访问入口 | 说明 |
| --- | --- | --- |
| 本地开发 | `http://localhost:8080` | 网关同时托管静态驾驶舱 |
| Docker | `http://<服务器IP>:8088` | Nginx 托管前端并把 `/api` 反代到网关 |
| 服务直连 | `http://<服务器IP>:8081` 等 | 仅用于排障；业务服务端口默认不对宿主暴露 |

所有业务接口都以 `/api` 开头。内部接口以 `/internal` 开头，不经过 Nginx，只允许内网服务直连。

### 1.2 统一响应

成功：

```json
{
  "success": true,
  "code": "0",
  "message": "OK",
  "data": { },
  "timestamp": "2026-11-02T02:30:00Z"
}
```

失败：

```json
{
  "success": false,
  "code": "40401",
  "message": "客户不存在: 999",
  "data": null,
  "timestamp": "2026-11-02T02:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 业务是否成功 |
| `code` | string | `0` 表示成功；其余见错误码表 |
| `message` | string | 面向用户的中文提示 |
| `data` | any | 业务负载，失败时为 `null` |
| `timestamp` | string | 服务端 UTC 时间，ISO-8601 |

### 1.3 分页

```json
{
  "items": [],
  "total": 0,
  "page": 0,
  "size": 20,
  "totalPages": 0
}
```

- `page` 从 0 开始；
- 各服务会把 `size` 限制在 `1..100`，防止一次拉取过多数据；
- 列表默认按创建时间倒序。

### 1.4 错误码与 HTTP 状态

| code | HTTP | 含义 |
| --- | --- | --- |
| `40001` | 400 | 参数校验失败 |
| `40101` | 401 | 未认证（当前版本预留） |
| `40301` | 403 | 无权限（当前版本预留） |
| `40401` | 404 | 资源不存在 |
| `40901` | 409 | 资源状态冲突 |
| `40902` | 409 | 当前状态不允许该操作 |
| `50201` | 502 | 下游服务调用失败 |
| `50000` | 500 | 系统内部错误 |

业务代码只允许使用 `ErrorCode` 枚举，不允许硬编码字符串错误码。

### 1.5 时间与脱敏

- 时间统一为 UTC `Instant`，Jackson 序列化为 ISO-8601 字符串；
- `/api/customers/**` 返回的姓名、证件号、手机号、邮箱均已脱敏；
- 需要原文 PII 的下游服务必须走 `/internal/customers/{id}/profile`，生产环境应加服务身份认证。

---

## 2. 客户服务 `fcrc-customer`

### 2.1 创建客户

`POST /api/customers`

```json
{
  "name": "赵敏",
  "customerType": "INDIVIDUAL",
  "idNumber": "110101199303031234",
  "phone": "13800009999",
  "email": "zhaomin@example.com",
  "country": "China",
  "industry": "科技",
  "annualIncome": 680000,
  "occupation": "产品经理",
  "pep": false,
  "channel": "WEB"
}
```

约束：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `name` | 是 | 最长 128 |
| `customerType` | 是 | `INDIVIDUAL` / `CORPORATE` |
| `country` | 是 | 最长 64 |
| `email` | 否 | 邮箱格式，最长 128 |
| `annualIncome` | 否 | `>= 0` |
| `channel` | 否 | `WEB` / `APP` / `BRANCH` / `PARTNER` |
| `idNumber` / `phone` | 否 | 分别最长 64 / 32 |

响应 `data` 为脱敏后的 `CustomerResponse`。系统字段 `customerNo`、`kycStatus`、`onboardingStatus`、`riskLevel` 由服务端生成，调用方不能注入。

### 2.2 查询客户列表

`GET /api/customers?keyword=张&status=APPROVED&page=0&size=20`

- `keyword`：客户号或姓名模糊匹配；
- `status`：同时匹配 KYC 状态与开户状态；
- 返回 `PageResult<CustomerResponse>`。

### 2.3 客户统计

`GET /api/customers/stats`

```json
{
  "total": 6,
  "pending": 1,
  "inReview": 2,
  "approved": 2,
  "rejected": 1,
  "pep": 2
}
```

### 2.4 客户详情

`GET /api/customers/{id}`

`id` 为客户主键（Long），不是 `customerNo`。

### 2.5 更新 KYC 状态

`PUT /api/customers/{id}/kyc-status`

```json
{
  "status": "APPROVED",
  "notes": "身份证明与地址证明已核验"
}
```

`status` 仅允许 `PENDING` / `IN_REVIEW` / `APPROVED` / `REJECTED`。

### 2.6 内部接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/internal/customers/{id}/profile` | 返回 `customerNo` + 完整 `CustomerProfile`，供风险/合规/工作流使用 |
| `PUT` | `/internal/customers/{id}/onboarding` | 回写开户状态、风险等级与备注 |

回写请求：

```json
{
  "onboardingStatus": "MANUAL_REVIEW",
  "riskLevel": "HIGH",
  "notes": "等待合规官复核"
}
```

`onboardingStatus` 仅允许 `NOT_STARTED` / `RUNNING` / `MANUAL_REVIEW` / `COMPLETED` / `REJECTED`。

> 安全边界：`/internal/**` 不应出现在公网网关路由中。工作流通过服务地址直连，不依赖网关。

---

## 3. 风险服务 `fcrc-risk`

### 3.1 风险评分

`POST /api/risk/assess`

```json
{
  "customerNo": "CUS-20261102-000001",
  "profile": {
    "name": "赵敏",
    "customerType": "INDIVIDUAL",
    "idNumber": "110101199303031234",
    "phone": "13800009999",
    "email": "zhaomin@example.com",
    "country": "China",
    "industry": "科技",
    "annualIncome": 680000,
    "occupation": "产品经理",
    "pep": false,
    "channel": "WEB"
  },
  "transactionAmount": 1500000,
  "transactionType": "TRANSFER",
  "crossBorder": true,
  "recentTransactionCount": 24,
  "newRelationship": true,
  "highRiskProduct": false
}
```

响应：

```json
{
  "assessmentNo": "RSK-20261102-000123",
  "customerNo": "CUS-20261102-000001",
  "score": 37,
  "level": "MEDIUM",
  "factors": [
    { "code": "LARGE_AMOUNT", "label": "大额交易", "points": 12, "description": "预计交易金额超过 100 万，触发增强监测" },
    { "code": "CROSS_BORDER", "label": "跨境交易", "points": 8, "description": "跨境资金流动需要额外的制裁与贸易背景筛查" },
    { "code": "BASE", "label": "基础风险", "points": 5, "description": "所有业务关系的基础风险权重" }
  ],
  "recommendation": "执行增强尽职调查，补充资金来源/用途证明，6 个月内复评。",
  "createdAt": "2026-11-02T02:30:00Z"
}
```

评分规则（确定性、可复现）：

| 因子 | 分值 |
| --- | --- |
| 基础风险 | +5 |
| FATF 高风险国家（伊朗、朝鲜、叙利亚、古巴） | +35 |
| 离岸/较高风险地区 | +20 |
| PEP | +25 |
| 现金密集型行业 | +12 |
| 高风险产品 | +15 |
| 金额 ≥ 500 万 / ≥ 100 万 / ≥ 20 万 | +20 / +12 / +5 |
| 跨境 | +8 |
| 近 30 天交易 ≥ 50 笔 / ≥ 20 笔 | +12 / +6 |
| 新关系且金额 ≥ 50 万 | +6 |
| 机构客户 / 境外机构 | +4 / +8 |

分级：`< 20 LOW`、`< 40 MEDIUM`、`< 60 HIGH`、`>= 60 CRITICAL`；总分封顶 100。

### 3.2 其他接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/risk/assessments?customerNo=&level=&page=&size=` | 分页查询评估记录 |
| `GET` | `/api/risk/assessments/{assessmentNo}` | 评估详情 |
| `GET` | `/api/risk/stats` | `total/low/medium/high/critical/averageScore` |

---

## 4. 合规服务 `fcrc-compliance`

### 4.1 合规筛查

`POST /api/compliance/screen`

```json
{
  "customerNo": "CUS-20261102-000001",
  "profile": {
    "name": "赵敏",
    "customerType": "INDIVIDUAL",
    "idNumber": "110101199303031234",
    "phone": "13800009999",
    "email": "zhaomin@example.com",
    "country": "China",
    "industry": "科技",
    "annualIncome": 680000,
    "occupation": "产品经理",
    "pep": false,
    "channel": "WEB"
  },
  "transactionAmount": 1500000,
  "transactionType": "TRANSFER",
  "crossBorder": true,
  "sourceOfFunds": "工资与投资收益",
  "businessPurpose": "跨境采购付款",
  "identityDocumentVerified": true,
  "beneficialOwnerVerified": true,
  "adverseMediaFlag": false,
  "riskLevel": "MEDIUM"
}
```

响应：

```json
{
  "screeningNo": "SCR-20261102-000456",
  "customerNo": "CUS-20261102-000001",
  "result": "PASS",
  "riskPoints": 5,
  "findings": [
    { "type": "AML", "severity": "INFO", "description": "跨境交易需额外核查制裁、出口管制与贸易背景。" }
  ],
  "recommendation": "可以继续办理；按客户风险等级执行持续监测。",
  "createdAt": "2026-11-02T02:30:00Z"
}
```

规则要点：

| 检查项 | 分值 |
| --- | --- |
| 制裁/观察名单命中 | +100（直接 FAIL） |
| 高风险国家/地区 | +30 |
| PEP | +25 |
| 负面舆情 | +25 |
| 身份证明未核验 | +20 |
| 机构客户受益所有人未核验 | +20 |
| 缺少资金来源 | +15 |
| 缺少业务目的 | +10 |
| 上游风险等级 HIGH / CRITICAL | +10 / +20 |
| 金额 ≥ 500 万 | +10 |
| 跨境 | +5 |

结论：名单命中或 `>= 60` → `FAIL`；`>= 25` → `REVIEW`；否则 `PASS`。总分封顶 100。

> 注意：`REVIEW` 表示“需要人工判断”，不是自动通过，也不是自动拒绝。

### 4.2 其他接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/compliance/screenings?customerNo=&result=&page=&size=` | 分页查询筛查记录 |
| `GET` | `/api/compliance/screenings/{screeningNo}` | 筛查详情 |
| `GET` | `/api/compliance/stats` | `total/pass/review/fail/watchlistEntries` |

---

## 5. 工作流服务 `fcrc-workflow`

### 5.1 发起开户编排

`POST /api/workflows`

```json
{
  "customerId": 1,
  "transactionAmount": 120000,
  "transactionType": "DEPOSIT",
  "crossBorder": false,
  "recentTransactionCount": 3,
  "newRelationship": false,
  "highRiskProduct": false,
  "sourceOfFunds": "工资收入",
  "businessPurpose": "日常储蓄",
  "identityDocumentVerified": true,
  "beneficialOwnerVerified": false,
  "adverseMediaFlag": false
}
```

约束：

- `customerId >= 1`；
- `transactionType`：`DEPOSIT` / `TRANSFER` / `INVESTMENT` / `TRADE_FINANCE`；
- `recentTransactionCount >= 0`；
- `sourceOfFunds` ≤ 200 字，`businessPurpose` ≤ 500 字；
- 客户画像由服务端从客户域读取，调用方不能自行上报身份资料。

响应 `data` 为 `WorkflowRunDetail`：

```json
{
  "run": {
    "runNo": "WF-20261102-000789",
    "customerId": 1,
    "customerNo": "CUS-DEMO-0001",
    "customerName": "张*",
    "status": "WAITING_REVIEW",
    "currentNode": "manual_review",
    "riskAssessmentNo": "RSK-20261102-000123",
    "riskScore": 37,
    "riskLevel": "MEDIUM",
    "riskFactors": ["LARGE_AMOUNT:12", "CROSS_BORDER:8"],
    "complianceScreeningNo": "SCR-20261102-000456",
    "complianceResult": "REVIEW",
    "complianceFindings": ["AML/WARN/缺少资金来源说明"],
    "preliminaryDecision": "MANUAL_REVIEW",
    "decision": null,
    "decisionReason": "合规结果 REVIEW 或风险分 37 >= 40 或客户为 PEP，转人工复核",
    "reviewDecision": null,
    "reviewer": null,
    "reviewComment": null,
    "caseNo": "CASE-20261102-000012",
    "reportNo": null,
    "onboardingStatus": "MANUAL_REVIEW",
    "auditTrail": ["load_customer", "risk_assess", "compliance_screen", "aggregate"],
    "canResume": true,
    "errorMessage": null,
    "createdAt": "2026-11-02T02:30:00Z",
    "updatedAt": "2026-11-02T02:30:03Z"
  },
  "events": [
    {
      "seq": 1,
      "type": "WORKFLOW_STARTED",
      "node": "load_customer",
      "message": "开户编排已创建，客户 ID=1",
      "payload": null,
      "createdAt": "2026-11-02T02:30:00Z"
    }
  ]
}
```

> 示例中 `decisionReason` 的文案以实际实现为准；只要不满足自动审批条件，就会创建案件并停在 `manual_review` 前。

运行状态：

| 状态 | 含义 |
| --- | --- |
| `RUNNING` | 正在执行 |
| `WAITING_REVIEW` | 已暂停，等待真人复核 |
| `COMPLETED` | 流程完成且通过 |
| `REJECTED` | 人工复核拒绝 |
| `FAILED` | 执行异常，查看事件时间线与 `errorMessage` |

`canResume=true` 仅表示当前状态允许复核，不代表 AI 可以调用复核接口。

### 5.2 运行列表与详情

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/workflows?keyword=&status=&page=&size=` | 按运行号/客户号/客户名模糊查询 |
| `GET` | `/api/workflows/stats` | `total/running/waitingReview/completed/rejected/failed` |
| `GET` | `/api/workflows/{runNo}` | 运行详情 + 事件时间线 |
| `GET` | `/api/workflows/{runNo}/events` | 仅事件列表 |

### 5.3 图结构

`GET /api/workflows/graph`

```json
{
  "mermaid": "---\ntitle: FCRC 开户审批流程\n---\nstateDiagram-v2\n..."
}
```

前端可直接把 `mermaid` 字段交给 Mermaid 渲染，用于教学展示“代码即流程”。

### 5.4 人工复核（HITL）

`POST /api/workflows/{runNo}/review`

```json
{
  "decision": "APPROVED",
  "reviewer": "compliance.li",
  "comment": "已核验资金来源与贸易背景，同意开户"
}
```

约束：

- 只有 `WAITING_REVIEW` 状态可提交，否则返回 `40902`；
- `decision` 仅允许 `APPROVED` / `REJECTED`；
- `reviewer` 必填，最长 64；生产环境应从登录态获取，而不是信任前端；
- 复核后从最新 checkpoint 恢复，不会重跑已完成的节点；
- 该接口**不是 AI 工具**，AI 无法调用。

### 5.5 事件流

`GET /api/workflows/{runNo}/events/stream`（`text/event-stream`）

```text
event: snapshot
data: {"runNo":"WF-20261102-000789","status":"WAITING_REVIEW"}

event: workflow-event
data: {"seq":6,"type":"HUMAN_REVIEW_REQUIRED","node":"manual_review","message":"流程已暂停，等待人工复核；案件号=CASE-...","payload":"{...}","createdAt":"..."}

event: done
data: {"runNo":"WF-20261102-000789","status":"COMPLETED"}
```

- 服务端轮询事件表并推送，前端断开后自动停止；
- 终止状态 `COMPLETED` / `REJECTED` / `FAILED` 会发送 `done` 并关闭连接；
- 超时时间 30 分钟。

---

## 6. 案件服务 `fcrc-case`

### 6.1 创建案件

`POST /api/cases`

```json
{
  "customerNo": "CUS-DEMO-0003",
  "customerName": "王*",
  "type": "KYC_REVIEW",
  "priority": "HIGH",
  "title": "PEP 客户强化尽职调查",
  "description": "客户为 PEP，需核实财富来源与业务目的",
  "sourceRef": "WF-20261102-000789"
}
```

- `type`：`KYC_REVIEW` / `SANCTIONS_HIT` / `ADVERSE_MEDIA` / `TRANSACTION_MONITORING` / `COMPLAINT`；
- `priority`：`LOW` / `MEDIUM` / `HIGH` / `CRITICAL`；
- 工作流转人工时会自动创建案件，通常不需要前端手工创建。

### 6.2 其他接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/cases?keyword=&status=&priority=&assignee=&page=&size=` | 分页查询 |
| `GET` | `/api/cases/stats` | 总数、各状态、逾期、CRITICAL 数量 |
| `GET` | `/api/cases/{caseNo}` | 案件详情 + 备注时间线 |
| `PUT` | `/api/cases/{caseNo}/assign` | `{"assignee":"compliance.li"}` |
| `PUT` | `/api/cases/{caseNo}/status` | `{"status":"RESOLVED","resolution":"已补充材料"}` |
| `POST` | `/api/cases/{caseNo}/notes` | `{"author":"compliance.li","content":"..."}` |

状态：`OPEN` / `ASSIGNED` / `IN_PROGRESS` / `PENDING_INFO` / `RESOLVED` / `CLOSED`。

规则：

- `RESOLVED` / `CLOSED` 必须填写 `resolution`；
- 备注只追加、不可修改；
- `overdue` 由服务端根据 `dueAt` 与当前状态实时计算。

---

## 7. 报告服务 `fcrc-report`

### 7.1 生成报告

`POST /api/reports`

```json
{
  "customerNo": "CUS-DEMO-0001",
  "workflowNo": "WF-20261102-000789",
  "reportType": "CUSTOMER_RISK",
  "period": "2026-11",
  "decision": "APPROVED",
  "riskScore": 18,
  "riskLevel": "LOW",
  "complianceResult": "PASS",
  "riskFactors": ["BASE:5", "ELEVATED_AMOUNT:5"],
  "complianceFindings": [],
  "narrative": "客户风险画像稳定，合规筛查无重大发现。"
}
```

- `reportType`：`CUSTOMER_RISK` / `SAR` / `CTR` / `PERIODIC_SUMMARY`；
- `decision`：`APPROVED` / `REJECTED` / `MANUAL_REVIEW`；
- `period`：`YYYY-MM`；
- `riskScore`：0–100；
- `riskFactors` / `complianceFindings` 最多 20 条。

### 7.2 其他接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/reports?customerNo=&reportType=&status=&period=&page=&size=` | 分页查询 |
| `GET` | `/api/reports/stats` | `total/draft/finalized/submitted/sar/ctr` |
| `GET` | `/api/reports/{reportNo}` | 报告详情 |
| `POST` | `/api/reports/{reportNo}/finalize` | 草稿 → 定稿 |
| `POST` | `/api/reports/{reportNo}/submit` | 定稿 → 已报送 |
| `GET` | `/api/reports/{reportNo}/csv` | 下载 CSV（UTF-8 BOM，Excel 可直接打开） |

状态机：

```text
DRAFT --finalize--> FINAL --submit--> SUBMITTED
```

- 未定稿不能报送；
- 报送后不能再次定稿/报送；
- CSV 响应头为 `Content-Disposition: attachment`。

---

## 8. AI 服务 `fcrc-agent`

### 8.1 同步对话

`POST /api/agent/chat`

```json
{
  "message": "评估客户 1 的风险，交易金额 150 万，跨境",
  "userId": "demo-user",
  "sessionId": "main"
}
```

响应：

```json
{
  "userId": "demo-user",
  "sessionId": "main",
  "reply": "客户 1 的风险分为 37（MEDIUM）……",
  "modelMode": "mock"
}
```

- `message` 必填，最长 4000；
- `userId` / `sessionId` 为空时分别回退到 `anonymous` / `main`；
- 生产环境 `userId` 必须从登录态解析，不能信任前端。

### 8.2 流式对话

`POST /api/agent/chat/stream`（`text/event-stream`）

```text
event: agent-event
data: {"type":"AGENT_START","..."}

event: agent-event
data: {"type":"TOOL_CALL_START","toolName":"get_customer_profile","..."}

event: agent-event
data: {"type":"TOOL_RESULT_END","..."}

event: agent-event
data: {"type":"AGENT_END","..."}

event: done
data: {"userId":"demo-user","sessionId":"main"}
```

失败时发送：

```text
event: error
data: {"message":"..."}
```

> `agent-event` 的完整字段由 AgentScope `AgentEvent` 序列化决定。前端应按 `type` 做容错展示，不要依赖固定字段全集。

### 8.3 能力与配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent/health` | `{"status":"UP","timeoutSeconds":180}` |
| `GET` | `/api/agent/capabilities` | 模型模式、工作区、工具白名单、拒绝列表、HITL 说明 |
| `GET` | `/api/agent/workspace` | 工作区路径与最多 200 个文件 |

`capabilities` 关键字段：

```json
{
  "agentId": "fcrc-copilot",
  "name": "FCRC 合规风控 Copilot",
  "modelMode": "mock",
  "model": "fcrc-mock-model",
  "workspace": ".../data/agent-workspace",
  "planModeEnabled": true,
  "memoryEnabled": true,
  "compactionEnabled": true,
  "skillsEnabled": true,
  "sandboxEnabled": false,
  "maxIters": 8,
  "businessTools": ["list_customers", "get_customer_profile", "..."],
  "deniedTools": ["execute", "write_file", "edit_file", "web_search", "web_fetch"],
  "registeredToolCount": 40,
  "humanInTheLoop": "工作流进入 MANUAL_REVIEW 后必须由真人调用 /api/workflows/{runNo}/review，AI 无此工具"
}
```

### 8.4 会话管理

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/agent/sessions` | `userId` | 列出该用户持久化会话 |
| `DELETE` | `/api/agent/sessions/{sessionId}` | `userId` | 删除会话与状态 |
| `POST` | `/api/agent/sessions/{sessionId}/clear` | `userId` | 清空上下文，保留会话记录 |
| `POST` | `/api/agent/interrupt` | `userId`、`sessionId` | 中断当前 ReAct 循环 |

### 8.5 Plan Mode 与权限模式

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/agent/plan/status` | `userId`、`sessionId` | 查询是否处于 Plan Mode |
| `POST` | `/api/agent/plan/enter` | 同上 | 进入只读调研模式 |
| `POST` | `/api/agent/plan/exit` | 同上 | 退出 Plan Mode |
| `GET` | `/api/agent/permission-mode` | 同上 | 查询当前权限模式 |
| `PUT` | `/api/agent/permission-mode` | 同上 + `mode` | 切换权限模式 |

权限模式取值由 AgentScope `PermissionMode` 决定（如 `DEFAULT`、`BYPASS` 等）。

> **教学演示接口，生产必须收紧**：允许前端切换 `BYPASS` 会让权限白名单失效。投产时应删除该端点或仅对管理员开放，并把模式切换写入审计。

### 8.6 完整调用示例

```bash
# 1. 健康检查
curl http://localhost:8080/api/agent/health

# 2. 查看能力清单
curl http://localhost:8080/api/agent/capabilities

# 3. 同步对话
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"客户 1 的风险如何？","userId":"demo-user","sessionId":"main"}'

# 4. 流式对话
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"查询客户 3 的合规筛查结果","userId":"demo-user","sessionId":"main"}'
```

---

## 9. 端到端示例

### 9.1 低风险客户自动通过

```bash
# 创建客户
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"测试客户","customerType":"INDIVIDUAL","country":"China","industry":"教育","annualIncome":300000,"pep":false,"channel":"WEB"}'

# 发起工作流（假设返回 id=7）
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{"customerId":7,"transactionAmount":80000,"transactionType":"DEPOSIT","crossBorder":false,"recentTransactionCount":2,"newRelationship":false,"highRiskProduct":false,"sourceOfFunds":"工资","businessPurpose":"储蓄","identityDocumentVerified":true,"beneficialOwnerVerified":false,"adverseMediaFlag":false}'
```

预期：合规 `PASS`、风险分 `< 40`、非 PEP → `COMPLETED`，生成报告。

### 9.2 高风险客户人工复核

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{"customerId":3,"transactionAmount":2000000,"transactionType":"TRANSFER","crossBorder":true,"recentTransactionCount":40,"newRelationship":true,"highRiskProduct":true,"sourceOfFunds":"","businessPurpose":"跨境投资","identityDocumentVerified":true,"beneficialOwnerVerified":true,"adverseMediaFlag":true}'
```

预期：`WAITING_REVIEW`，生成案件，客户状态 `MANUAL_REVIEW`。

真人复核：

```bash
curl -X POST http://localhost:8080/api/workflows/WF-20261102-000789/review \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECTED","reviewer":"compliance.li","comment":"资金来源无法解释，拒绝开户"}'
```

预期：`REJECTED`，报告生成，客户状态回写 `REJECTED`。

---

## 10. 兼容性说明

- 所有接口只接受/返回 JSON，CSV 下载除外；
- 未知字段默认忽略，但不要依赖该行为做版本兼容；
- 新增字段属于向后兼容变更；删除字段或改变语义需要升版本；
- 本版本没有认证头要求，生产接入 OIDC 后应在网关统一校验并注入 `userId`。
