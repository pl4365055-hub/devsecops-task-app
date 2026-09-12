# Lab 01 - Frontend 技术梳理

## 1. 技术栈总览

本项目 frontend 使用以下技术：

| 技术 | 作用 |
| --- | --- |
| Vue 3 | 构建组件化用户界面 |
| Vite | 开发服务器、热更新和生产构建 |
| Vue Router 4 | 页面路由管理 |
| Element Plus | 表单、表格、菜单、弹窗等 UI 组件 |
| Axios | 与 backend API 通信，可用于统一封装请求和 Token |
| npm | 依赖安装和脚本管理 |

主要目录：

```text
frontend/
├─ public/                 静态资源
├─ src/
│  ├─ assets/              图片和图标
│  ├─ components/          可复用组件
│  ├─ router/index.js      路由配置
│  ├─ views/               页面组件
│  │  ├─ Login.vue
│  │  ├─ Dashboard.vue
│  │  └─ TaskList.vue
│  ├─ App.vue              根组件
│  ├─ main.js              应用入口
│  └─ style.css            全局样式
├─ index.html
├─ package.json
└─ vite.config.js
```

## 2. 必知必会

### 2.1 创建和启动项目

```powershell
cd frontend
npm install
npm run dev
```

前端开发地址通常是：

```text
http://localhost:5173
```

生产构建：

```powershell
npm run build
npm run preview
```

`npm run dev` 面向开发调试；`npm run build` 会把项目打包到 `dist/`，用于部署前检查。

### 2.2 Vue 组件基本结构

```vue
<script setup>
import { ref } from 'vue'

const count = ref(0)
</script>

<template>
  <button @click="count++">{{ count }}</button>
</template>

<style scoped>
button {
  cursor: pointer;
}
</style>
```

必记：

- `ref()` 用于基本类型响应式数据，模板中会自动解包
- `reactive()` 适合表单对象
- `v-model` 实现输入框双向绑定
- `v-if` 控制条件渲染
- `v-for` 渲染列表
- `@click`、`@submit` 等是事件绑定
- `:prop="value"` 是动态属性绑定
- `<style scoped>` 只影响当前组件

### 2.3 路由

当前路由：

```text
/login       登录页
/dashboard   仪表板
/tasks       任务列表
/            重定向到 /login
```

路由入口由 `App.vue` 中的 `<router-view />` 渲染：

```vue
<template>
  <router-view />
</template>
```

页面跳转：

```js
import { useRouter } from 'vue-router'

const router = useRouter()
router.push('/dashboard')
```

读取当前路径：

```js
import { useRoute } from 'vue-router'

const route = useRoute()
console.log(route.path)
```

### 2.4 Element Plus

`main.js` 已全局注册 Element Plus：

```js
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

createApp(App).use(ElementPlus).mount('#app')
```

因此页面可以直接使用：

```vue
<el-form>
  <el-input v-model="username" />
  <el-button type="primary">登录</el-button>
</el-form>
```

常用组件：

| 组件 | 用途 |
| --- | --- |
| `el-form` / `el-form-item` | 表单和字段验证 |
| `el-input` | 文本、密码输入 |
| `el-button` | 操作按钮 |
| `el-menu` | 侧边导航 |
| `el-table` | 任务列表 |
| `el-dialog` | 新增/编辑弹窗 |
| `el-tag` | 状态和优先级标签 |
| `el-card` | 内容区域 |

表单验证示例：

```js
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
  ],
}
```

```vue
<el-form ref="formRef" :model="form" :rules="rules">
  <el-form-item label="用户名" prop="username">
    <el-input v-model="form.username" />
  </el-form-item>
</el-form>
```

### 2.5 API 呼叫和 JWT

登录成功后，backend 返回 JWT：

```js
const response = await axios.post('http://localhost:8080/api/auth/login', {
  username: form.username,
  password: form.password,
})

localStorage.setItem('token', response.data.token)
```

请求受保护 API 时带上 Header：

```js
const token = localStorage.getItem('token')

await axios.get('http://localhost:8080/api/users', {
  headers: {
    Authorization: `Bearer ${token}`,
  },
})
```

实际项目建议统一封装 Axios：

```js
import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default api
```

## 3. 难点重点

### 3.1 路由文件和页面文件必须同时存在

`router/index.js` 如果写了：

```js
import Login from '../views/Login.vue'
```

那么 `src/views/Login.vue` 必须真实存在，大小写也要一致。否则 Vite 会在启动或构建时出现模块找不到错误。

### 3.2 Element Plus 必须注册

只安装 npm 包还不够。必须在 `main.js` 中注册：

```js
createApp(App).use(ElementPlus).mount('#app')
```

并加载：

```js
import 'element-plus/dist/index.css'
```

否则组件可能无法识别，或页面缺少基础样式。

### 3.3 API 地址和端口

开发环境通常是：

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

这是跨来源请求。若浏览器出现 CORS 错误，需要在 backend 配置 CORS，不能只修改前端 URL。

### 3.4 登录成功不代表页面一定能访问

登录页面需要完成三个动作：

1. 调用 `/api/auth/login`
2. 保存返回的 `token`
3. 后续请求带上 `Authorization: Bearer <token>`

少了第 3 步，用户 API 和任务 API 会返回 `401` 或 `403`。

### 3.5 `el-table` 和 `el-dialog` 的数据流

编辑流程通常是：

1. 点击表格行的编辑按钮
2. 把当前 row 复制到表单对象
3. 打开 `el-dialog`
4. 保存时判断新增还是编辑
5. 更新本地列表或重新请求 backend

不要直接把表格 row 当作长期表单对象，复制一份可以避免取消编辑时意外修改列表。

## 4. 错误集

### 错误 1：PostCSS 配置 JSON 出现 `Unexpected token '�'`

典型错误：

```text
Failed to load PostCSS config
Unexpected token '�'
```

常见原因：

- `postcss.config.json` 被保存成 UTF-16 或异常编码
- 配置文件开头存在错误 BOM
- Vite 向上层目录找到了错误的 `.postcssrc` 或 PostCSS JSON

排查：

```powershell
Get-ChildItem -Force -Recurse -File | Where-Object { $_.Name -match 'postcss|\.postcssrc' }
```

如果项目没有使用 PostCSS，可以删除无效配置，或建立有效的 `postcss.config.cjs`：

```js
module.exports = {}
```

### 错误 2：`npm run dev` 找不到命令

原因通常是依赖没有安装：

```powershell
npm install
npm run dev
```

也要确认当前目录是 `frontend`，并且目录中存在 `package.json`。

### 错误 3：页面显示默认 Vite 示例

原因是 `App.vue` 仍然渲染默认组件：

```vue
<HelloWorld />
```

使用路由页面时应该改成：

```vue
<router-view />
```

### 错误 4：浏览器请求返回 `401`

检查：

- 登录账号密码是否正确
- JWT 是否保存
- 请求是否包含 `Authorization` Header
- Token 是否有 `Bearer ` 前缀
- backend 是否正在运行

### 错误 5：浏览器请求返回 `403`

`403` 表示服务器理解请求，但拒绝访问。常见原因：

- 当前 Token 没有需要的角色，例如删除用户需要 `ADMIN`
- 使用的是旧 Token 或旧 backend 进程
- Spring Security 配置没有放行登录接口

重启 backend 后重新登录，生成新的 Token。

### 错误 6：构建出现 chunk size warning

Element Plus 全量引入时，Vite 可能提示 JavaScript chunk 大于 500 KB。这是性能警告，不是构建失败。后续可使用按需导入或代码分割优化。

## 5. 检查清单

- [ ] `npm install` 成功
- [ ] `npm run build` 成功
- [ ] `main.js` 注册了 Element Plus
- [ ] `App.vue` 使用 `<router-view />`
- [ ] `/login`、`/dashboard`、`/tasks` 路由可以打开
- [ ] 登录后保存 JWT
- [ ] 受保护 API 请求带有 Bearer Token
- [ ] 前端 API 地址指向 `http://localhost:8080`
