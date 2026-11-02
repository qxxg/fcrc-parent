package com.fcrc.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 人工复核意见。
 *
 * <p>复核人必须实名记录，结论只允许 APPROVED / REJECTED。生产环境还应校验
 * 复核人是否具备合规官角色，并禁止申请人自审；本项目用 {@code reviewer}
 * 字段演示审计轨迹，角色校验留给统一权限中心。</p>
 */
public record ReviewRequest(
        @NotBlank(message = "复核结论不能为空")
        @Pattern(regexp = "APPROVED|REJECTED", message = "复核结论只能是 APPROVED 或 REJECTED")
        String decision,

        @NotBlank(message = "复核人不能为空")
        @Size(max = 64, message = "复核人长度不能超过 64")
        String reviewer,

        @Size(max = 1000, message = "复核意见不能超过 1000 字")
        String comment) {
}
