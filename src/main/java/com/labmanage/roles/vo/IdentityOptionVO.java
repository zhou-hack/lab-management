package com.labmanage.roles.vo;

/**
 * 身份选项（ProfileVO.identities，身份一键切换下拉数据源）。
 * code 为角色码（ADMIN/TEACHER/STUDENT/ASSISTANT）；主身份恒在列表首位；
 * 无授权 / 单身份用户列表仅一项，前端不显示切换入口。
 */
public record IdentityOptionVO(
        String code,
        String label,
        String description
) {
}