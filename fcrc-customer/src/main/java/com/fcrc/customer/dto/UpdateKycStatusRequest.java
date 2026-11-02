package com.fcrc.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** KYC 状态变更请求，仅允许合规人员通过受控接口调用。 */
public record UpdateKycStatusRequest(
        @NotBlank(message = "KYC 状态不能为空")
        @Pattern(regexp = "PENDING|IN_REVIEW|APPROVED|REJECTED", message = "KYC 状态非法")
        String status,

        @Size(max = 1000, message = "备注长度不能超过 1000")
        String notes) {
}
