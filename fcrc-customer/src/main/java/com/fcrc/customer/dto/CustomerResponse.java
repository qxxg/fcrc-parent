package com.fcrc.customer.dto;

import com.fcrc.customer.domain.CustomerEntity;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 对外客户视图。
 *
 * <p>姓名、证件、手机、邮箱均已脱敏。真正需要原文的下游服务必须走内部接口，
 * 并且应在生产环境配合 mTLS、服务网格或内部令牌做服务间认证。</p>
 */
public record CustomerResponse(
        Long id,
        String customerNo,
        String name,
        String customerType,
        String idNumber,
        String phone,
        String email,
        String country,
        String industry,
        BigDecimal annualIncome,
        String occupation,
        boolean pep,
        String channel,
        String kycStatus,
        String onboardingStatus,
        String riskLevel,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getCustomerNo(),
                maskName(entity.getName()),
                entity.getCustomerType(),
                maskId(entity.getIdNumber()),
                maskPhone(entity.getPhone()),
                maskEmail(entity.getEmail()),
                entity.getCountry(),
                entity.getIndustry(),
                entity.getAnnualIncome(),
                entity.getOccupation(),
                entity.isPep(),
                entity.getChannel(),
                entity.getKycStatus(),
                entity.getOnboardingStatus(),
                entity.getRiskLevel(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static String maskName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() == 1) {
            return "*";
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "**" + value.charAt(value.length() - 1);
    }

    static String maskId(String value) {
        if (value == null || value.length() < 6) {
            return value == null ? null : "***";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }

    static String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return value == null ? null : "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    static String maskEmail(String value) {
        if (value == null || !value.contains("@")) {
            return value;
        }
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
