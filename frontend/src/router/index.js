import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import TaskList from '../views/TaskList.vue'
import Users from '../views/Users.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard },
  { path: '/tasks', component: TaskList },
  { path: '/users', component: Users, meta: { requiresAdmin: true } },
  { path: '/', redirect: '/login' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
    return '/dashboard'
  }
})

export default router
