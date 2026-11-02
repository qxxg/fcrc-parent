package com.fcrc.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** 合规筛查留痕，保存结论、分值、发现项快照与建议。 */
@Entity
@Table(name = "compliance_screenings")
public class ScreeningEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String screeningNo;

    @Column(nullable = false, length = 32)
    private String customerNo;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(nullable = false)
    private int riskPoints;

    @Lob
    @Column(nullable = false)
    private String findingsJson;

    @Column(nullable = false, length = 1000)
    private String recommendation;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getScreeningNo() {
        return screeningNo;
    }

    public void setScreeningNo(String screeningNo) {
        this.screeningNo = screeningNo;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getRiskPoints() {
        return riskPoints;
    }

    public void setRiskPoints(int riskPoints) {
        this.riskPoints = riskPoints;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
