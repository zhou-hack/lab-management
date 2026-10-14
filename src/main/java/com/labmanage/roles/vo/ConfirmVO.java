package com.labmanage.roles.vo;

/**
 * 敏感操作二次确认响应（Sa-Token 二级认证已开启）
 *
 * @param validSeconds 二级认证有效期（秒），敏感接口在期内免再次确认
 */
public record ConfirmVO(Integer validSeconds) {
}
