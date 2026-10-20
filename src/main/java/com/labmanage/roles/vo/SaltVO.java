package com.labmanage.roles.vo;

/**
 * 账号 BCrypt 盐响应（登录前按账号预取）。
 *
 * @param salt 该账号密码散列的完整盐前缀（$2a$10$22字符，共 29 位），
 *             前端用它对明文密码做 bcrypt 得到与库中一致的哈希
 */
public record SaltVO(String salt) {
}
