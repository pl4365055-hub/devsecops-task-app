import axios from 'axios'
import * as localAuth from './local.js'
import * as ssoAuth from './sso.js'

/**
 * 統一認證入口：啟動時依後端 /api/auth/config 決定 local 或 sso。
 */

let mode = 'local'

export async function initAuth() {
  // 偵測模式；請求失敗時預設 local，避免開發環境後端未啟動直接卡死
  let config = { mode: 'local' }
  try {
    const { data } = await axios.get('/api/auth/config')
    config = data
  } catch {
    config = { mode: 'local' }
  }

  mode = config.mode === 'sso' ? 'sso' : 'local'

  if (mode === 'sso') {
    return ssoAuth.initAuth(config)
  }
  return localAuth.initAuth()
}

export function login() {
  return mode === 'sso' ? ssoAuth.login() : localAuth.login()
}

export function logout() {
  return mode === 'sso' ? ssoAuth.logout() : localAuth.logout()
}

export function refreshToken() {
  return mode === 'sso' ? ssoAuth.refreshToken() : localAuth.refreshToken()
}

export function isSso() {
  return mode === 'sso'
}

export function isAuthenticated() {
  return mode === 'sso'
    ? ssoAuth.isAuthenticated()
    : localAuth.isAuthenticated()
}
