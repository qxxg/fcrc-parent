package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.domain.CustomerProfile;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 风险域客户端。
 *
 * <p>风险评分是同步、确定性的规则计算，调用失败时不能让流程“猜一个分数”，
 * 因此异常会直接向上冒泡，由运行服务把本次运行标记为 FAILED。</p>
 */
@Component
public class RiskClient {

    private final DownstreamRestClient http;
    private final WorkflowClientProperties properties;

    public RiskClient(DownstreamRestClient http, WorkflowClientProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public Assessment assess(RiskRequest request) {
        return http.post(properties.getRisk(), "/api/risk/assess", request,
                new ParameterizedTypeReference<ApiResponse<Assessment>>() {
                });
    }

    /** 风险评分请求，字段与风险服务契约保持一致。 */
    public record RiskRequest(
            String customerNo,
            CustomerProfile profile,
            BigDecimal transactionAmount,
            String transactionType,
            boolean crossBorder,
            int recentTransactionCount,
            boolean newRelationship,
            boolean highRiskProduct) {
    }

    /** 风险评分响应。 */
    public record Assessment(
            String assessmentNo,
            String customerNo,
            int score,
            String level,
            List<Factor> factors,
            String recommendation) {
    }

    /** 单个风险因子。 */
    public record Factor(String code, String label, int points, String description) {
    }
}
