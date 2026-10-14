package com.labmanage.roles.auth.vo;

/**
 * 登录结果
 *
 * @param token 会话令牌（前端以 Authorization: Bearer 携带）
 */
public record LoginVO(String token) {
}
