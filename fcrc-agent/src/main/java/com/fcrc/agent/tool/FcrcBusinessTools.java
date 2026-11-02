package com.fcrc.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.agent.client.AgentDownstreamClient;
import com.fcrc.agent.client.AgentDownstreamException;
import com.fcrc.agent.client.dto.CustomerProfileView;
import com.fcrc.common.domain.CustomerProfile;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FCRC 业务工具集。
 *
 * <p><b>教学点 1：工具就是 AI 的手。</b> 模型本身只能生成文本，真正查客户、跑风险、
 * 启动工作流，都要靠这里的方法。每个工具都要有清晰的名字、描述和参数说明，
 * 因为模型是通过这些文字决定"该不该调用、传什么参数"的。</p>
 *
 * <p><b>教学点 2：工具返回 String。</b> AgentScope 会把返回值作为工具结果喂回模型，
 * 因此统一返回 JSON 字符串，既有结构又便于模型阅读。异常不抛出，而是转成
 * {@code {"error": "..."}}，让模型可以解释失败原因。</p>
 *
 * <p><b>教学点 3：只读优先。</b> 除"启动工作流"外，所有工具都是只读的（readOnly=true），
 * 便于权限引擎做更细的控制，也方便未来接入审计和缓存。</p>
 *
 * <p><b>合规底线：</b> 这里没有"提交人工复核"工具。工作流到人工复核节点会暂停，
 * 必须由真人在工作流服务里调用 {@code /review}。AI 可以解释、可以建议，
 * 但不能代替人做审批决定。</p>
 */
@Component
public class FcrcBusinessTools {

    private static final Logger log = LoggerFactory.getLogger(FcrcBusinessTools.class);

    private final AgentDownstreamClient client;
    private final ObjectMapper objectMapper;

    public FcrcBusinessTools(AgentDownstreamClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 客户
    // ------------------------------------------------------------------

    @Tool(name = "list_customers",
            description = "分页查询客户。keyword 支持客户号/姓名模糊匹配；status 可传 KYC 或开户状态。",
            readOnly = true)
    public String listCustomers(
            @ToolParam(name = "keyword", description = "客户号或姓名关键字，可为空", required = false) String keyword,
            @ToolParam(name = "status", description = "状态过滤，例如 COMPLETED/MANUAL_REVIEW/PENDING，可为空",
                    required = false) String status,
            @ToolParam(name = "page", description = "页码，从 0 开始，默认 0", required = false) Integer page,
            @ToolParam(name = "size", description = "每页条数，默认 10，最大 50", required = false) Integer size) {
        return call(() -> client.listCustomers(keyword, status, page == null ? 0 : page, clamp(size)));
    }

    @Tool(name = "get_customer_profile",
            description = "按客户 ID 获取完整客户画像（姓名、国家、行业、收入、职业、PEP 标识等）。"
                    + "这是风险评估与合规筛查的输入。",
            readOnly = true)
    public String getCustomerProfile(
            @ToolParam(name = "customer_id", description = "客户主键 ID") Long customerId) {
        return call(() -> client.getCustomerProfile(customerId));
    }

    // ------------------------------------------------------------------
    // 风险 / 合规
    // ------------------------------------------------------------------

    @Tool(name = "assess_customer_risk",
            description = "对指定客户做风险评分。必须提供 customer_id 和交易金额；"
                    + "跨境、新关系、高风险产品会显著提高评分。返回 0-100 分、等级、风险因子和建议。",
            readOnly = true)
    public String assessCustomerRisk(
            @ToolParam(name = "customer_id", description = "客户主键 ID") Long customerId,
            @ToolParam(name = "transaction_amount", description = "预计交易金额，单位元，例如 250000") String transactionAmount,
            @ToolParam(name = "transaction_type", description = "DEPOSIT/TRANSFER/INVESTMENT/TRADE_FINANCE",
                    required = false) String transactionType,
            @ToolParam(name = "cross_border", description = "是否跨境，默认 false", required = false) Boolean crossBorder,
            @ToolParam(name = "recent_transaction_count", description = "近 30 天交易笔数，默认 0",
                    required = false) Integer recentTransactionCount,
            @ToolParam(name = "new_relationship", description = "是否新建立业务关系，默认 false",
                    required = false) Boolean newRelationship,
            @ToolParam(name = "high_risk_product", description = "是否涉及高风险产品，默认 false",
                    required = false) Boolean highRiskProduct) {
        return call(() -> {
            CustomerProfileView view = client.getCustomerProfile(customerId);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("customerNo", view.customerNo());
            request.put("profile", view.profile());
            request.put("transactionAmount", amount(transactionAmount));
            request.put("transactionType", blankToNull(transactionType));
            request.put("crossBorder", Boolean.TRUE.equals(crossBorder));
            request.put("recentTransactionCount", recentTransactionCount == null ? 0 : recentTransactionCount);
            request.put("newRelationship", Boolean.TRUE.equals(newRelationship));
            request.put("highRiskProduct", Boolean.TRUE.equals(highRiskProduct));
            return client.assessRisk(request);
        });
    }

    @Tool(name = "screen_customer_compliance",
            description = "对指定客户做 KYC/AML/制裁名单/负面舆情筛查。返回 PASS/MANUAL/REJECT 等结果、"
                    + "风险点、发现项和建议。资金用途说明越完整，结果越可信。",
            readOnly = true)
    public String screenCustomerCompliance(
            @ToolParam(name = "customer_id", description = "客户主键 ID") Long customerId,
            @ToolParam(name = "transaction_amount", description = "预计交易金额，单位元，可为空",
                    required = false) String transactionAmount,
            @ToolParam(name = "transaction_type", description = "DEPOSIT/TRANSFER/INVESTMENT/TRADE_FINANCE",
                    required = false) String transactionType,
            @ToolParam(name = "cross_border", description = "是否跨境，默认 false", required = false) Boolean crossBorder,
            @ToolParam(name = "source_of_funds", description = "资金来源说明，可为空", required = false) String sourceOfFunds,
            @ToolParam(name = "business_purpose", description = "业务目的说明，可为空", required = false) String businessPurpose,
            @ToolParam(name = "identity_document_verified", description = "身份证明是否已核验，默认 false",
                    required = false) Boolean identityDocumentVerified,
            @ToolParam(name = "beneficial_owner_verified", description = "受益所有人是否已核验，默认 false",
                    required = false) Boolean beneficialOwnerVerified,
            @ToolParam(name = "adverse_media_flag", description = "外部负面舆情命中，默认 false",
                    required = false) Boolean adverseMediaFlag,
            @ToolParam(name = "risk_level", description = "客户当前风险等级 LOW/MEDIUM/HIGH/CRITICAL，可为空",
                    required = false) String riskLevel) {
        return call(() -> {
            CustomerProfileView view = client.getCustomerProfile(customerId);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("customerNo", view.customerNo());
            request.put("profile", view.profile());
            request.put("transactionAmount", amount(transactionAmount));
            request.put("transactionType", blankToNull(transactionType));
            request.put("crossBorder", Boolean.TRUE.equals(crossBorder));
            request.put("sourceOfFunds", blankToNull(sourceOfFunds));
            request.put("businessPurpose", blankToNull(businessPurpose));
            request.put("identityDocumentVerified", Boolean.TRUE.equals(identityDocumentVerified));
            request.put("beneficialOwnerVerified", Boolean.TRUE.equals(beneficialOwnerVerified));
            request.put("adverseMediaFlag", Boolean.TRUE.equals(adverseMediaFlag));
            request.put("riskLevel", blankToNull(riskLevel));
            return client.screenCompliance(request);
        });
    }

    // ------------------------------------------------------------------
    // 工作流
    // ------------------------------------------------------------------

    @Tool(name = "start_onboarding_workflow",
            description = "为客户启动开户+风险+合规联合工作流（Spring AI Alibaba Graph 编排）。"
                    + "自动审批条件：合规 PASS、风险分 < 40、客户非 PEP；否则会创建合规案件并暂停等待人工复核。"
                    + "本工具只负责启动，不能代替人工复核。")
    public String startOnboardingWorkflow(
            @ToolParam(name = "customer_id", description = "客户主键 ID") Long customerId,
            @ToolParam(name = "transaction_amount", description = "预计交易金额，单位元", required = false) String transactionAmount,
            @ToolParam(name = "transaction_type", description = "DEPOSIT/TRANSFER/INVESTMENT/TRADE_FINANCE",
                    required = false) String transactionType,
            @ToolParam(name = "cross_border", description = "是否跨境，默认 false", required = false) Boolean crossBorder,
            @ToolParam(name = "recent_transaction_count", description = "近 30 天交易笔数，默认 0",
                    required = false) Integer recentTransactionCount,
            @ToolParam(name = "new_relationship", description = "是否新建立业务关系，默认 false",
                    required = false) Boolean newRelationship,
            @ToolParam(name = "high_risk_product", description = "是否涉及高风险产品，默认 false",
                    required = false) Boolean highRiskProduct,
            @ToolParam(name = "source_of_funds", description = "资金来源说明", required = false) String sourceOfFunds,
            @ToolParam(name = "business_purpose", description = "业务目的说明", required = false) String businessPurpose) {
        return call(() -> {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("customerId", customerId);
            request.put("transactionAmount", amount(transactionAmount));
            request.put("transactionType", blankToNull(transactionType));
            request.put("crossBorder", Boolean.TRUE.equals(crossBorder));
            request.put("recentTransactionCount", recentTransactionCount == null ? 0 : recentTransactionCount);
            request.put("newRelationship", Boolean.TRUE.equals(newRelationship));
            request.put("highRiskProduct", Boolean.TRUE.equals(highRiskProduct));
            request.put("sourceOfFunds", blankToNull(sourceOfFunds));
            request.put("businessPurpose", blankToNull(businessPurpose));
            return client.startOnboarding(request);
        });
    }

    @Tool(name = "get_workflow_status",
            description = "按运行号查询工作流状态、当前节点、风险/合规结果、是否等待人工复核，以及事件时间线。",
            readOnly = true)
    public String getWorkflowStatus(
            @ToolParam(name = "run_no", description = "工作流运行号，例如 WF20261102-0001") String runNo) {
        return call(() -> client.getWorkflow(runNo));
    }

    @Tool(name = "list_workflow_runs",
            description = "分页查询工作流运行记录，可用 status 过滤（RUNNING/MANUAL_REVIEW/COMPLETED/REJECTED/FAILED）。",
            readOnly = true)
    public String listWorkflowRuns(
            @ToolParam(name = "status", description = "状态过滤，可为空", required = false) String status,
            @ToolParam(name = "page", description = "页码，从 0 开始", required = false) Integer page,
            @ToolParam(name = "size", description = "每页条数，默认 10", required = false) Integer size) {
        return call(() -> client.listWorkflows(status, page == null ? 0 : page, clamp(size)));
    }

    // ------------------------------------------------------------------
    // 案件 / 报告
    // ------------------------------------------------------------------

    @Tool(name = "list_cases",
            description = "分页查询合规案件，支持关键字、状态（OPEN/IN_PROGRESS/RESOLVED/CLOSED）和优先级过滤。",
            readOnly = true)
    public String listCases(
            @ToolParam(name = "keyword", description = "案件号/客户号/标题关键字，可为空", required = false) String keyword,
            @ToolParam(name = "status", description = "状态过滤，可为空", required = false) String status,
            @ToolParam(name = "priority", description = "LOW/MEDIUM/HIGH/CRITICAL，可为空", required = false) String priority,
            @ToolParam(name = "page", description = "页码，从 0 开始", required = false) Integer page,
            @ToolParam(name = "size", description = "每页条数，默认 10", required = false) Integer size) {
        return call(() -> client.listCases(keyword, status, priority, page == null ? 0 : page, clamp(size)));
    }

    @Tool(name = "get_case",
            description = "按案件号查询案件详情和不可变备注时间线，用于向用户解释案件进展。",
            readOnly = true)
    public String getCase(
            @ToolParam(name = "case_no", description = "案件号，例如 CASE20261102-0001") String caseNo) {
        return call(() -> client.getCase(caseNo));
    }

    @Tool(name = "list_reports",
            description = "分页查询监管报告，支持客户号、报告类型（CUSTOMER_RISK/SAR/CTR/PERIODIC_SUMMARY）和状态过滤。",
            readOnly = true)
    public String listReports(
            @ToolParam(name = "customer_no", description = "客户号，可为空", required = false) String customerNo,
            @ToolParam(name = "report_type", description = "报告类型，可为空", required = false) String reportType,
            @ToolParam(name = "status", description = "DRAFT/FINAL/SUBMITTED，可为空", required = false) String status,
            @ToolParam(name = "page", description = "页码，从 0 开始", required = false) Integer page,
            @ToolParam(name = "size", description = "每页条数，默认 10", required = false) Integer size) {
        return call(() -> client.listReports(customerNo, reportType, status,
                page == null ? 0 : page, clamp(size)));
    }

    @Tool(name = "get_report",
            description = "按报告号查询监管报告全文摘要。",
            readOnly = true)
    public String getReport(
            @ToolParam(name = "report_no", description = "报告号，例如 RPT202611-0001") String reportNo) {
        return call(() -> client.getReport(reportNo));
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private int clamp(Integer size) {
        if (size == null) {
            return 10;
        }
        return Math.max(1, Math.min(size, 50));
    }

    private BigDecimal amount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("transaction_amount 必须是数字，例如 250000");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 统一执行 + 序列化 + 错误转换。
     *
     * <p>工具绝不能把异常直接抛给模型框架，否则整轮对话会中断。
     * 把异常变成结构化错误，模型才有机会说"下游服务暂时不可用，请稍后再试"。</p>
     */
    private String call(ToolCall action) {
        try {
            Object value = action.run();
            if (value == null) {
                return "{\"error\":\"未查询到数据\"}";
            }
            return objectMapper.writeValueAsString(value);
        } catch (AgentDownstreamException ex) {
            log.warn("工具调用失败：{}", ex.getMessage());
            return error(ex.getMessage());
        } catch (Exception ex) {
            log.warn("工具调用异常", ex);
            return error(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception ex) {
            return "{\"error\":\"工具执行失败\"}";
        }
    }

    /** 允许抛受检异常的工具动作。 */
    @FunctionalInterface
    private interface ToolCall {
        Object run() throws Exception;
    }
}
