package com.fcrc.workflow;

import com.fcrc.workflow.client.WorkflowClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 客户开户编排微服务。
 *
 * <p>该服务是平台的“流程大脑”，把客户、风险、合规、案件、报告五个领域服务
 * 串成一条可审计、可暂停、可恢复的开户流水线。核心编排使用
 * Spring AI Alibaba Graph 的 {@code StateGraph}：</p>
 *
 * <pre>
 * START
 *   └─ load_customer
 *        ├─ risk_assess ──────┐
 *        └─ compliance_screen ┴─ aggregate
 *                                  ├─(条件路由)─ auto_approve ─ finalize_approval ─ END
 *                                  └─(条件路由)─ manual_review(暂停等待人工) ─┬─ finalize_approval
 *                                                                          └─ finalize_rejection
 * </pre>
 *
 * <p>教学重点：</p>
 * <ol>
 *   <li><b>扇出/扇入</b>：风险与合规互不阻塞，可并行执行，再由聚合节点合并；</li>
 *   <li><b>条件路由</b>：聚合节点根据风险分、风险等级与合规结论选择自动审批或人工复核；</li>
 *   <li><b>Human-in-the-loop</b>：{@code interruptBefore("manual_review")} 在人工节点前暂停，
 *       复核意见通过 {@code RunnableConfig.withResume()} 恢复执行；</li>
 *   <li><b>持久化检查点</b>：{@code JpaCheckpointSaver} 把图状态写入数据库，
 *       服务重启后仍可继续审批，而不是依赖内存状态；</li>
 *   <li><b>可观测</b>：每个节点的开始/结束都写入事件表，并通过 SSE 提供给前端。</li>
 * </ol>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
@EnableConfigurationProperties(WorkflowClientProperties.class)
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
