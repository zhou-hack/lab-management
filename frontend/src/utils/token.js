const ACCESS_KEY = 'lab_access_token'
const REFRESH_KEY = 'lab_refresh_token'

export function getToken() {
  return sessionStorage.getItem(ACCESS_KEY)
}

export function setToken(token) {
  sessionStorage.setItem(ACCESS_KEY, token)
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function setRefreshToken(token) {
  sessionStorage.setItem(REFRESH_KEY, token)
}

export function removeToken() {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}
