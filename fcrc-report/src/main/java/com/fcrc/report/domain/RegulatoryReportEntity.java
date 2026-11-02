package com.fcrc.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** 监管报告快照。 */
@Entity
@Table(name = "regulatory_reports")
public class RegulatoryReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reportNo;

    @Column(nullable = false, length = 32)
    private String customerNo;

    @Column(length = 40)
    private String workflowNo;

    @Column(nullable = false, length = 32)
    private String reportType;

    @Column(length = 16)
    private String period;

    @Column(nullable = false, length = 24)
    private String decision;

    private Integer riskScore;

    @Column(length = 16)
    private String riskLevel;

    @Column(length = 16)
    private String complianceResult;

    @Lob
    @Column(nullable = false)
    private String contentJson;

    @Column(nullable = false, length = 2000)
    private String summary;

    /** DRAFT / FINAL / SUBMITTED。 */
    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant finalizedAt;

    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getWorkflowNo() {
        return workflowNo;
    }

    public void setWorkflowNo(String workflowNo) {
        this.workflowNo = workflowNo;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getComplianceResult() {
        return complianceResult;
    }

    public void setComplianceResult(String complianceResult) {
        this.complianceResult = complianceResult;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
