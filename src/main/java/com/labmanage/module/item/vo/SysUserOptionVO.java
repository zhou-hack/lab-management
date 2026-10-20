package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户候选选项 VO（保管人选择等场景；跨模块只读引用 sys_user）
 */
@Schema(description = "用户候选选项")
public record SysUserOptionVO(
        @Schema(description = "用户 ID", example = "1234567890123456789")
        String userId,

        @Schema(description = "姓名", example = "张老师")
        String realName,

        @Schema(description = "登录账号（可空）", example = "admin")
        String accountName,

        @Schema(description = "学工号（可空）", example = "T1001")
        String personNo
) {
}
