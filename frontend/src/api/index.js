import axios from 'axios'
import { isSso, logout, refreshToken } from '../auth/index.js'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: 自動附加 JWT
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  const isAuthEndpoint = config.url?.includes('/auth/')
  if (token && !isAuthEndpoint) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor：SSO 模式嘗試刷新 token；401 時登出並回到登入流程
let isRefreshing = false

api.interceptors.response.use(
  response => response,
  async error => {
    const status = error.response?.status
    const isAuthEndpoint = error.config?.url?.includes('/auth/')

    if (status === 401 && !isAuthEndpoint && isSso() && !isRefreshing) {
      isRefreshing = true
      try {
        const ok = await refreshToken()
        if (ok) {
          // 以新 token 重放原請求
          error.config.headers.Authorization =
            `Bearer ${localStorage.getItem('token')}`
          return api(error.config)
        }
      } catch {
        // fall through to logout
      } finally {
        isRefreshing = false
      }
    }

    if (status === 401) {
      if (isSso()) {
        await logout()
      } else {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        localStorage.removeItem('role')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
    }

    return Promise.reject(error)
  }
)

export default api
