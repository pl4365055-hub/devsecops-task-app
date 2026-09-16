import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: 自动附加 JWT
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  const isLoginRequest = config.url?.endsWith('/auth/login')
  if (token && !isLoginRequest) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor: 401/403 时跳回登录
api.interceptors.response.use(
  response => response,
  error => {
    if ([401, 403].includes(error.response?.status)) {
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default api