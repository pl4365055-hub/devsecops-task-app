import { createRouter, createWebHistory } from 'vue-router'
import { login, isAuthenticated } from '../auth/index.js'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import TaskList from '../views/TaskList.vue'
import Users from '../views/Users.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard, meta: { requiresAuth: true } },
  { path: '/tasks', component: TaskList, meta: { requiresAuth: true } },
  {
    path: '/users',
    component: Users,
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  { path: '/', redirect: '/login' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const authenticated = isAuthenticated()

  if (to.meta.requiresAuth && !authenticated) {
    // SSO 模式直接跳轉 Keycloak 登入頁；local 模式回表單頁
    return login() ?? '/login'
  }

  if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
    return '/dashboard'
  }
})

export default router
