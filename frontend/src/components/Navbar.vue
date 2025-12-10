<template>
  <el-menu
    mode="horizontal"
    :default-active="activeIndex"
    background-color="#409EFF"
    text-color="#fff"
    active-text-color="#ffd04b"
    router
    :ellipsis="false"
  >
    <!-- 系统标题（不可点击） -->
    <el-menu-item index="/deploy/" disabled class="brand-title">
      Spring Boot 应用管理
    </el-menu-item>
    <!-- 弹性占位，将导航项推到右侧 -->
    <div style="flex-grow: 1;"></div>
    <!-- 导航菜单项 -->
    <el-menu-item index="/deploy/">版本构建</el-menu-item>
    <el-menu-item index="/deploy/appMgt">应用启动</el-menu-item>
    <el-menu-item index="/deploy/logFiles">日志文件</el-menu-item>
  </el-menu>
</template>

<script>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'Navbar',
  setup() {
    const route = useRoute()
    // 根据当前路由路径计算激活的菜单项
    const activeIndex = computed(() => route.path)
    return { activeIndex }
  }
}
</script>

<style scoped>
.el-menu--horizontal {
  border-bottom: none;
  flex-wrap: nowrap;
}
:deep(.el-menu--horizontal .el-menu-item:not(.is-disabled).is-active) {
  border-bottom: none !important;
}
:deep(.el-menu--horizontal > .el-menu-item) {
  flex-shrink: 0;
}
:deep(.el-menu--horizontal .el-sub-menu__hide-arrow) {
  display: none !important;
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
