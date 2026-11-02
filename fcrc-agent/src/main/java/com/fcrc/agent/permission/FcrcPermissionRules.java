package com.fcrc.agent.permission;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.List;

/**
 * FCRC 权限策略。
 *
 * <p><b>教学点：Agent 的权限系统不是"可有可无"的装饰。</b> 一个能执行 shell、能写文件、
 * 能访问任意 URL 的 Agent，一旦被提示词注入攻击，后果比传统越权更严重。所以这里采用
 * "默认拒绝 + 显式白名单"：</p>
 *
 * <ul>
 *   <li>{@link PermissionMode#DEFAULT}：没有命中 allow 规则的操作一律询问/拒绝；</li>
 *   <li>白名单：只放行 FCRC 业务工具、只读文件工具、记忆/会话/计划/技能工具；</li>
 *   <li>黑名单：明确拒绝 shell（{@code execute}）、写文件、联网工具；</li>
 *   <li>人工复核接口未注册为工具，形成第二道防线。</li>
 * </ul>
 *
 * <p>为什么允许 {@code read_file}/{@code grep_files}？因为制度库检索需要读工作区文件。
 * 为什么拒绝 {@code write_file}/{@code edit_file}？因为 AI 不应该修改制度或代码，
 * 需要产出时走"报告"业务流程，而不是直接写服务器文件。</p>
 */
public final class FcrcPermissionRules {

    private FcrcPermissionRules() {
    }

    /** 只读业务工具 + 受控的框架工具。 */
    public static final List<String> ALLOWED_TOOLS = List.of(
            // FCRC 业务工具
            "list_customers",
            "get_customer_profile",
            "assess_customer_risk",
            "screen_customer_compliance",
            "start_onboarding_workflow",
            "get_workflow_status",
            "list_workflow_runs",
            "list_cases",
            "get_case",
            "list_reports",
            "get_report",
            "search_fcrc_knowledge",
            "read_policy_document",
            // 只读文件工具
            "read_file",
            "list_files",
            "glob_files",
            "grep_files",
            // 记忆
            "memory_search",
            "memory_get",
            "memory_save",
            // 会话检索
            "session_search",
            "session_list",
            "session_history",
            // Plan Mode
            "plan_enter",
            "plan_write",
            "plan_exit",
            // 技能
            "load_skill_through_path",
            "skill_manage",
            // 子智能体与异步任务
            "agent_spawn",
            "agent_list",
            "task_list",
            "task_output",
            "task_cancel",
            "wait_async_results");

    /** 明确拒绝的高风险工具。 */
    public static final List<String> DENIED_TOOLS = List.of(
            "execute",      // shell 命令
            "write_file",   // 写文件
            "edit_file",    // 改文件
            "web_search",   // 联网搜索
            "web_fetch");   // 联网抓取

    public static PermissionContextState build() {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT);

        for (String tool : ALLOWED_TOOLS) {
            builder.addAllowRule(tool, new PermissionRule(tool, null, PermissionBehavior.ALLOW, "fcrc-policy"));
        }
        for (String tool : DENIED_TOOLS) {
            builder.addDenyRule(tool, new PermissionRule(tool, null, PermissionBehavior.DENY, "fcrc-policy"));
        }
        return builder.build();
    }
}
