# 构建和部署指南

## 项目结构

```
app-deploy/
├── frontend/              # Vue3前端项目
│   ├── src/
│   │   ├── api/          # API接口
│   │   ├── components/   # Vue组件
│   │   ├── views/        # 页面视图
│   │   ├── router/       # 路由配置
│   │   ├── utils/        # 工具函数
│   │   ├── assets/       # 静态资源
│   │   ├── App.vue       # 根组件
│   │   └── main.js       # 入口文件
│   ├── package.json
│   └── vite.config.js
├── src/                  # Java后端项目
│   └── main/
│       ├── java/         # Java源代码
│       └── resources/    # 配置文件
└── pom.xml              # Maven配置
```

## 技术栈

### 前端
- **Vue 3.4** - 渐进式JavaScript框架
- **Vite 5.2** - 下一代前端构建工具
- **Vue Router 4.3** - Vue.js官方路由
- **Axios 1.6** - HTTP客户端
- **Bootstrap 5.3** - UI框架

### 后端
- **Spring Boot 2.7.18** - Java应用框架
- **Maven** - 项目管理工具
- **JDK 1.8** - Java开发工具包

## 开发环境要求

- **JDK**: 1.8 或更高版本
- **Maven**: 3.6 或更高版本
- **Node.js**: v18.20.4（打包时自动下载，无需预装）
- **NPM**: 10.7.0（打包时自动下载，无需预装）

## 本地开发

### 前端开发

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 启动开发服务器（端口3000）
npm run dev

# 构建生产版本
npm run build
```

前端开发服务器会自动代理API请求到 `http://localhost:8080/deploy`

### 后端开发

```bash
# 使用Maven启动Spring Boot应用
mvn spring-boot:run
```

或者使用IDE直接运行 `SpringBootApplication.java`

## 生产环境打包

### 一键打包（推荐）

```bash
# 清理并打包（包含前端构建）
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests
```

打包过程：
1. Maven下载并安装Node.js v18.20.4和NPM 10.7.0
2. 在frontend目录执行npm install安装前端依赖
3. 在frontend目录执行npm run build构建Vue项目
4. 将frontend/dist目录的内容复制到target/classes/static
5. 打包成可执行的jar文件

生成的jar文件位于：`target/app-deploy-1.0.0.jar`

### 分步打包（可选）

如果需要单独构建前端或后端：

```bash
# 仅构建前端
cd frontend
npm install
npm run build

# 手动复制前端构建产物
mkdir -p ../target/classes/static
cp -r dist/* ../target/classes/static/

# 仅构建后端
cd ..
mvn clean package -Dmaven.test.skip=true
```

## 运行应用

### 开发环境运行

```bash
# 运行jar包
java -jar target/app-deploy-1.0.0.jar
```

### 生产环境运行

```bash
# 基本运行
java -jar app-deploy-1.0.0.jar

# 指定端口和上下文路径（可选）
java -jar app-deploy-1.0.0.jar --server.port=8080 --server.servlet.context-path=/deploy

# 后台运行
nohup java -jar app-deploy-1.0.0.jar > app.log 2>&1 &
```

应用启动后访问：
- 默认地址：http://localhost:7080/deploy/
- 自定义端口：http://localhost:{port}/deploy/

## 配置说明

### 应用配置（application.yml）

```yaml
server:
  port: 7080                      # 服务端口
  servlet:
    context-path: /deploy         # 上下文路径

app:
  directory:
    data: /home/data             # 数据目录
    release: /home/release       # 发布目录
    logs: /home/logs            # 日志目录
```

### 前端配置（vite.config.js）

```javascript
export default defineConfig({
  base: '/deploy/',              // 基础路径，需与context-path一致
  server: {
    port: 3000,
    proxy: {
      '/deploy': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

## 常见问题

### 1. 打包时Node.js下载慢

可以配置Maven使用镜像：

在 `pom.xml` 的 `frontend-maven-plugin` 配置中添加：

```xml
<configuration>
    <nodeVersion>v18.20.4</nodeVersion>
    <npmVersion>10.7.0</npmVersion>
    <nodeDownloadRoot>https://npmmirror.com/mirrors/node/</nodeDownloadRoot>
    <npmDownloadRoot>https://registry.npmmirror.com/npm/-/</npmDownloadRoot>
</configuration>
```

### 2. npm install 慢

在 frontend 目录下创建 `.npmrc` 文件：

```
registry=https://registry.npmmirror.com/
```

### 3. 前端页面404

确保：
1. 前端已正确构建并复制到 static 目录
2. application.yml 中配置了正确的静态资源路径
3. WebConfig.java 中正确配置了路由转发

### 4. API请求失败

检查：
1. 后端API路径是否正确（应以 /deploy 开头）
2. CORS配置是否正确
3. 前端axios baseURL配置是否正确

## 项目特性

✅ **前后端分离架构** - Vue3 + Spring Boot
✅ **单JAR包部署** - 前后端打包在一个jar文件中
✅ **自动化构建** - Maven一键完成前后端构建
✅ **开发热重载** - 前端Vite HMR + 后端DevTools
✅ **响应式设计** - Bootstrap 5 现代UI
✅ **RESTful API** - 标准化的API接口
✅ **SPA路由** - Vue Router单页应用

## 版本信息

- **项目版本**: 1.0.0
- **Spring Boot**: 2.7.18
- **Vue**: 3.4.21
- **Node.js**: v18.20.4
- **构建工具**: Maven 3.x + Vite 5.2

