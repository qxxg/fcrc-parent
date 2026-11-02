package com.fcrc.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 更新案件状态。关闭或解决时必须填写处置结论。 */
public record UpdateCaseStatusRequest(
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "OPEN|ASSIGNED|IN_PROGRESS|PENDING_INFO|RESOLVED|CLOSED", message = "状态非法")
        String status,
        @Size(max = 2000) String resolution) {
}
