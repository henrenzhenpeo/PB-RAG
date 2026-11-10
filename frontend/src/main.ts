import { createApp } from 'vue'
import { createPinia } from 'pinia'
import TDesign from 'tdesign-vue-next'
import 'tdesign-vue-next/es/style/index.css'

import App from './App.vue'
import router from './router'

// 前端启动入口：注册路由、状态管理和 TDesign 组件后挂载根组件。
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(TDesign)

app.mount('#app')
