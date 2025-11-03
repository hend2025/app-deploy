<template>
  <div class="container-fluid h-100" style="padding: 15px; overflow-y: auto;">
    <div class="container mt-4">
      <div class="row">
        <div class="col-12">
          <div class="jumbotron bg-light p-5 rounded">
            <h1 class="display-4">关于我们</h1>
            <p class="lead">这是一个使用Spring Boot + Vue3开发的版本发布工具</p>
          </div>
        </div>
      </div>

      <div class="row" style="margin-top: 30px;">
        <div class="col-md-6">
          <div class="card">
            <div class="card-header">
              <h5>技术栈</h5>
            </div>
            <div class="card-body">
              <ul class="list-group list-group-flush">
                <li class="list-group-item">
                  <strong>后端：</strong>Spring Boot 2.7.18
                </li>
                <li class="list-group-item">
                  <strong>前端：</strong>Vue 3.4 + Vite 5.2
                </li>
                <li class="list-group-item">
                  <strong>UI框架：</strong>Bootstrap 5.3
                </li>
                <li class="list-group-item">
                  <strong>HTTP客户端：</strong>Axios
                </li>
                <li class="list-group-item">
                  <strong>构建工具：</strong>Maven + Vite
                </li>
              </ul>
            </div>
          </div>
        </div>
        
        <div class="col-md-6">
          <div class="card">
            <div class="card-header">
              <h5>项目特性</h5>
            </div>
            <div class="card-body">
              <ul class="list-group list-group-flush">
                <li class="list-group-item">
                  前后端分离架构
                </li>
                <li class="list-group-item">
                  RESTful API 接口
                </li>
                <li class="list-group-item">
                  Vue3 组合式API
                </li>
                <li class="list-group-item">
                  响应式设计
                </li>
                <li class="list-group-item">
                  单JAR包部署
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'About'
}
</script>

<style scoped>
.container-fluid.h-100 {
  height: calc(100vh - 76px);
}
</style>

