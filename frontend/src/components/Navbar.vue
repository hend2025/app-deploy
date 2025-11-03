<template>
  <nav class="navbar navbar-expand-lg navbar-dark bg-primary">
    <div class="container">
      <router-link class="navbar-brand" to="/deploy/">
        Spring Boot 应用管理
      </router-link>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarNav">
        <ul class="navbar-nav ms-auto">
          <li class="nav-item">
            <router-link class="nav-link" to="/deploy/" :class="{ active: isActive('/deploy/') }">
              版本构建
            </router-link>
          </li>
          <li class="nav-item">
            <router-link class="nav-link" to="/deploy/appMgt" :class="{ active: isActive('/deploy/appMgt') }">
              应用启动
            </router-link>
          </li>
          <li class="nav-item">
            <router-link class="nav-link" to="/deploy/logMgt" :class="{ active: isActive('/deploy/logMgt') }">
              日志查看
            </router-link>
          </li>
          <li class="nav-item">
            <router-link class="nav-link" to="/deploy/about" :class="{ active: isActive('/deploy/about') }">
              关于
            </router-link>
          </li>
        </ul>
      </div>
    </div>
  </nav>
</template>

<script>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'Navbar',
  setup() {
    const route = useRoute()
    
    const isActive = (path) => {
      return computed(() => route.path === path).value
    }

    return {
      isActive
    }
  }
}
</script>

