package com.fcrc.agent.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.agent.client.dto.CaseDetailView;
import com.fcrc.agent.client.dto.CaseView;
import com.fcrc.agent.client.dto.CustomerProfileView;
import com.fcrc.agent.client.dto.CustomerView;
import com.fcrc.agent.client.dto.PageView;
import com.fcrc.agent.client.dto.ReportView;
import com.fcrc.agent.client.dto.ResultViews;
import com.fcrc.agent.client.dto.WorkflowDetailView;
import com.fcrc.agent.client.dto.WorkflowRunView;
import com.fcrc.agent.config.AgentClientProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 下游业务服务访问客户端。
 *
 * <p><b>为什么 AI 不直接连数据库？</b> 因为数据库 schema 是业务服务的私有实现，
 * AI 直接读会产生三个问题：绕过业务校验、绕过权限、破坏服务边界。这里统一走 HTTP，
 * 与人类操作者使用同一套 API，审计口径一致。</p>
 *
 * <p><b>统一响应解包：</b> 所有服务都返回 {@code ApiResponse<T>}（success/code/message/data）。
 * 本类集中处理 {@code success=false} 与 HTTP 异常，工具层只关心 data。</p>
 *
 * <p><b>安全边界：</b> 这里只暴露只读查询、风险评估、合规筛查、启动工作流。
 * 故意不提供 {@code /review}（人工复核）接口——人工复核必须由真人完成，
 * AI 不能代替人做决定，这是本项目的合规底线。</p>
 */
@Component
public class AgentDownstreamClient {

    private static final Logger log = LoggerFactory.getLogger(AgentDownstreamClient.class);

    private final RestClient customerClient;
    private final RestClient riskClient;
    private final RestClient complianceClient;
    private final RestClient workflowClient;
    private final RestClient caseClient;
    private final RestClient reportClient;
    private final ObjectMapper objectMapper;

    public AgentDownstreamClient(AgentClientProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        this.customerClient = build(properties.getCustomer(), factory);
        this.riskClient = build(properties.getRisk(), factory);
        this.complianceClient = build(properties.getCompliance(), factory);
        this.workflowClient = build(properties.getWorkflow(), factory);
        this.caseClient = build(properties.getCases(), factory);
        this.reportClient = build(properties.getReport(), factory);
    }

    private RestClient build(String baseUrl, SimpleClientHttpRequestFactory factory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    // ------------------------------------------------------------------
    // 客户域
    // ------------------------------------------------------------------

    public CustomerView getCustomer(long customerId) {
        return get(customerClient, "/api/customers/{id}", Map.of("id", customerId), Map.of(),
                new TypeReference<CustomerView>() {
                });
    }

    public CustomerProfileView getCustomerProfile(long customerId) {
        return get(customerClient, "/internal/customers/{id}/profile", Map.of("id", customerId), Map.of(),
                new TypeReference<CustomerProfileView>() {
                });
    }

    public PageView<CustomerView> listCustomers(String keyword, String status, int page, int size) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("keyword", keyword);
        query.put("status", status);
        query.put("page", page);
        query.put("size", size);
        return get(customerClient, "/api/customers", Map.of(), query,
                new TypeReference<PageView<CustomerView>>() {
                });
    }

    // ------------------------------------------------------------------
    // 风险 / 合规域
    // ------------------------------------------------------------------

    public ResultViews.RiskView assessRisk(Map<String, Object> request) {
        return post(riskClient, "/api/risk/assess", request,
                new TypeReference<ResultViews.RiskView>() {
                });
    }

    public ResultViews.ComplianceView screenCompliance(Map<String, Object> request) {
        return post(complianceClient, "/api/compliance/screen", request,
                new TypeReference<ResultViews.ComplianceView>() {
                });
    }

    // ------------------------------------------------------------------
    // 工作流域
    // ------------------------------------------------------------------

    public WorkflowDetailView startOnboarding(Map<String, Object> request) {
        return post(workflowClient, "/api/workflows", request,
                new TypeReference<WorkflowDetailView>() {
                });
    }

    public WorkflowDetailView getWorkflow(String runNo) {
        return get(workflowClient, "/api/workflows/{runNo}", Map.of("runNo", runNo), Map.of(),
                new TypeReference<WorkflowDetailView>() {
                });
    }

    public PageView<WorkflowRunView> listWorkflows(String status, int page, int size) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("status", status);
        query.put("page", page);
        query.put("size", size);
        return get(workflowClient, "/api/workflows", Map.of(), query,
                new TypeReference<PageView<WorkflowRunView>>() {
                });
    }

    // ------------------------------------------------------------------
    // 案件域
    // ------------------------------------------------------------------

    public PageView<CaseView> listCases(String keyword, String status, String priority, int page, int size) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("keyword", keyword);
        query.put("status", status);
        query.put("priority", priority);
        query.put("page", page);
        query.put("size", size);
        return get(caseClient, "/api/cases", Map.of(), query,
                new TypeReference<PageView<CaseView>>() {
                });
    }

    public CaseDetailView getCase(String caseNo) {
        return get(caseClient, "/api/cases/{caseNo}", Map.of("caseNo", caseNo), Map.of(),
                new TypeReference<CaseDetailView>() {
                });
    }

    // ------------------------------------------------------------------
    // 报告域
    // ------------------------------------------------------------------

    public PageView<ReportView> listReports(String customerNo, String reportType, String status, int page, int size) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("customerNo", customerNo);
        query.put("reportType", reportType);
        query.put("status", status);
        query.put("page", page);
        query.put("size", size);
        return get(reportClient, "/api/reports", Map.of(), query,
                new TypeReference<PageView<ReportView>>() {
                });
    }

    public ReportView getReport(String reportNo) {
        return get(reportClient, "/api/reports/{reportNo}", Map.of("reportNo", reportNo), Map.of(),
                new TypeReference<ReportView>() {
                });
    }

    // ------------------------------------------------------------------
    // 通用 HTTP 辅助
    // ------------------------------------------------------------------

    private <T> T get(RestClient client, String path, Map<String, ?> uriVariables, Map<String, ?> query,
                      TypeReference<T> type) {
        String url = buildUrl(path, uriVariables, query);
        try {
            JsonNode root = client.get().uri(url).retrieve().body(JsonNode.class);
            return convert(root, type);
        } catch (RestClientResponseException ex) {
            throw new AgentDownstreamException(
                    "下游服务返回 HTTP " + ex.getStatusCode().value() + "：" + safeBody(ex), ex);
        } catch (AgentDownstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentDownstreamException("调用下游服务失败：" + ex.getMessage(), ex);
        }
    }

    private String buildUrl(String path, Map<String, ?> uriVariables, Map<String, ?> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (value instanceof String text && text.isBlank()) {
                    return;
                }
                builder.queryParam(key, value);
            });
        }
        return builder.buildAndExpand(uriVariables == null ? Map.of() : uriVariables)
                .encode()
                .toUriString();
    }

    private <T> T post(RestClient client, String path, Object body, TypeReference<T> type) {
        try {
            JsonNode root = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return convert(root, type);
        } catch (RestClientResponseException ex) {
            throw new AgentDownstreamException(
                    "下游服务返回 HTTP " + ex.getStatusCode().value() + "：" + safeBody(ex), ex);
        } catch (AgentDownstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AgentDownstreamException("调用下游服务失败：" + ex.getMessage(), ex);
        }
    }

    private <T> T convert(JsonNode root, TypeReference<T> type) throws Exception {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.has("success") && !root.path("success").asBoolean(true)) {
            throw new AgentDownstreamException("下游业务失败[" + root.path("code").asText() + "]："
                    + root.path("message").asText());
        }
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        log.debug("下游响应转换：target={} bytes={}", type.getType(), data.toString().length());
        return objectMapper.convertValue(data, type);
    }

    private String safeBody(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body == null || body.isBlank() ? ex.getStatusText() : body;
    }
}
