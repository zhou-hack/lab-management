import { ElMessageBox } from 'element-plus'

/**
 * 统一操作确认弹窗
 *
 * 用户确认返回 true，取消或关闭弹窗返回 false（不抛异常），
 * 调用方按布尔结果决定是否执行操作。
 *
 * @param {string} message 提示内容
 * @param {string} [title] 标题，默认"操作确认"
 * @param {object} [options] 透传 ElMessageBox.confirm 的额外配置
 */
export async function confirmAction(message, title = '操作确认', options = {}) {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      ...options
    })
    return true
  } catch {
    return false
  }
}
