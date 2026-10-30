package com.labmanage.roles.vo;

/**
 * 角色下用户名单项（角色权限页"人员名单"查看详情）。
 * <p>
 * ASSISTANT 角色不做持久化绑定，其名单由有效助理授权动态计算得出。
 *
 * @param id             用户 ID（字符串，避免 JS 精度损失）
 * @param userType       用户类型：STUDENT/TEACHER/ADMIN
 * @param realName       姓名
 * @param accountName    登录账号
 * @param personNo       学工号
 * @param departmentName 所属部门名（无部门为 null）
 * @param enabled        账号状态：1 启用 / 0 停用
 */
public record RoleUserItemVO(
        String id,
        String userType,
        String realName,
        String accountName,
        String personNo,
        String departmentName,
        Integer enabled
) {
}
