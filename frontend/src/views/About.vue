<template>
  <div class="page-container">
    <el-card class="hero-card">
      <h1>关于我们</h1>
      <p class="lead">这是一个使用Spring Boot + Vue3开发的版本发布工具</p>
    </el-card>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>技术栈</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="后端">Spring Boot 2.7.18</el-descriptions-item>
            <el-descriptions-item label="前端">Vue 3.4 + Vite 5.2</el-descriptions-item>
            <el-descriptions-item label="UI框架">Element Plus</el-descriptions-item>
            <el-descriptions-item label="HTTP客户端">Axios</el-descriptions-item>
            <el-descriptions-item label="构建工具">Maven + Vite</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>项目特性</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="架构">前后端分离架构</el-descriptions-item>
            <el-descriptions-item label="接口">RESTful API 接口</el-descriptions-item>
            <el-descriptions-item label="Vue">Vue3 组合式API</el-descriptions-item>
            <el-descriptions-item label="设计">响应式设计</el-descriptions-item>
            <el-descriptions-item label="部署">单JAR包部署</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: 'About'
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: calc(100vh - 61px);
  overflow-y: auto;
}
.hero-card {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  text-align: center;
  padding: 40px;
}
.hero-card h1 {
  margin: 0 0 15px 0;
  font-size: 32px;
}
.hero-card .lead {
  margin: 0;
  font-size: 16px;
  opacity: 0.9;
}
</style>
