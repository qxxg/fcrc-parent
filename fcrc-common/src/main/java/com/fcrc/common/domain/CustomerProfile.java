package com.fcrc.common.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 客户画像（跨服务契约）。
 *
 * <p>工作流、风险引擎、合规筛查与智能体工具都使用同一份画像，避免各服务对字段含义产生分歧。
 * 真实项目中建议使用 Avro/Protobuf 管理跨服务契约；教学项目使用 Java record 保持可读性。</p>
 *
 * <p>敏感字段（身份证号、手机号）在日志与智能体上下文中必须脱敏，参见
 * {@code PiiMaskingMiddleware}。</p>
 */
public record CustomerProfile(
        @NotBlank(message = "客户姓名不能为空") String name,
        @NotBlank(message = "客户类型不能为空") String customerType,
        String idNumber,
        String phone,
        @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "国家/地区不能为空") String country,
        String industry,
        @PositiveOrZero(message = "年收入不能为负数") BigDecimal annualIncome,
        String occupation,
        boolean pep,
        String channel) {

    /** 便于调用方在缺省渠道时得到稳定值。 */
    public String channelOrDefault() {
        return channel == null || channel.isBlank() ? "WEB" : channel;
    }

    /** 返回用于展示的脱敏姓名（保留首尾字符）。 */
    public String maskedName() {
        if (name == null || name.length() <= 1) {
            return name == null ? "" : "*";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "**" + name.charAt(name.length() - 1);
    }
}
