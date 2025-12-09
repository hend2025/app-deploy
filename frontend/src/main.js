/**
 * 应用入口文件
 * 
 * 初始化Vue应用，配置Element Plus UI框架和路由
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/style.css'

const app = createApp(App)

// 全局注册Element Plus图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 配置Element Plus（中文语言包）
app.use(ElementPlus, { locale: zhCn })
// 配置路由
app.use(router)
// 挂载应用
app.mount('#app')
