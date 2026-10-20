package com.labmanage.roles.vo;

import java.util.List;

/**
 * 登录/刷新响应
 *
 * @param accessToken  JWT access token（Authorization: Bearer 传递，短效）
 * @param refreshToken JWT refresh token（仅用于到期换新，不作为业务请求凭据）
 * @param expiresIn    access token 有效期（秒）
 * @param userId       用户 ID（字符串，避免 JS 精度损失）
 * @param realName     姓名
 * @param userType     用户类型：STUDENT/TEACHER/ADMIN
 * @param roles        角色码集合（含动态计算的 ASSISTANT）
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String userId,
        String realName,
        String userType,
        List<String> roles
) {
}
