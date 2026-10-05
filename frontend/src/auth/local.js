/**
 * local 認證模式（dev / uat）：
 * 帳號密碼表單登入，token 由 AuthController 簽發後存於 localStorage。
 * 這裡只提供與 sso 模組一致的介面，大部分邏輯仍在 Login.vue / api 攔截器。
 */

export async function initAuth() {
  return false
}

export function login() {}

export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
}

export async function refreshToken() {
  return false
}

export function isSso() {
  return false
}

export function isAuthenticated() {
  return Boolean(localStorage.getItem('token'))
}
