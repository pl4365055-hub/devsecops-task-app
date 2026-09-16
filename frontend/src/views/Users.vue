<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/index.js'

const users = ref([])
const loading = ref(false)
const errorMessage = ref('')

const loadUsers = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const { data } = await api.get('/users')
    users.value = data
  } catch (error) {
    errorMessage.value = error.response?.status === 403
      ? '只有管理员可以查看用户列表'
      : '用户列表加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

const removeUser = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${user.username}」吗？`,
      '删除用户',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )

    await api.delete(`/users/${user.id}`)
    users.value = users.value.filter((item) => item.id !== user.id)
    ElMessage.success('用户已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.response?.status === 403 ? '只有管理员可以删除用户' : '删除用户失败')
  }
}

const formatDate = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

onMounted(loadUsers)
</script>

<template>
  <main class="users-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">ACCESS CONTROL</p>
        <h1>用户列表</h1>
        <p class="muted">查看团队成员与账号角色。</p>
      </div>
      <el-button :loading="loading" @click="loadUsers">重新加载</el-button>
    </header>

    <el-alert
      v-if="errorMessage"
      class="page-alert"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="users" stripe empty-text="目前没有用户">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="username" label="用户名" min-width="180" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建立时间" min-width="220">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<style scoped>
.users-page {
  min-height: 100vh;
  padding: clamp(28px, 5vw, 64px) clamp(20px, 7vw, 96px);
  box-sizing: border-box;
  background: #f4f7f5;
}

.page-header {
  max-width: 1120px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 0 auto 28px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #23785d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

h1 {
  margin: 0 0 8px;
  color: #18332a;
  font-size: clamp(30px, 4vw, 46px);
}

.muted {
  margin: 0;
  color: #718078;
}

.page-alert,
.table-card {
  max-width: 1120px;
  margin-right: auto;
  margin-left: auto;
}

.page-alert {
  margin-bottom: 16px;
}

.table-card {
  border: 1px solid #dce5df;
  border-radius: 8px;
}

@media (max-width: 600px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
