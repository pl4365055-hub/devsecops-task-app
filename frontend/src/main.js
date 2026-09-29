import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { initAuth } from './auth/index.js'

// 先完成認證初始化（決定 local / sso），再掛載應用
initAuth()
  .catch(() => {})
  .finally(() => {
    createApp(App).use(router).use(ElementPlus).mount('#app')
  })
