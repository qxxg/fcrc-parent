package com.fcrc.customer.domain;

import com.fcrc.common.domain.CustomerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 客户主数据实体。
 *
 * <p>教学说明：真实金融系统必须做到“最小必要”保存 PII（个人可识别信息）。
 * 本项目为了让流程可演示，数据库保存了完整字段，但所有对外 API 默认脱敏；
 * 生产环境还应叠加字段级加密（KMS）、访问审计、数据分级与留存期限策略。</p>
 */
@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外业务编号，避免暴露数据库自增 ID。 */
    @Column(nullable = false, unique = true, length = 32)
    private String customerNo;

    @Column(nullable = false, length = 128)
    private String name;

    /** INDIVIDUAL / CORPORATE。 */
    @Column(nullable = false, length = 16)
    private String customerType;

    @Column(length = 64)
    private String idNumber;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(nullable = false, length = 64)
    private String country;

    @Column(length = 64)
    private String industry;

    @Column(precision = 18, scale = 2)
    private BigDecimal annualIncome;

    @Column(length = 64)
    private String occupation;

    /** 政治公众人物，反洗钱高风险因子。 */
    @Column(nullable = false)
    private boolean pep;

    @Column(length = 16)
    private String channel;

    /** PENDING / IN_REVIEW / APPROVED / REJECTED。 */
    @Column(nullable = false, length = 16)
    private String kycStatus = "PENDING";

    /** NOT_STARTED / RUNNING / MANUAL_REVIEW / COMPLETED / REJECTED。 */
    @Column(nullable = false, length = 24)
    private String onboardingStatus = "NOT_STARTED";

    /** 最新一次风险评估等级，便于列表页展示。 */
    @Column(length = 16)
    private String riskLevel;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** 乐观锁：防止两名合规官并发覆盖状态。 */
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

    /** 转换为跨服务共享的领域契约，避免把 JPA 实体泄露给其他服务。 */
    public CustomerProfile toProfile() {
        return new CustomerProfile(name, customerType, idNumber, phone, email, country,
                industry, annualIncome, occupation, pep, channel);
    }

    public Long getId() {
        return id;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public BigDecimal getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(BigDecimal annualIncome) {
        this.annualIncome = annualIncome;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public boolean isPep() {
        return pep;
    }

    public void setPep(boolean pep) {
        this.pep = pep;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
