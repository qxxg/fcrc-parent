package com.fcrc.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * 一次开户编排运行的聚合根。
 *
 * <p>运行表只保存“业务结果”，不保存 Graph 的完整内部状态；完整状态由
 * {@link WorkflowCheckpointEntity} 承载。这样查询列表时不会反复反序列化大对象，
 * 同时保留恢复执行所需的全部信息。</p>
 */
@Entity
@Table(name = "workflow_runs")
public class WorkflowRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外业务编号，同时作为 Graph 的 threadId，保证检查点可定位。 */
    @Column(nullable = false, unique = true, length = 40)
    private String runNo;

    @Column(nullable = false, length = 40)
    private String threadId;

    @Column(nullable = false)
    private Long customerId;

    @Column(length = 32)
    private String customerNo;

    @Column(length = 128)
    private String customerName;

    /** RUNNING / WAITING_REVIEW / COMPLETED / REJECTED / FAILED。 */
    @Column(nullable = false, length = 24)
    private String status = "RUNNING";

    /** 当前或暂停所在的图节点。 */
    @Column(length = 64)
    private String currentNode;

    @Column(length = 40)
    private String riskAssessmentNo;

    private Integer riskScore;

    @Column(length = 16)
    private String riskLevel;

    @Lob
    private String riskFactorsJson;

    @Column(length = 40)
    private String complianceScreeningNo;

    @Column(length = 16)
    private String complianceResult;

    @Lob
    private String complianceFindingsJson;

    /** AUTO_APPROVE / MANUAL_REVIEW，由聚合节点给出。 */
    @Column(length = 24)
    private String preliminaryDecision;

    /** APPROVED / REJECTED，流程结束后的最终结论。 */
    @Column(length = 24)
    private String decision;

    @Column(length = 1000)
    private String decisionReason;

    /** 人工复核输入：APPROVED / REJECTED。 */
    @Column(length = 24)
    private String reviewDecision;

    @Column(length = 64)
    private String reviewer;

    @Column(length = 1000)
    private String reviewComment;

    @Column(length = 40)
    private String caseNo;

    @Column(length = 40)
    private String reportNo;

    /** 回写到客户服务的开户状态。 */
    @Column(length = 24)
    private String onboardingStatus;

    @Lob
    private String auditTrailJson;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(String currentNode) {
        this.currentNode = currentNode;
    }

    public String getRiskAssessmentNo() {
        return riskAssessmentNo;
    }

    public void setRiskAssessmentNo(String riskAssessmentNo) {
        this.riskAssessmentNo = riskAssessmentNo;
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

    public String getRiskFactorsJson() {
        return riskFactorsJson;
    }

    public void setRiskFactorsJson(String riskFactorsJson) {
        this.riskFactorsJson = riskFactorsJson;
    }

    public String getComplianceScreeningNo() {
        return complianceScreeningNo;
    }

    public void setComplianceScreeningNo(String complianceScreeningNo) {
        this.complianceScreeningNo = complianceScreeningNo;
    }

    public String getComplianceResult() {
        return complianceResult;
    }

    public void setComplianceResult(String complianceResult) {
        this.complianceResult = complianceResult;
    }

    public String getComplianceFindingsJson() {
        return complianceFindingsJson;
    }

    public void setComplianceFindingsJson(String complianceFindingsJson) {
        this.complianceFindingsJson = complianceFindingsJson;
    }

    public String getPreliminaryDecision() {
        return preliminaryDecision;
    }

    public void setPreliminaryDecision(String preliminaryDecision) {
        this.preliminaryDecision = preliminaryDecision;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getReviewDecision() {
        return reviewDecision;
    }

    public void setReviewDecision(String reviewDecision) {
        this.reviewDecision = reviewDecision;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public String getAuditTrailJson() {
        return auditTrailJson;
    }

    public void setAuditTrailJson(String auditTrailJson) {
        this.auditTrailJson = auditTrailJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
