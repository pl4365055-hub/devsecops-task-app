import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import TaskList from '../views/TaskList.vue'

const routes = [
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard },
  { path: '/tasks', component: TaskList },
  { path: '/', redirect: '/login' }
]

export default createRouter({ history: createWebHistory(), routes })
