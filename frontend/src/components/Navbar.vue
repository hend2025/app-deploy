<template>
  <el-menu
    mode="horizontal"
    :default-active="activeIndex"
    background-color="#409EFF"
    text-color="#fff"
    active-text-color="#ffd04b"
    router
  >
    <el-menu-item index="/deploy/" disabled class="brand-title">
      Spring Boot 应用管理
    </el-menu-item>
    <div style="flex-grow: 1;"></div>
    <el-menu-item index="/deploy/">版本构建</el-menu-item>
    <el-menu-item index="/deploy/appMgt">应用启动</el-menu-item>
    <el-menu-item index="/deploy/logMgt">日志查看</el-menu-item>
    <el-menu-item index="/deploy/about">关于</el-menu-item>
  </el-menu>
</template>

<script>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'Navbar',
  setup() {
    const route = useRoute()
    const activeIndex = computed(() => route.path)
    return { activeIndex }
  }
}
</script>

<style scoped>
.el-menu--horizontal {
  border-bottom: none;
}
:deep(.el-menu--horizontal .el-menu-item:not(.is-disabled).is-active) {
  border-bottom: none !important;
}
.brand-title {
  font-weight: bold;
  font-size: 18px;
  cursor: default;
}
:deep(.brand-title.is-disabled) {
  opacity: 1 !important;
  color: #fff !important;
}
</style>
