package com.fcrc.agent.subagent;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 子智能体声明。
 *
 * <p><b>教学点：Subagent 是"分工"而不是"复制"。</b> 主智能体负责对话与工具编排，
 * 当需要产出结构化、长文本的合规报告时，把任务委派给 {@code report-writer}。
 * 好处是：</p>
 * <ul>
 *   <li>子智能体有独立上下文，不会污染主对话；</li>
 *   <li>工具白名单更窄，只能读，不能审批；</li>
 *   <li>会话可持久化（{@code persistSession(true)}），长任务中断后可恢复。</li>
 * </ul>
 *
 * <p>子智能体同样受父级权限约束（{@code inheritParentPermissions=true}），
 * 因此即使声明了写工具也会被权限引擎拒绝。</p>
 */
@Configuration
public class SubagentConfiguration {

    @Bean
    public SubagentDeclaration reportWriterSubagent() {
        return SubagentDeclaration.builder()
                .name("report-writer")
                .description("负责把客户风险、合规筛查、案件与制度依据整理成结构化的合规报告草稿。"
                        + "当用户要求'写报告''生成合规说明''整理尽调结论'时委派给它。")
                .inlineAgentsBody("""
                        # 合规报告撰写子智能体

                        你是一名资深合规报告撰写人。你的任务是把事实整理成可审计的报告草稿。

                        ## 原则
                        1. 只使用工具返回的事实，禁止编造。
                        2. 每个结论后面标注依据（工具名或制度文件路径）。
                        3. 明确区分"事实""判断""建议"。
                        4. 涉及人工复核的，写明"待人工复核"，不得写成"已通过"。
                        5. 不要输出完整证件号、手机号。

                        ## 报告结构
                        ```
                        一、客户概况
                        二、风险评级与主要因子
                        三、合规筛查结论
                        四、关联案件与处置进展
                        五、制度依据
                        六、结论与建议（含待人工复核事项）
                        七、免责声明
                        ```

                        ## 工具建议
                        - 客户：`list_customers` / `get_customer_profile`
                        - 案件：`list_cases` / `get_case`
                        - 报告：`list_reports` / `get_report`
                        - 制度：`search_fcrc_knowledge` / `read_policy_document`
                        """)
                .tools(List.of(
                        "list_customers",
                        "get_customer_profile",
                        "list_cases",
                        "get_case",
                        "list_reports",
                        "get_report",
                        "search_fcrc_knowledge",
                        "read_policy_document",
                        "read_file",
                        "list_files",
                        "grep_files"))
                .persistSession(true)
                .inheritParentPermissions(true)
                .steps(8)
                .build();
    }
}
