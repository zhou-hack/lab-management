import { useUserStore } from '@/store/user'

/**
 * 权限判断纯函数：value 为权限标识或数组，任一命中即通过；空值放行。
 * 仅作界面体验控制，接口鉴权以后端 Sa-Token 为准。
 */
export function hasPermission(value, perms) {
  if (!value || (Array.isArray(value) && value.length === 0)) return true
  const required = Array.isArray(value) ? value : [value]
  return required.some((p) => perms.includes(p))
}

export const permission = {
  mounted(el, binding) {
    const store = useUserStore()
    if (!hasPermission(binding.value, store.permissions)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
