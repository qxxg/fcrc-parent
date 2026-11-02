package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.domain.CustomerProfile;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 合规域客户端。
 *
 * <p>合规筛查与风险评分并行执行。并行意味着两个节点只能依赖 {@code load_customer}
 * 已写入的状态，不能互相读取对方的输出——这是工作流设计里必须明确的边界。
 * 因此本请求不携带风险等级，合规引擎独立给出结论，聚合节点再综合判断。</p>
 */
@Component
public class ComplianceClient {

    private final DownstreamRestClient http;
    private final WorkflowClientProperties properties;

    public ComplianceClient(DownstreamRestClient http, WorkflowClientProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public Screening screen(ScreenRequest request) {
        return http.post(properties.getCompliance(), "/api/compliance/screen", request,
                new ParameterizedTypeReference<ApiResponse<Screening>>() {
                });
    }

    /** 合规筛查请求。 */
    public record ScreenRequest(
            String customerNo,
            CustomerProfile profile,
            BigDecimal transactionAmount,
            String transactionType,
            boolean crossBorder,
            String sourceOfFunds,
            String businessPurpose,
            boolean identityDocumentVerified,
            boolean beneficialOwnerVerified,
            boolean adverseMediaFlag,
            String riskLevel) {
    }

    /** 合规筛查响应。 */
    public record Screening(
            String screeningNo,
            String customerNo,
            String result,
            int riskPoints,
            List<Finding> findings,
            String recommendation) {
    }

    /** 合规发现项。 */
    public record Finding(String type, String severity, String description) {
    }
}
