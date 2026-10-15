import { JSEncrypt } from 'jsencrypt'
import request from '@/api/request'

/**
 * 口令传输加密：提交前从后端获取 RSA 公钥（X.509 Base64），
 * 用公钥把明文口令加密为 Base64 密文再上传，明文不出现在请求报文中。
 * 每次提交都取新公钥：后端重启即轮换密钥，不缓存可避免旧密文失效。
 * 传输层安全仍以 HTTPS 为准，本机制解决明文口令落报文/日志的问题。
 */

/** 获取当前口令加密公钥（每次调用重新拉取） */
async function fetchPublicKey() {
  const data = await request.get('/auth/public_key')
  return data.publicKey
}

/** 明文口令 → RSA/2048 PKCS#1 v1.5 Base64 密文；空值原样返回 */
export async function encryptPassword(plain) {
  if (!plain) return plain
  const publicKey = await fetchPublicKey()
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const cipher = encryptor.encrypt(plain)
  if (!cipher) {
    throw new Error('密码加密失败，请刷新后重试')
  }
  return cipher
}
