package com.fcrc.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 生成监管报告请求。
 *
 * <p>报告输入是工作流的“结果快照”。一旦生成草稿，后续规则变化不能篡改历史报告，
 * 因此服务把关键字段序列化保存，而不是每次查询时重新计算。</p>
 */
public record GenerateReportRequest(
        @NotBlank(message = "客户编号不能为空") String customerNo,
        String workflowNo,
        @NotBlank(message = "报告类型不能为空")
        @Pattern(regexp = "CUSTOMER_RISK|SAR|CTR|PERIODIC_SUMMARY", message = "报告类型非法")
        String reportType,
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "报告期间格式应为 YYYY-MM")
        String period,
        @NotBlank(message = "审查结论不能为空")
        @Pattern(regexp = "APPROVED|REJECTED|MANUAL_REVIEW", message = "审查结论非法")
        String decision,
        @Min(value = 0, message = "风险分不能小于 0")
        @Max(value = 100, message = "风险分不能大于 100")
        Integer riskScore,
        String riskLevel,
        String complianceResult,
        @Size(max = 20) List<String> riskFactors,
        @Size(max = 20) List<String> complianceFindings,
        @Size(max = 2000) String narrative) {
}
