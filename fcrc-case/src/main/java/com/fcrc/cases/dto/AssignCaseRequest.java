package com.fcrc.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 分派案件。 */
public record AssignCaseRequest(
        @NotBlank(message = "处理人不能为空") @Size(max = 64) String assignee) {
}
