package com.fcrc.workflow.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fcrc.common.domain.CustomerProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流状态键与读取工具。
 *
 * <p>Spring AI Alibaba Graph 的状态是一个 {@code Map<String,Object>}。显式集中定义键名，
 * 可以避免“字符串魔法值”在多处节点中漂移；同时把每个键的合并策略注册到
 * {@link #keyStrategyFactory()}，是使用 Graph 时最容易遗漏、也最容易导致状态被覆盖的地方。</p>
 *
 * <p>策略选择：</p>
 * <ul>
 *   <li>绝大多数键是 {@link KeyStrategy#REPLACE}：节点返回的新值覆盖旧值；</li>
 *   <li>{@code auditTrail} 使用 {@link KeyStrategy#APPEND}：并行节点也能安全追加，不会互相覆盖。</li>
 * </ul>
 */
public final class WorkflowState {

    public static final String RUN_NO = "runNo";
    public static final String CUSTOMER_ID = "customerId";
    public static final String CUSTOMER_NO = "customerNo";
    public static final String CUSTOMER_NAME = "customerName";
    public static final String CUSTOMER_PROFILE = "customerProfile";
    public static final String ONBOARDING_REQUEST = "onboardingRequest";

    public static final String RISK_ASSESSMENT_NO = "riskAssessmentNo";
    public static final String RISK_SCORE = "riskScore";
    public static final String RISK_LEVEL = "riskLevel";
    public static final String RISK_FACTORS = "riskFactors";
    public static final String RISK_RECOMMENDATION = "riskRecommendation";

    public static final String COMPLIANCE_SCREENING_NO = "complianceScreeningNo";
    public static final String COMPLIANCE_RESULT = "complianceResult";
    public static final String COMPLIANCE_FINDINGS = "complianceFindings";
    public static final String COMPLIANCE_RECOMMENDATION = "complianceRecommendation";
    public static final String COMPLIANCE_RISK_POINTS = "complianceRiskPoints";

    public static final String PRELIMINARY_DECISION = "preliminaryDecision";
    public static final String DECISION = "decision";
    public static final String DECISION_REASON = "decisionReason";

    public static final String REVIEW_DECISION = "reviewDecision";
    public static final String REVIEWER = "reviewer";
    public static final String REVIEW_COMMENT = "reviewComment";

    public static final String CASE_NO = "caseNo";
    public static final String REPORT_NO = "reportNo";
    public static final String ONBOARDING_STATUS = "onboardingStatus";
    public static final String AUDIT_TRAIL = "auditTrail";
    public static final String ERROR_MESSAGE = "errorMessage";

    private static final List<String> REPLACE_KEYS = List.of(
            RUN_NO, CUSTOMER_ID, CUSTOMER_NO, CUSTOMER_NAME, CUSTOMER_PROFILE, ONBOARDING_REQUEST,
            RISK_ASSESSMENT_NO, RISK_SCORE, RISK_LEVEL, RISK_FACTORS, RISK_RECOMMENDATION,
            COMPLIANCE_SCREENING_NO, COMPLIANCE_RESULT, COMPLIANCE_FINDINGS, COMPLIANCE_RECOMMENDATION,
            COMPLIANCE_RISK_POINTS, PRELIMINARY_DECISION, DECISION, DECISION_REASON,
            REVIEW_DECISION, REVIEWER, REVIEW_COMMENT, CASE_NO, REPORT_NO, ONBOARDING_STATUS, ERROR_MESSAGE);

    private WorkflowState() {
    }

    /**
     * 注册所有状态键。
     *
     * <p>Graph 只允许写入已注册的键；未注册的键会在更新时被忽略，
     * 这是很多“节点明明返回了值但下游读不到”问题的根因。</p>
     */
    public static KeyStrategyFactory keyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new LinkedHashMap<>();
            REPLACE_KEYS.forEach(key -> strategies.put(key, KeyStrategy.REPLACE));
            strategies.put(AUDIT_TRAIL, KeyStrategy.APPEND);
            return strategies;
        };
    }

    public static String string(OverAllState state, String key) {
        Object value = state.data().get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String string(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Integer integer(OverAllState state, String key) {
        Object value = state.data().get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    public static Long longValue(OverAllState state, String key) {
        Object value = state.data().get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(OverAllState state, String key) {
        Object value = state.data().get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public static CustomerProfile profile(OverAllState state) {
        Object value = state.data().get(CUSTOMER_PROFILE);
        return value instanceof CustomerProfile profile ? profile : null;
    }
}
