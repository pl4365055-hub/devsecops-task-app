import { createRouter, createWebHistory } from 'vue-router'
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
  const isAuthenticated = Boolean(localStorage.getItem('token'))

  if (to.meta.requiresAuth && !isAuthenticated) {
    return '/login'
  }

  if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
    return '/dashboard'
  }
})

export default router
