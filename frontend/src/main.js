import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/theme.css'
import '@/styles/element.css'
import '@/styles/base.css'
import App from '@/App.vue'
import router from '@/router'
import { permission } from '@/utils/permission'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('permission', permission)

// 组件内未捕获异常兜底：避免白屏，统一提示并输出日志
app.config.errorHandler = (err, instance, info) => {
  console.error('[app] 未捕获异常:', err, info)
  ElMessage.error('页面出现异常，请刷新重试')
}

app.mount('#app')
