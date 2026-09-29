import Keycloak from 'keycloak-js'

/**
 * SSO 認證模組（prod）：
 * - Keycloak 連線資訊由後端 /api/auth/config 執行時期提供（build once）
 * - Keycloak 登入後把 token / 使用者資訊同步到 localStorage，沿用既有請求攔截器
 */

let keycloak = null

export async function initAuth(config) {
  keycloak = new Keycloak({
    url: config.keycloakUrl,
    realm: config.realm,
    clientId: config.clientId,
  })

  const authenticated = await keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
  })

  if (authenticated) {
    syncSession()
  }

  keycloak.onAuthSuccess = syncSession
  keycloak.onAuthRefreshSuccess = syncSession
  keycloak.onAuthLogout = clearSession

  return authenticated
}

function parseJwtPayload(token) {
  // JWT payload 是 base64url
  const base64Url = token.split('.')[1]
  const json = atob(base64Url.replace(/-/g, '+').replace(/_/g, '/'))
  return JSON.parse(decodeURIComponent(escape(json)))
}

function syncSession() {
  if (!keycloak?.token) return
  const token = keycloak.token
  const payload = parseJwtPayload(token)
  const roles = payload.realm_access?.roles ?? []
  const role = roles.includes('ADMIN') ? 'ADMIN' : 'USER'

  localStorage.setItem('token', token)
  localStorage.setItem('username', payload.preferred_username ?? '')
  localStorage.setItem('role', role)
}

function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
}

export function login() {
  return keycloak?.login({ redirectUri: window.location.origin + '/dashboard' })
}

export function logout() {
  clearSession()
  return keycloak?.logout({ redirectUri: window.location.origin + '/login' })
}

export async function refreshToken() {
  if (!keycloak) return false
  const refreshed = await keycloak.updateToken(30)
  if (refreshed) syncSession()
  return true
}

export function isSso() {
  return keycloak !== null
}

export function isAuthenticated() {
  return Boolean(keycloak?.authenticated)
}
