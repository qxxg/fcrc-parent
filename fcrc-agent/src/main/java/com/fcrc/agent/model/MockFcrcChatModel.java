package com.fcrc.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 确定性的本地演示模型。
 *
 * <p><b>为什么需要它？</b> 真实大模型需要 API Key、网络和费用。教学/演示环境经常
 * 三者都不可用。本类实现一个"规则驱动的假模型"，让 AgentScope 的完整链路
 * （ReAct 循环、工具调用、权限、流式事件、会话持久化）在没有外部依赖时也能跑通。</p>
 *
 * <p><b>工作方式：</b></p>
 * <ol>
 *   <li>如果最后一条消息是 USER：根据关键词选择一个工具调用；没有命中则返回帮助文本。</li>
 *   <li>如果最后一条消息是 TOOL：把工具结果整理成中文摘要返回，结束本轮。</li>
 *   <li>其他情况：返回兜底文本，避免死循环。</li>
 * </ol>
 *
 * <p><b>重要：</b> 这不是"假装 AI"的生产方案。生产环境请设置
 * {@code fcrc.agent.model=dashscope} 并提供 {@code DASHSCOPE_API_KEY}。</p>
 */
public class MockFcrcChatModel extends ChatModelBase {

    private static final Logger log = LoggerFactory.getLogger(MockFcrcChatModel.class);
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,9})");
    private static final int MAX_TOOL_TEXT = 3000;

    private final ObjectMapper objectMapper;

    public MockFcrcChatModel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        setContextWindowSize(32_000);
        setNativeStructuredOutput(false);
    }

    @Override
    public String getModelName() {
        return "fcrc-mock-model";
    }

    @Override
    protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        Msg last = messages == null || messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (last == null) {
            return Flux.just(text("请告诉我你想查询什么：客户、风险、合规、工作流、案件或报告。"));
        }

        if (last.getRole() == MsgRole.TOOL) {
            return Flux.just(text(summarizeToolResult(last.getTextContent())));
        }

        if (last.getRole() == MsgRole.USER) {
            String userText = last.getTextContent() == null ? "" : last.getTextContent();
            if (isMaintenancePrompt(userText)) {
                // 记忆 flush / 上下文压缩是框架内部调用，不能把它当成业务问题去调工具。
                return Flux.just(text("（本地演示模型）记忆与上下文维护已完成。"));
            }
            PlannedCall call = plan(userText, tools);
            if (call != null) {
                log.debug("Mock 模型选择工具：{}", call.name());
                return Flux.just(toolCall(call));
            }
            return Flux.just(text(helpText(userText)));
        }

        return Flux.just(text("本轮已处理完成。你可以继续询问客户、风险、合规、工作流、案件或报告。"));
    }

    // ------------------------------------------------------------------
    // 决策
    // ------------------------------------------------------------------

    private PlannedCall plan(String text, List<ToolSchema> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Long id = firstNumber(text);

        if (containsAny(lower, "工作流", "开户", "onboarding", "workflow")) {
            if (hasTool(tools, "start_onboarding_workflow") && id != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("customer_id", id);
                args.put("transaction_amount", "250000");
                args.put("transaction_type", "TRANSFER");
                args.put("cross_border", lower.contains("跨境"));
                return new PlannedCall("start_onboarding_workflow", args);
            }
            if (hasTool(tools, "list_workflow_runs")) {
                return new PlannedCall("list_workflow_runs", Map.of("size", 10));
            }
        }

        if (containsAny(lower, "合规", "筛查", "制裁", "洗钱", "aml", "compliance")) {
            if (hasTool(tools, "screen_customer_compliance")) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("customer_id", id == null ? 1L : id);
                args.put("transaction_amount", "150000");
                args.put("transaction_type", "TRANSFER");
                args.put("cross_border", lower.contains("跨境"));
                args.put("source_of_funds", "工资及经营收入");
                args.put("business_purpose", "日常资金结算");
                args.put("identity_document_verified", true);
                args.put("beneficial_owner_verified", true);
                return new PlannedCall("screen_customer_compliance", args);
            }
        }

        if (containsAny(lower, "风险", "评分", "risk")) {
            if (hasTool(tools, "assess_customer_risk")) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("customer_id", id == null ? 1L : id);
                args.put("transaction_amount", "300000");
                args.put("transaction_type", "TRANSFER");
                args.put("cross_border", lower.contains("跨境"));
                args.put("recent_transaction_count", 12);
                args.put("new_relationship", false);
                args.put("high_risk_product", lower.contains("高風險") || lower.contains("高风险"));
                return new PlannedCall("assess_customer_risk", args);
            }
        }

        if (containsAny(lower, "案件", "case")) {
            if (hasTool(tools, "list_cases")) {
                return new PlannedCall("list_cases", Map.of("size", 10));
            }
        }

        if (containsAny(lower, "报告", "report")) {
            if (hasTool(tools, "list_reports")) {
                return new PlannedCall("list_reports", Map.of("size", 10));
            }
        }

        if (containsAny(lower, "制度", "知识", "pep", "受益所有人", "高风险国家", "可疑交易", "政策")) {
            if (hasTool(tools, "search_fcrc_knowledge")) {
                return new PlannedCall("search_fcrc_knowledge", Map.of("query", text, "limit", 5));
            }
        }

        if (containsAny(lower, "客户", "customer")) {
            if (hasTool(tools, "get_customer_profile") && id != null) {
                return new PlannedCall("get_customer_profile", Map.of("customer_id", id));
            }
            if (hasTool(tools, "list_customers")) {
                return new PlannedCall("list_customers", Map.of("size", 10));
            }
        }

        return null;
    }

    private boolean hasTool(List<ToolSchema> tools, String name) {
        return tools.stream().anyMatch(schema -> name.equals(schema.getName()));
    }

    /**
     * 识别框架内部的记忆/压缩调用。
     *
     * <p>这些调用也走模型，但它们的输出会被写进记忆或摘要，绝不能触发业务工具。</p>
     */
    private boolean isMaintenancePrompt(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("memory")
                || lower.contains("compaction")
                || lower.contains("summariz")
                || lower.contains("consolidat")
                || lower.contains("flush")
                || lower.contains("长期记忆")
                || lower.contains("记忆维护")
                || lower.contains("上下文压缩");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Long firstNumber(String text) {
        Matcher matcher = NUMBER.matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    // ------------------------------------------------------------------
    // 响应构造
    // ------------------------------------------------------------------

    private ChatResponse toolCall(PlannedCall call) {
        List<ContentBlock> content = new ArrayList<>();
        content.add(new ToolUseBlock(UUID.randomUUID().toString(), call.name(), call.arguments()));
        return ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .finishReason("tool_calls")
                .build();
    }

    private ChatResponse text(String text) {
        return ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .content(List.of(TextBlock.builder().text(text).build()))
                .finishReason("stop")
                .build();
    }

    private String summarizeToolResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return "工具没有返回数据。";
        }
        String pretty = raw;
        try {
            JsonNode node = objectMapper.readTree(raw);
            pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ignored) {
            // 工具结果不是 JSON 时按原样展示
        }
        if (pretty.length() > MAX_TOOL_TEXT) {
            pretty = pretty.substring(0, MAX_TOOL_TEXT) + "\n...（结果过长已截断）";
        }
        return "工具执行结果：\n" + pretty + "\n\n以上数据来自 FCRC 业务服务。"
                + "如需人工复核或审批，请由真人在工作流页面操作，AI 不会代替人工做决定。";
    }

    private String helpText(String userText) {
        return "我是 FCRC 合规风控 Copilot。当前使用本地演示模型（无需 API Key）。\n"
                + "你可以这样问我：\n"
                + "- 查客户：\"列出客户\"，或\"客户 1 的画像\"\n"
                + "- 风险：\"评估客户 1 的风险\"、\"跨境交易风险\"\n"
                + "- 合规：\"对客户 1 做合规筛查\"\n"
                + "- 工作流：\"给客户 1 开户\"、\"查工作流状态\"\n"
                + "- 案件/报告：\"有哪些案件\"、\"列出监管报告\"\n"
                + "- 制度：\"PEP 的尽职调查要求\"、\"高风险国家有哪些\"\n\n"
                + "你刚才说的是：" + (userText.isBlank() ? "（空）" : userText);
    }

    /** 计划中的一次工具调用。 */
    private record PlannedCall(String name, Map<String, Object> arguments) {
    }
}
