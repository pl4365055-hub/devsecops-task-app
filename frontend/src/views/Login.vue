<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/index.js'

const router = useRouter()
const formRef = ref()
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少需要 6 个字符', trigger: 'blur' },
  ],
}

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    const { data } = await api.post('/auth/login', form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.user.username)
    localStorage.setItem('role', data.user.role)
    await router.push('/dashboard')
  } catch (err) {
    const message = err.response?.data?.message || '登录失败，请检查用户名和密码'
    ElMessage.error(message)
  }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="never">
      <div class="brand-mark">TASK / OPS</div>
      <h1>欢迎回来</h1>
      <p class="subtitle">登录以管理你的 DevSecOps 任务</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" size="large" native-type="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  box-sizing: border-box;
  background: #f3f6f4;
}

.login-card {
  width: min(100%, 420px);
  border: 1px solid #dce5df;
  border-radius: 8px;
  background: #ffffff;
}

.brand-mark {
  color: #16745a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

h1 {
  margin: 18px 0 6px;
  color: #18332a;
  font-size: 32px;
}

.subtitle {
  margin-bottom: 28px;
  color: #718078;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  margin-top: 8px;
}
</style>
