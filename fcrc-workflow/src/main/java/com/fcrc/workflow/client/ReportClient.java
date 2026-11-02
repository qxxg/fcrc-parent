package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 报告域客户端。
 *
 * <p>流程结束时生成监管报告草稿并立即定稿。报告内容来自工作流状态快照，
 * 而不是查询时临时拼接，保证“当时为什么批准/拒绝”在事后可还原。</p>
 *
 * <p>注意：生成与定稿是两个事务。若定稿失败，报告仍以 DRAFT 存在，
 * 工作流会因异常标记失败，人工可修正后重新定稿，而不会丢失证据。</p>
 */
@Component
public class ReportClient {

    private final DownstreamRestClient http;
    private final WorkflowClientProperties properties;

    public ReportClient(DownstreamRestClient http, WorkflowClientProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public String generateAndFinalize(GenerateReport request) {
        Map<String, Object> draft = http.post(properties.getReport(), "/api/reports", request,
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        String reportNo = draft == null ? null : String.valueOf(draft.get("reportNo"));
        if (reportNo == null || "null".equals(reportNo)) {
            throw new IllegalStateException("报告服务未返回 reportNo");
        }
        http.post(properties.getReport(), "/api/reports/" + reportNo + "/finalize", Map.of(),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        return reportNo;
    }

    /** 生成监管报告请求。 */
    public record GenerateReport(
            String customerNo,
            String workflowNo,
            String reportType,
            String period,
            String decision,
            Integer riskScore,
            String riskLevel,
            String complianceResult,
            List<String> riskFactors,
            List<String> complianceFindings,
            String narrative) {
    }
}
