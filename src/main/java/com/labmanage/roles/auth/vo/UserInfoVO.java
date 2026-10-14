package com.labmanage.roles.auth.vo;

/**
 * 当前用户信息
 *
 * @param userId 用户 ID（字符串化避免前端精度损失）
 * @param realName 姓名
 * @param username 登录账号
 * @param userType 用户类型：STUDENT/TEACHER/ADMIN
 */
public record UserInfoVO(
        String userId,
        String realName,
        String username,
        String userType
) {
}
