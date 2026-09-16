<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, List, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const activeMenu = computed(() => route.path)
const isCollapsed = ref(false)

const handleMenuSelect = (path) => {
  if (path === '/logout') {
    router.push('/login')
    return
  }
  router.push(path)
}
</script>

<template>
  <div class="dashboard-shell">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header">
        <strong v-if="!isCollapsed">TASK / OPS</strong>
        <strong v-else>TO</strong>
        <el-button text class="collapse-button" :aria-label="isCollapsed ? '展开菜单' : '收起菜单'" @click="isCollapsed = !isCollapsed">
          {{ isCollapsed ? '›' : '‹' }}
        </el-button>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapsed" @select="handleMenuSelect">
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <template #title>儀表板</template>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon>
          <template #title>任務列表</template>
        </el-menu-item>
        <el-menu-item index="/logout">
          <el-icon><SwitchButton /></el-icon>
          <template #title>退出登录</template>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="dashboard-content">
      <div class="welcome-banner">
        <p class="eyebrow">WORKSPACE OVERVIEW</p>
        <h1>欢迎回来，团队成员</h1>
        <p>从这里查看项目进度，并快速进入你的任务清单。</p>
        <el-button type="primary" @click="router.push('/tasks')">查看任务</el-button>
      </div>

      <section class="stats-grid" aria-label="任务概览">
        <el-card shadow="never"><span>进行中</span><strong>08</strong></el-card>
        <el-card shadow="never"><span>已完成</span><strong>24</strong></el-card>
        <el-card shadow="never"><span>待处理</span><strong>05</strong></el-card>
      </section>
    </main>
  </div>
</template>

<style scoped>
.dashboard-shell {
  min-height: 100vh;
  display: flex;
  background: #f4f7f5;
}

.sidebar {
  width: 248px;
  flex: 0 0 248px;
  background: #18332a;
  transition: width 0.2s, flex-basis 0.2s;
}

.sidebar.collapsed {
  width: 64px;
  flex-basis: 64px;
}

.sidebar-header {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px 0 24px;
  box-sizing: border-box;
  color: #ffffff;
  letter-spacing: 1.2px;
}

.collapse-button {
  color: #b9cec4;
  font-size: 24px;
}

.el-menu {
  border-right: 0;
  background: transparent;
}

:deep(.el-menu-item) {
  color: #b9cec4;
}

:deep(.el-menu-item:hover),
:deep(.el-menu-item.is-active) {
  color: #ffffff;
  background: #24513f;
}

.dashboard-content {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 56px clamp(24px, 6vw, 80px);
  box-sizing: border-box;
}

.welcome-banner {
  padding: clamp(28px, 5vw, 60px);
  border-radius: 8px;
  color: #ffffff;
  background: #23785d;
}

.eyebrow {
  margin: 0 0 16px;
  color: #b9e2cf;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

h1 {
  margin: 0 0 12px;
  font-size: clamp(28px, 4vw, 46px);
}

.welcome-banner p:not(.eyebrow) {
  margin-bottom: 28px;
  color: #d9f0e4;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 20px;
}

.stats-grid .el-card {
  border: 1px solid #dce5df;
}

.stats-grid span,
.stats-grid strong {
  display: block;
}

.stats-grid span {
  color: #718078;
  font-size: 14px;
}

.stats-grid strong {
  margin-top: 12px;
  color: #18332a;
  font-size: 32px;
}

@media (max-width: 680px) {
  .sidebar {
    width: 64px;
    flex-basis: 64px;
  }

  .sidebar-header {
    padding: 0 18px;
  }

  .sidebar-header strong {
    display: none;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
