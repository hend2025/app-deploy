# App Deploy - 应用部署管理系统

基于 **Spring Boot + Vue3** 的应用部署管理系统，支持版本构建、应用启动/停止、日志查看等功能。

## 🚀 快速开始

### 环境要求

- **JDK**: 1.8 或更高版本
- **Maven**: 3.6 或更高版本
- **Node.js**: v18.20.4（打包时自动下载，无需预装）

### 一键构建和运行

#### Windows

```bash
# 构建项目
build.bat

# 运行应用
run.bat
```

#### Linux/Mac

```bash
# 添加执行权限
chmod +x build.sh run.sh

# 构建项目
./build.sh

# 运行应用
./run.sh
```

### 手动构建

```bash
# 清理并打包（包含前端和后端）
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests

# 运行JAR包
java -jar target/app-deploy-1.0.0.jar
```

构建完成后，访问：**http://localhost:7080/deploy/**

## 📁 项目结构

```
app-deploy/
├── frontend/                  # Vue3前端项目
│   ├── src/
│   │   ├── api/              # API接口封装
│   │   ├── components/       # Vue组件
│   │   │   ├── Navbar.vue   # 导航栏组件
│   │   │   └── LogModal.vue # 日志查看组件
│   │   ├── views/            # 页面视图
│   │   │   ├── AppMgt.vue   # 应用管理页面
│   │   │   ├── VerMgt.vue   # 版本管理页面
│   │   │   ├── LogMgt.vue   # 日志管理页面
│   │   │   └── About.vue    # 关于页面
│   │   ├── router/           # 路由配置
│   │   ├── utils/            # 工具函数
│   │   ├── assets/           # 静态资源
│   │   ├── App.vue           # 根组件
│   │   └── main.js           # 入口文件
│   ├── package.json          # NPM配置
│   ├── vite.config.js        # Vite配置
│   └── index.html            # HTML模板
├── src/                      # Java后端项目
│   └── main/
│       ├── java/
│       │   └── com/aeye/app/deploy/
│       │       ├── config/          # 配置类
│       │       ├── controller/      # 控制器
│       │       ├── service/         # 服务层
│       │       ├── model/           # 数据模型
│       │       └── util/            # 工具类
│       └── resources/
│           ├── application.yml      # 应用配置
│           ├── data/               # 数据文件
│           └── scripts/            # 脚本文件
├── pom.xml                   # Maven配置
├── BUILD.md                  # 详细构建文档
├── build.bat / build.sh      # 构建脚本
└── run.bat / run.sh          # 运行脚本
```

## ✨ 功能特性

### 📦 版本构建
- 搜索和查看应用版本信息
- 启动应用版本构建任务
- 停止正在运行的构建任务
- 实时查看构建日志

### 🚀 应用管理
- 查看所有已部署的应用
- 启动/停止应用进程
- 自定义启动参数
- 实时监控应用状态
- 查看应用运行日志

### 📋 日志管理
- 浏览服务器日志文件列表
- 实时查看日志内容
- 自动刷新日志
- 下载日志文件
- 支持大文件增量加载

### ℹ️ 关于页面
- 查看技术栈信息
- 查看项目特性

## 🛠️ 技术栈

### 前端技术
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4.21 | 渐进式JavaScript框架 |
| Vite | 5.2.0 | 下一代前端构建工具 |
| Vue Router | 4.3.0 | Vue.js官方路由 |
| Axios | 1.6.8 | HTTP客户端 |
| Bootstrap | 5.3.3 | 响应式UI框架 |

### 后端技术
| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.18 | Java应用框架 |
| Maven | 3.x | 项目管理工具 |
| JDK | 1.8 | Java开发工具包 |

### 构建工具
- **Frontend Maven Plugin**: 自动化前端构建
- **Maven Resources Plugin**: 资源文件处理

## 🔧 配置说明

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

### 自定义配置

启动时可以通过命令行参数覆盖配置：

```bash
# 修改端口
java -jar app-deploy-1.0.0.jar --server.port=8080

# 修改数据目录
java -jar app-deploy-1.0.0.jar --app.directory.logs=/var/logs

# 多个参数
java -jar app-deploy-1.0.0.jar --server.port=8080 --app.directory.logs=/var/logs
```

## 📝 开发指南

### 前端开发

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器（端口3000）
npm run dev

# 构建生产版本
npm run build
```

开发服务器会自动代理API请求到 `http://localhost:7080/deploy`

### 后端开发

```bash
# 使用Maven启动
mvn spring-boot:run

# 或使用IDE直接运行
# 运行 src/main/java/com/aeye/app/deploy/SpringBootApplication.java
```

### API接口

所有API接口都在 `/deploy` 路径下：

- `GET /deploy/appMgt/list` - 获取应用列表
- `POST /deploy/appMgt/start` - 启动应用
- `POST /deploy/appMgt/stop` - 停止应用
- `GET /deploy/verBuild/search` - 搜索版本
- `POST /deploy/verBuild/build` - 构建版本
- `POST /deploy/verBuild/stop` - 停止构建
- `GET /deploy/logs/file/list` - 获取日志文件列表
- `GET /deploy/logs/file/read-file-last-lines` - 读取日志
- `GET /deploy/logs/file/download-file` - 下载日志

## 📦 部署

### 开发环境部署

```bash
java -jar app-deploy-1.0.0.jar
```

### 生产环境部署

```bash
# 后台运行
nohup java -jar app-deploy-1.0.0.jar > app.log 2>&1 &

# 使用systemd服务（Linux）
sudo systemctl start app-deploy
```

## ❓ 常见问题

### 1. 构建时Node.js下载慢

在 `pom.xml` 中配置国内镜像：

```xml
<nodeDownloadRoot>https://npmmirror.com/mirrors/node/</nodeDownloadRoot>
<npmDownloadRoot>https://registry.npmmirror.com/npm/-/</npmDownloadRoot>
```

### 2. npm install 慢

项目已配置国内镜像（frontend/.npmrc）：

```
registry=https://registry.npmmirror.com/
```

### 3. 前端页面显示404

确保：
- 执行了完整的 `mvn package` 构建
- frontend/dist 内容已复制到 target/classes/static
- application.yml 配置正确

### 4. API请求失败

检查：
- 后端是否正常启动
- 端口7080是否被占用
- 防火墙设置

## 🎯 项目特点

✅ **前后端分离** - Vue3 SPA + Spring Boot REST API  
✅ **单JAR部署** - 前后端打包在一个jar文件中  
✅ **自动化构建** - Maven一键完成前后端构建  
✅ **开发友好** - 前端HMR热重载 + 后端DevTools  
✅ **响应式设计** - Bootstrap 5现代化UI  
✅ **RESTful API** - 标准化的API接口设计  
✅ **实时监控** - 自动刷新应用状态和日志  

## 📄 许可证

本项目采用 MIT 许可证

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

---

**开发团队** | **版本**: 1.0.0 | **更新日期**: 2025-11-03

