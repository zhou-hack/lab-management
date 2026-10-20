import { defineStore } from 'pinia'
import { loginByPassword, logout as apiLogout, getProfile } from '@/api/user'
import { getToken, setToken, setRefreshToken, removeToken } from '@/utils/token'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: null, // { userId, realName, userType }
    roles: [],
    permissions: []
  }),
  actions: {
    /** 账密登录：{ accountName, password } → 双 token 入库 */
    async login(params) {
      const data = await loginByPassword({ accountName: params.accountName, password: params.password })
      this.token = data.accessToken
      setToken(data.accessToken)
      setRefreshToken(data.refreshToken)
      this.user = { userId: data.userId, realName: data.realName, userType: data.userType }
      this.roles = data.roles || []
    },
    async fetchProfile() {
      const data = await getProfile()
      this.user = { userId: data.userId, realName: data.realName, userType: data.userType }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 网络异常时忽略注销接口失败，前端状态照常清理
      }
      this.resetState()
    },
    resetOn401() {
      this.resetState()
    },
    resetState() {
      removeToken()
      this.token = ''
      this.user = null
      this.roles = []
      this.permissions = []
    }
  }
})
