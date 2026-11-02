package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * 案件域客户端。
 *
 * <p>人工复核不是“弹一个确认框”那么简单：企业里必须留下可分派、可计时、
 * 可升级的合规案件。工作流在进入暂停前创建案件，把 runNo 写入 sourceRef，
 * 复核人从案件系统即可追溯到完整执行链路。</p>
 */
@Component
public class CaseClient {

    private final DownstreamRestClient http;
    private final WorkflowClientProperties properties;

    public CaseClient(DownstreamRestClient http, WorkflowClientProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /** 创建人工复核案件，返回案件编号。 */
    public String createReviewCase(String customerNo, String customerName, String priority,
                                   String title, String description, String sourceRef) {
        Map<String, Object> data = http.post(properties.getCases(), "/api/cases",
                new CreateCase(customerNo, customerName, "KYC_REVIEW", priority, title, description, sourceRef),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        return data == null ? null : String.valueOf(data.get("caseNo"));
    }

    /** 创建案件请求。 */
    public record CreateCase(
            String customerNo,
            String customerName,
            String type,
            String priority,
            String title,
            String description,
            String sourceRef) {
    }

    /** 仅用于展示契约字段的辅助类型（本项目不直接反序列化案件详情）。 */
    public record CaseSummary(String caseNo, String status, List<String> tags) {
    }
}
