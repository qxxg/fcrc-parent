package com.fcrc.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建合规案件。 */
public record CreateCaseRequest(
        @NotBlank(message = "客户编号不能为空") @Size(max = 32) String customerNo,
        @Size(max = 128) String customerName,
        @NotBlank(message = "案件类型不能为空")
        @Pattern(regexp = "KYC_REVIEW|SANCTIONS_HIT|ADVERSE_MEDIA|TRANSACTION_MONITORING|COMPLAINT",
                message = "案件类型非法") String type,
        @NotBlank(message = "优先级不能为空")
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "优先级非法") String priority,
        @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 64) String sourceRef) {
}
