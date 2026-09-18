# Lab 07 - Frontend Automated Testing

## 1. 學習目標

- 使用 Vitest 執行 Vue frontend unit test
- 使用 Vue Test Utils mount Vue component
- 使用 jsdom 模擬瀏覽器 DOM
- 在測試中載入 Element Plus 和 Vue Router
- 驗證 Login component 的表單渲染與互動流程

## 2. 測試工具

Frontend 使用以下工具：

| 工具 | 用途 |
| --- | --- |
| Vitest | JavaScript/Vue 測試 runner |
| `@vue/test-utils` | mount 和操作 Vue component |
| jsdom | 在 Node.js 中模擬瀏覽器 DOM |
| Element Plus | 提供 `el-input`、`el-form`、`el-button` 等元件 |
| Vue Router | 提供 Login component 使用的 router injection |

安裝測試依賴：

```powershell
cd frontend
npm install -D vitest @vue/test-utils jsdom
```

專案已使用 Vite Vue plugin 編譯 `.vue` component。

## 3. Vitest 設定

檔案：

```text
frontend/vitest.config.js
```

目前設定：

```js
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true
  }
})
```

`environment: 'jsdom'` 很重要，因為 Vue component 測試需要 `document`、`window` 等 DOM API。

## 4. Login component 測試環境

`Login.vue` 使用了：

```js
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
```

template 也使用 Element Plus 元件：

```vue
<el-form>
  <el-input />
  <el-button />
</el-form>
```

因此不能只使用：

```js
mount(Login)
```

否則測試會出現：

```text
injection "Symbol(router)" not found
Failed to resolve component: el-input
```

測試需要提供 Element Plus plugin 和 memory router：

```js
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: Login }]
})

const mountLogin = () => mount(Login, {
  global: {
    plugins: [ElementPlus, router]
  }
})
```

`createMemoryHistory()` 適合測試，因為它不需要真的操作瀏覽器 URL，也不會依賴 production 的 browser history。

## 5. 測試內容

測試檔案：

```text
frontend/src/__tests__/Login.spec.js
```

### 5.1 渲染登入表單

```js
it('renders login form', () => {
  const wrapper = mountLogin()
  expect(wrapper.find('input[type="text"]').exists()).toBe(true)
  expect(wrapper.find('input[type="password"]').exists()).toBe(true)
})
```

此測試確認：

- Login component 可以成功 mount
- 使用者名稱 input 存在
- 密碼 input 存在
- Element Plus input 已經正確渲染

### 5.2 登入失敗流程

```js
it('shows error when login fails', async () => {
  const wrapper = mountLogin()
  await wrapper.find('input[type="text"]').setValue('user')
  await wrapper.find('input[type="password"]').setValue('wrong')
  await wrapper.find('button').trigger('click')
})
```

此測試目前確認：

- 可以輸入 username
- 可以輸入 password
- 可以觸發 submit button
- component submit flow 不會因 mount 設定而失敗

目前測試尚未 mock API，也尚未 assertion `ElMessage.error` 的內容，因此測試名稱「shows error」仍可進一步加強。

## 6. 執行測試

目前 `package.json` 尚未建立 test script，因此使用 `npx` 執行：

```powershell
cd frontend
npx vitest run
```

執行單一測試檔：

```powershell
npx vitest run src/__tests__/Login.spec.js
```

開發模式執行：

```powershell
npx vitest
```

預期結果：

```text
Test Files  1 passed (1)
Tests       2 passed (2)
```

## 7. 建議的 test script

為了讓團隊使用一致指令，可以在 `frontend/package.json` 加入：

```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest"
  }
}
```

之後可以使用：

```powershell
npm test
npm run test:watch
```

## 8. 測試缺口與下一步

目前只有 Login component 測試，建議後續增加：

- mock `api.post('/auth/login', ...)`
- 驗證錯誤時顯示的 Element Plus message
- 驗證成功後儲存 token、username 和 role
- 驗證成功後呼叫 `router.push('/dashboard')`
- 驗證密碼少於 6 個字元時的 validation message
- 測試 logout 清除 localStorage
- 測試 router guard 對未登入使用者的導向
- 為 Dashboard、TaskList 和 Users component 增加測試

API mock 應使用 Vitest 的 mock 功能，避免 frontend unit test 依賴正在執行的 backend：

```js
vi.mock('../api/index.js', () => ({
  default: {
    post: vi.fn()
  }
}))
```

這樣可以分離：

```text
Frontend component behavior -> Vitest unit test
Backend API behavior        -> Spring Boot test
Frontend + backend + DB     -> E2E/integration test
```

## 9. 驗證結果

本次執行：

```powershell
cd frontend
npx vitest run --reporter=verbose
```

結果：

```text
Test Files  1 passed (1)
Tests       2 passed (2)
```

Frontend 自動化測試目前已能在 jsdom 中成功 mount Vue component，並完成 Login 表單的基本渲染與互動驗證。
