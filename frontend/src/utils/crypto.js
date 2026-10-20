import bcrypt from 'bcryptjs'

/**
 * 口令传输保护：登录前按账号从后端取该账号的 BCrypt 盐前缀
 * （库存哈希的前 29 位），前端用同一盐对明文口令复算 BCrypt，
 * 得到的 60 位哈希与库中一致后上传，服务端做常量时间比对。
 * 全程不传输明文口令；传输层安全仍以 HTTPS 为准。
 */

/** 明文口令 + BCrypt 盐前缀 → 60 位 BCrypt 哈希（与后端库存值一致） */
export function hashPassword(plain, salt) {
  if (!plain || !salt) return plain
  return bcrypt.hashSync(plain, salt)
}
