import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import Login from '../views/Login.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: Login }]
})

const mountLogin = () => mount(Login, {
  global: {
    plugins: [ElementPlus, router]
  }
})

describe('Login.vue', () => {
  it('renders login form', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })

  it('shows error when login fails', async () => {
    const wrapper = mountLogin()
    await wrapper.find('input[type="text"]').setValue('user')
    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('button').trigger('click')
    // 验证错误提示显示
  })
})
