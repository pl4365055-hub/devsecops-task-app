<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/index.js'

const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref()
const form = reactive({ id: null, title: '', description: '', status: 'PENDING', priority: 'MEDIUM', assigneeId: null })
const rules = {
  title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  owner: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
}

const tasks = ref([])
const loading = ref(false)
const errorMessage = ref('')

const resetForm = () => {
  Object.assign(form, { id: null, title: '', description: '', status: 'PENDING', priority: 'MEDIUM', assigneeId: null })
}

const openCreate = () => {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (task) => {
  isEditing.value = true
  Object.assign(form, task)
  dialogVisible.value = true
}

const saveTask = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    const payload = {
      title: form.title,
      description: form.description,
      status: form.status,
      priority: form.priority,
      assigneeId: form.assigneeId || null,
    }

    if (isEditing.value) {
      const { data } = await api.put(`/tasks/${form.id}`, payload)
      const index = tasks.value.findIndex((task) => task.id === form.id)
      if (index !== -1) tasks.value[index] = data
    } else {
      const { data } = await api.post('/tasks', payload)
      tasks.value.push(data)
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存任务失败')
  }
}

const removeTask = async (task) => {
  try {
    await api.delete(`/tasks/${task.id}`)
    tasks.value = tasks.value.filter((item) => item.id !== task.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '删除任务失败')
  }
}

const statusLabel = (status) => ({ IN_PROGRESS: '进行中', COMPLETED: '已完成', PENDING: '待处理' })[status] || status
const priorityLabel = (priority) => ({ HIGH: '高', MEDIUM: '普通', LOW: '低' })[priority] || priority
const statusType = (status) => ({ IN_PROGRESS: 'warning', COMPLETED: 'success', PENDING: 'info' })[status]
const priorityType = (priority) => (priority === 'HIGH' ? 'danger' : 'info')

const loadTasks = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await api.get('/tasks')
    tasks.value = data
  } catch (error) {
    errorMessage.value = error.response?.status === 403
      ? '没有权限查看任务列表'
      : '任务列表加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)
</script>

<template>
  <main class="task-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">WORK ITEMS</p>
        <h1>任务列表</h1>
        <p class="muted">集中查看、编辑和追踪团队任务。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增任务</el-button>
    </header>

    <el-alert v-if="errorMessage" class="page-alert" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tasks" stripe empty-text="目前没有任务">
        <el-table-column prop="title" label="任务名称" min-width="260" />
        <el-table-column prop="assigneeId" label="负责人 ID" width="130" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="120">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" effect="plain">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑任务' : '新增任务'" width="min(92vw, 520px)">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="任务名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：完成安全扫描" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="请输入任务描述" />
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="form.assigneeId" :min="1" :step="1" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="待处理" value="PENDING" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="已完成" value="COMPLETED" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="低" value="LOW" />
              <el-option label="普通" value="MEDIUM" />
              <el-option label="高" value="HIGH" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.task-page {
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

.table-card {
  max-width: 1120px;
  margin: 0 auto;
  border: 1px solid #dce5df;
  border-radius: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 600px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
