package com.labmanage.roles.vo;

import java.util.List;

/**
 * 登录/刷新/切换身份响应
 *
 * @param accessToken  JWT access token（Authorization: Bearer 传递，短效，携带活动角色声明）
 * @param refreshToken JWT refresh token（仅用于到期换新，不作为业务请求凭据）
 * @param expiresIn    access token 有效期（秒）
 * @param userId       用户 ID（字符串，避免 JS 精度损失）
 * @param realName     姓名
 * @param userType     用户类型：STUDENT/TEACHER/ADMIN
 * @param accountName  账号名（前端二次确认前按账号取 BCrypt 盐）
 * @param roles        角色码集合（活动角色 + 动态计算的 ASSISTANT）
 * @param activeRole   活动角色（当前生效的身份视图，多角色用户可切换）
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String userId,
        String realName,
        String userType,
        String accountName,
        List<String> roles,
        String activeRole
) {
}
