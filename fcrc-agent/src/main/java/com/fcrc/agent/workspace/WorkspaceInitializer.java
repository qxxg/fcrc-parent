package com.fcrc.agent.workspace;

import com.fcrc.agent.config.AgentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工作区初始化器。
 *
 * <p><b>教学点：Workspace 是 Agent 的"办公桌"。</b> 它把四类内容放在固定位置：</p>
 * <ul>
 *   <li>{@code AGENTS.md}：角色、职责、输出规范，作为系统提示的一部分被读取；</li>
 *   <li>{@code knowledge/}：制度库，通过检索工具按需读取，回答可引用；</li>
 *   <li>{@code skills/<name>/SKILL.md}：可复用技能，带 YAML frontmatter；</li>
 *   <li>{@code MEMORY.md} + {@code memory/}：长期记忆，跨会话保留。</li>
 * </ul>
 *
 * <p>初始化是幂等的：文件已存在就不覆盖，避免重启把人工调整过的制度覆盖掉。</p>
 */
@Component
public class WorkspaceInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitializer.class);

    private final AgentProperties properties;

    public WorkspaceInitializer(AgentProperties properties) {
        this.properties = properties;
    }

    public Path workspace() {
        return properties.getWorkspace().toAbsolutePath().normalize();
    }

    /**
     * 创建目录与种子文件。可重复调用。
     */
    public synchronized void initialize() {
        Path root = workspace();
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("knowledge"));
            Files.createDirectories(root.resolve("skills"));
            Files.createDirectories(root.resolve("plans"));
            Files.createDirectories(root.resolve("memory"));

            writeIfAbsent(root.resolve("AGENTS.md"), AGENTS_MD);
            writeIfAbsent(root.resolve("README.md"), README_MD);
            writeIfAbsent(root.resolve("MEMORY.md"), MEMORY_MD);

            writeIfAbsent(root.resolve("knowledge/KNOWLEDGE.md"), KNOWLEDGE_MD);
            writeIfAbsent(root.resolve("knowledge/high-risk-countries.md"), HIGH_RISK_COUNTRIES);
            writeIfAbsent(root.resolve("knowledge/pep-due-diligence.md"), PEP_DD);
            writeIfAbsent(root.resolve("knowledge/suspicious-transaction.md"), SUSPICIOUS);

            writeIfAbsent(root.resolve("skills/kyc-review/SKILL.md"), KYC_SKILL);
            writeIfAbsent(root.resolve("skills/sanctions-escalation/SKILL.md"), SANCTIONS_SKILL);

            log.info("Agent 工作区已就绪：{}", root);
        } catch (IOException ex) {
            throw new IllegalStateException("初始化 Agent 工作区失败：" + root, ex);
        }
    }

    private void writeIfAbsent(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 工作区种子内容
    // ------------------------------------------------------------------

    private static final String AGENTS_MD = """
            # FCRC 合规风控 Copilot

            ## 角色
            你是 FCRC（Financial Compliance & Risk Control）平台的合规风控助手。
            你服务的是银行/支付机构的反洗钱、KYC、风控与合规运营人员。

            ## 你能做什么
            - 查询客户画像、客户列表与开户状态
            - 发起风险评分与合规筛查
            - 启动"开户 + 风险 + 合规"联合工作流（Spring AI Alibaba Graph 编排）
            - 查询工作流状态、案件、监管报告
            - 检索本地制度库并给出带引用的解释
            - 在需要时把复杂写作任务委派给 report-writer 子智能体

            ## 你不能做什么
            - 不能代替人工做审批、复核、放行、上报决定
            - 不能修改客户数据、案件状态、报告状态
            - 不能执行 shell、不能写服务器文件、不能访问互联网
            - 不能编造任何业务数据

            ## 输出规范
            1. 先给结论，再给依据；依据必须标注来自哪个工具或哪个制度文件。
            2. 涉及金额时写清币种与单位。
            3. 风险评分要解释主要风险因子，而不是只报一个数字。
            4. 工作流处于 MANUAL_REVIEW 时，明确提示"需要真人复核"。
            5. 不确定时先调用工具查询，不要猜测。

            ## 工具使用顺序建议
            - 客户问题：list_customers → get_customer_profile
            - 风险问题：get_customer_profile → assess_customer_risk
            - 合规问题：get_customer_profile → screen_customer_compliance
            - 开户问题：get_customer_profile → start_onboarding_workflow → get_workflow_status
            - 制度问题：search_fcrc_knowledge → read_policy_document
            """;

    private static final String README_MD = """
            # Agent 工作区说明

            本目录由 `WorkspaceInitializer` 自动创建，是 AgentScope HarnessAgent 的工作区。

            | 路径 | 作用 |
            | --- | --- |
            | `AGENTS.md` | 角色与行为规范，进入系统提示 |
            | `MEMORY.md` | 长期记忆摘要 |
            | `memory/` | 按日期沉淀的记忆账本 |
            | `knowledge/` | 合规制度库，供检索工具使用 |
            | `skills/` | 技能目录，每个技能一个 `SKILL.md` |
            | `plans/` | Plan Mode 生成的计划文件 |

            生产环境建议把 `knowledge/` 挂载为只读卷，由合规部门维护。
            """;

    private static final String MEMORY_MD = """
            # 长期记忆

            ## 平台约定
            - 自动审批条件：合规 PASS、风险分 < 40、客户非 PEP。
            - 其余情况一律进入人工复核，并创建合规案件。
            - AI 不参与人工复核。

            ## 常用业务标识
            - 客户号：CUS 开头
            - 工作流运行号：WF 开头
            - 案件号：CASE 开头
            - 报告号：RPT 开头
            """;

    private static final String KNOWLEDGE_MD = """
            # FCRC 合规制度摘要

            ## 1. KYC（了解你的客户）
            - 所有客户在建立业务关系前必须完成身份识别与受益所有人识别。
            - 身份证明文件必须核验原件或等效电子凭证。
            - 客户风险等级分为 LOW / MEDIUM / HIGH / CRITICAL，至少每年复核一次；
              HIGH 及以上每半年复核一次。

            ## 2. 风险评分
            - 评分范围 0-100。
            - 低于 40 分为低风险，40-69 分为中风险，70-89 分为高风险，90 分及以上为严重风险。
            - 跨境、新建立关系、高风险产品、异常交易频率会显著提高评分。

            ## 3. 自动审批边界
            - 仅当合规结果为 PASS、风险分 < 40、且客户不是 PEP 时，才允许自动通过。
            - 任一条件不满足，必须转人工复核并留存理由。

            ## 4. 记录保存
            - 客户身份资料、交易记录、筛查记录自业务关系结束之日起至少保存 5 年。
            - 监管报告提交后不可修改，只能追加说明。
            """;

    private static final String HIGH_RISK_COUNTRIES = """
            # 高风险国家/地区清单（示例）

            以下清单仅用于教学演示，不代表任何官方口径。生产环境应接入权威名单并定期更新。

            - 受全面制裁或高强度反洗钱监控的国家/地区
            - 离岸金融中心且透明度较低的地区
            - FATF 灰名单国家/地区

            ## 处理要求
            1. 涉及高风险国家的交易，风险评分至少 +25 分。
            2. 必须核实资金来源与最终受益人。
            3. 不得自动审批，必须人工复核。
            4. 必要时提交可疑交易报告（SAR）。
            """;

    private static final String PEP_DD = """
            # PEP（政治公众人物）尽职调查

            ## 定义
            PEP 指正在或曾经担任重要公职的人员，及其近亲属和密切关系人。

            ## 要求
            1. 建立业务关系前必须获得高级管理层批准。
            2. 必须识别资金来源与财富来源。
            3. 必须进行强化持续监控。
            4. 不得适用简化尽职调查。
            5. 本平台策略：PEP 客户一律不自动审批，转人工复核。

            ## 常见误区
            - "客户否认是 PEP" 不能作为豁免依据，必须以名单筛查结果为准。
            - PEP 离职后一段时间内仍应按 PEP 管理，具体期限按属地监管要求执行。
            """;

    private static final String SUSPICIOUS = """
            # 可疑交易识别与上报

            ## 典型特征
            - 交易金额与客户身份、经营规模明显不符
            - 短时间内高频、整数金额、快进快出
            - 资金来自高风险国家或流向制裁地区
            - 客户对资金来源解释含糊或前后矛盾
            - 使用多个关联账户拆分交易

            ## 处理流程
            1. 冻结自动审批，转人工复核。
            2. 补充客户尽职调查材料。
            3. 经合规官确认后提交 SAR。
            4. 全程留痕，不得向客户泄露上报行为。
            """;

    private static final String KYC_SKILL = """
            ---
            name: kyc-review
            description: 对单个客户执行 KYC 完整性检查，输出缺失项、风险提示与下一步动作。
            ---

            # KYC 复核技能

            当用户要求"检查 KYC""开户前复核""资料是否齐全"时使用本技能。

            ## 步骤
            1. 调用 `get_customer_profile` 获取客户画像。
            2. 检查必填项：姓名、客户类型、国家、行业、职业。
            3. 检查 PEP 标识；PEP 必须转人工。
            4. 调用 `screen_customer_compliance` 做制裁与负面舆情筛查。
            5. 汇总：通过 / 需补充 / 拒绝，并列出依据。

            ## 输出模板
            ```
            结论：<通过 | 需补充 | 拒绝>
            缺失项：<列表>
            风险提示：<列表>
            下一步：<具体动作>
            依据：<工具或制度文件>
            ```
            """;

    private static final String SANCTIONS_SKILL = """
            ---
            name: sanctions-escalation
            description: 制裁名单命中后的分级处置与升级路径，确保不放过也不误伤。
            ---

            # 制裁命中处置技能

            ## 分级
            - **精确命中**：姓名、证件、出生日期全部匹配 → 立即停止业务，转人工与合规官。
            - **疑似命中**：仅姓名相似 → 补充出生日期、国籍、地址后复核。
            - **误报**：可证明为不同人 → 记录排除理由，保留证据。

            ## 红线
            - 不得由 AI 直接排除精确命中。
            - 不得在未留痕的情况下继续交易。
            - 涉及监管报告时，由人工确认后提交。
            """;
}
