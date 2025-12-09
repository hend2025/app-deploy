<template>
  <div id="app">
    <!-- 顶部导航栏 -->
    <Navbar />
    <!-- 路由页面容器 -->
    <router-view></router-view>
  </div>
</template>

<script>
import Navbar from './components/Navbar.vue'

export default {
  name: 'App',
  components: {
    Navbar
  }
}
</script>

<style>
html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  display: flex;
  flex-direction: column;
}

#app {
  display: flex;
  flex-direction: column;
}
</style>
