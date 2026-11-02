package com.fcrc.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 新建客户请求。
 *
 * <p>API 入参和数据库实体分离：调用方不能注入 kycStatus、riskLevel 等由系统计算的字段，
 * 从而避免“前端直接改成低风险”这一类越权写入。</p>
 */
public record CreateCustomerRequest(
        @NotBlank(message = "客户姓名/机构名称不能为空")
        @Size(max = 128, message = "名称长度不能超过 128")
        String name,

        @NotBlank(message = "客户类型不能为空")
        @Pattern(regexp = "INDIVIDUAL|CORPORATE", message = "客户类型只能为 INDIVIDUAL 或 CORPORATE")
        String customerType,

        @Size(max = 64, message = "证件号长度不能超过 64")
        String idNumber,

        @Size(max = 32, message = "手机号长度不能超过 32")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        @NotBlank(message = "国家/地区不能为空")
        @Size(max = 64, message = "国家/地区长度不能超过 64")
        String country,

        @Size(max = 64, message = "行业长度不能超过 64")
        String industry,

        @PositiveOrZero(message = "年收入不能为负数")
        BigDecimal annualIncome,

        @Size(max = 64, message = "职业长度不能超过 64")
        String occupation,

        boolean pep,

        @Pattern(regexp = "WEB|APP|BRANCH|PARTNER", message = "渠道非法")
        String channel) {
}
