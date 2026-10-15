import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElInputNumber, ElOption, ElSelect, ElSwitch } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-input-number.css'
import 'element-plus/theme-chalk/el-option.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-switch.css'
import 'element-plus/theme-chalk/el-message.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElSelect)
  .use(ElOption)
  .use(ElInputNumber)
  .use(ElSwitch)
  .mount('#app')
