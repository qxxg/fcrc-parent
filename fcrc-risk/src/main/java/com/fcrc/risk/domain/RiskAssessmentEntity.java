package com.fcrc.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 风险评估留痕。
 *
 * <p>风控结果不能只返回给调用方就结束：监管检查时需要回答“当时为什么给出这个等级”。
 * 因此把完整因子快照保存为 JSON，后续规则升级也不会改变历史结论。</p>
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String assessmentNo;

    @Column(nullable = false, length = 32)
    private String customerNo;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 16)
    private String level;

    /** 因子快照 JSON，包含 code/label/points/description。 */
    @Lob
    @Column(nullable = false)
    private String factorsJson;

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

    public String getAssessmentNo() {
        return assessmentNo;
    }

    public void setAssessmentNo(String assessmentNo) {
        this.assessmentNo = assessmentNo;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getFactorsJson() {
        return factorsJson;
    }

    public void setFactorsJson(String factorsJson) {
        this.factorsJson = factorsJson;
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
