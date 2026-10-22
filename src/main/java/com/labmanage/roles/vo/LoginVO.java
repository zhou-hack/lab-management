package com.labmanage.roles.vo;

import java.util.List;

/**
 * 登录响应
 *
 * @param token                  Sa-Token 会话令牌（经 Authorization: Bearer 传递）
 * @param userId                 用户 ID（字符串，避免 JS 精度损失）
 * @param realName               姓名
 * @param userType               用户类型：STUDENT/TEACHER/ADMIN
 * @param roles                  角色码集合（含动态计算的 ASSISTANT）
 * @param passwordChangeRequired 从未设置/被重置密码（管理员），须强制改密
 * @param needSetPassword        尚未设置密码（老师首登须设置敏感操作密码）
 */
public record LoginVO(
        String token,
        String userId,
        String realName,
        String userType,
        List<String> roles,
        Boolean passwordChangeRequired,
        Boolean needSetPassword
) {
}
