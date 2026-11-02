package com.fcrc.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.common.api.PageResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import com.fcrc.report.domain.RegulatoryReportEntity;
import com.fcrc.report.dto.GenerateReportRequest;
import com.fcrc.report.dto.ReportResponse;
import com.fcrc.report.dto.ReportResponse.ReportContent;
import com.fcrc.report.dto.ReportStats;
import com.fcrc.report.repository.RegulatoryReportRepository;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 监管报告应用服务。 */
@Service
public class ReportService {

    private final RegulatoryReportRepository repository;
    private final ObjectMapper objectMapper;

    public ReportService(RegulatoryReportRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportResponse generate(GenerateReportRequest request) {
        RegulatoryReportEntity entity = new RegulatoryReportEntity();
        entity.setReportNo(BusinessIds.next("RPT"));
        entity.setCustomerNo(request.customerNo());
        entity.setWorkflowNo(request.workflowNo());
        entity.setReportType(request.reportType());
        entity.setPeriod(request.period() == null ? YearMonth.now().toString() : request.period());
        entity.setDecision(request.decision());
        entity.setRiskScore(request.riskScore());
        entity.setRiskLevel(request.riskLevel());
        entity.setComplianceResult(request.complianceResult());
        ReportContent content = new ReportContent(
                request.riskFactors() == null ? List.of() : List.copyOf(request.riskFactors()),
                request.complianceFindings() == null ? List.of() : List.copyOf(request.complianceFindings()),
                request.narrative());
        entity.setContentJson(write(content));
        entity.setSummary(buildSummary(request));
        entity.setStatus("DRAFT");
        repository.save(entity);
        return toResponse(entity, content);
    }

    @Transactional(readOnly = true)
    public PageResult<ReportResponse> search(String customerNo, String reportType, String status,
                                             String period, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Specification<RegulatoryReportEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(customerNo)) {
                predicates.add(cb.equal(root.get("customerNo"), customerNo.trim()));
            }
            if (notBlank(reportType)) {
                predicates.add(cb.equal(root.get("reportType"), reportType.trim()));
            }
            if (notBlank(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (notBlank(period)) {
                predicates.add(cb.equal(root.get("period"), period.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<RegulatoryReportEntity> result = repository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(String reportNo) {
        return toResponse(require(reportNo));
    }

    @Transactional
    public ReportResponse finalizeReport(String reportNo) {
        RegulatoryReportEntity entity = require(reportNo);
        if ("SUBMITTED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已报送报告不能再次定稿");
        }
        entity.setStatus("FINAL");
        entity.setFinalizedAt(Instant.now());
        return toResponse(entity);
    }

    @Transactional
    public ReportResponse submit(String reportNo) {
        RegulatoryReportEntity entity = require(reportNo);
        if (!"FINAL".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "只有已定稿报告才能报送");
        }
        entity.setStatus("SUBMITTED");
        entity.setSubmittedAt(Instant.now());
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String reportNo) {
        ReportResponse report = get(reportNo);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("报告编号,客户编号,工作流编号,报告类型,报告期间,审查结论,风险分,风险等级,合规结果,报告状态,摘要\n");
        csv.append(csv(report.reportNo())).append(',')
                .append(csv(report.customerNo())).append(',')
                .append(csv(report.workflowNo())).append(',')
                .append(csv(report.reportType())).append(',')
                .append(csv(report.period())).append(',')
                .append(csv(report.decision())).append(',')
                .append(report.riskScore() == null ? "" : report.riskScore()).append(',')
                .append(csv(report.riskLevel())).append(',')
                .append(csv(report.complianceResult())).append(',')
                .append(csv(report.status())).append(',')
                .append(csv(report.summary())).append('\n');
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public ReportStats stats() {
        return new ReportStats(repository.count(), repository.countByStatus("DRAFT"),
                repository.countByStatus("FINAL"), repository.countByStatus("SUBMITTED"),
                repository.countByReportType("SAR"), repository.countByReportType("CTR"));
    }

    private String buildSummary(GenerateReportRequest request) {
        StringBuilder summary = new StringBuilder();
        summary.append("客户 ").append(request.customerNo())
                .append(" 于报告期间 ").append(request.period() == null ? YearMonth.now() : request.period())
                .append(" 的审查结论为 ").append(request.decision()).append("。");
        if (request.riskScore() != null) {
            summary.append("风险评分 ").append(request.riskScore())
                    .append("（").append(request.riskLevel() == null ? "UNKNOWN" : request.riskLevel())
                    .append("）。");
        }
        if (request.complianceResult() != null) {
            summary.append("合规筛查结果 ").append(request.complianceResult()).append("。");
        }
        if (request.riskFactors() != null && !request.riskFactors().isEmpty()) {
            summary.append("主要风险因子：").append(String.join("、", request.riskFactors())).append("。");
        }
        if (request.complianceFindings() != null && !request.complianceFindings().isEmpty()) {
            summary.append("合规发现：").append(String.join("、", request.complianceFindings())).append("。");
        }
        if (request.narrative() != null && !request.narrative().isBlank()) {
            summary.append("补充说明：").append(request.narrative());
        }
        return summary.length() > 1900 ? summary.substring(0, 1900) : summary.toString();
    }

    private ReportResponse toResponse(RegulatoryReportEntity entity) {
        return toResponse(entity, readContent(entity.getContentJson()));
    }

    private ReportResponse toResponse(RegulatoryReportEntity entity, ReportContent content) {
        return ReportResponse.from(entity, content);
    }

    private RegulatoryReportEntity require(String reportNo) {
        return repository.findByReportNo(reportNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在: " + reportNo));
    }

    private String write(ReportContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            throw new IllegalStateException("报告内容序列化失败", ex);
        }
    }

    private ReportContent readContent(String json) {
        try {
            return objectMapper.readValue(json, ReportContent.class);
        } catch (Exception ex) {
            return new ReportContent(List.of(), List.of(), null);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
