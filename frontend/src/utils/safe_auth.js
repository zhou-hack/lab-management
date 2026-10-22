import { ElMessageBox } from 'element-plus'
import { confirmPassword } from '@/api/user'

/**
 * 敏感操作二级认证包装（对应后端 SecurityUtils.checkSafe 校验点）：
 * 1. 先执行 action；后端返回 40101（需要二级认证）时弹出登录密码确认框；
 * 2. 确认通过（300 秒二级认证窗口）后自动重试一次 action；
 * 3. 返回 { ok, data }：用户取消确认时 ok=false，页面不得再提示操作成功；
 *    其他错误（含确认密码错误）原样抛出，由拦截器统一提示。
 */
export async function withSafeAuth(action) {
  try {
    return { ok: true, data: await action() }
  } catch (err) {
    if (!err || !err.safeAuthRequired) throw err
  }
  let password
  try {
    const res = await ElMessageBox.prompt('该操作需要二次密码确认，确认后 5 分钟内免再次确认', '敏感操作确认', {
      inputType: 'password',
      inputPlaceholder: '请输入登录密码',
      inputValidator: (v) => (v && v.length > 0) || '请输入登录密码',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    password = res.value
  } catch {
    return { ok: false }
  }
  await confirmPassword(password)
  return { ok: true, data: await action() }
}
