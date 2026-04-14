import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setupAuthRuntime } from '@/lib/auth'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import './style.css'

setupAuthRuntime(router)

const app = createApp(App)
app.use(router)
app.mount('#app')
