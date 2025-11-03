# 更新日志

## [1.0.0] - 2025-11-03

### 🎉 重大更新：前端重构为Vue3

#### ✨ 新增功能
- 前端使用Vue3 + Vite重写，替换原有的Thymeleaf模板
- 采用前后端分离架构
- 单页应用（SPA）支持，路由由Vue Router管理
- 前后端打包成单个jar文件，便于部署
- 自动化构建流程（Maven + Frontend Maven Plugin）

#### 🔄 架构变更

**前端技术栈**
- Vue 3.4.21（Composition API）
- Vite 5.2.0（构建工具）
- Vue Router 4.3.0（路由管理）
- Axios 1.6.8（HTTP客户端）
- Bootstrap 5.3.3（UI框架）

**后端变更**
- 移除Thymeleaf依赖
- 更新WebConfig支持SPA路由
- 优化HomeController简化路由处理
- 添加CORS配置

**构建配置**
- 配置Frontend Maven Plugin自动下载Node.js v18.20.4
- 自动执行npm install和npm build
- 自动复制前端构建产物到static目录
- 支持一键打包成jar文件

#### 📦 组件化改造

**Vue组件**
- `Navbar.vue` - 导航栏组件（支持路由高亮）
- `LogModal.vue` - 日志查看模态框组件（可复用）
- `AppMgt.vue` - 应用管理页面
- `VerMgt.vue` - 版本构建页面
- `LogMgt.vue` - 日志管理页面
- `About.vue` - 关于页面

**API封装**
- 统一的axios实例配置
- API接口分类管理（appMgtApi, verBuildApi, logFileApi）
- 请求/响应拦截器
- 错误处理统一化

**工具函数**
- `alert.js` - 消息提示工具

#### 🛠️ 开发体验优化
- 前端支持热模块替换（HMR）
- 开发服务器支持API代理
- 构建脚本（build.bat/build.sh）
- 运行脚本（run.bat/run.sh）
- 详细的文档（README.md, BUILD.md, QUICK_START.md）

#### 📝 文档完善
- 新增README.md（项目介绍和快速开始）
- 新增BUILD.md（详细构建文档）
- 新增QUICK_START.md（5分钟上手指南）
- 新增CHANGELOG.md（更新日志）
- 新增.gitignore（版本控制配置）

#### 🔧 配置优化
- 简化application.yml配置
- 添加frontend/.npmrc（npm镜像配置）
- 优化pom.xml（Maven构建配置）
- 添加vite.config.js（Vite构建配置）

#### 🎨 UI保持不变
- 保持原有的Bootstrap UI风格
- 保持原有的功能交互逻辑
- 保持原有的API接口不变
- 完全向后兼容

#### ⚡ 性能提升
- Vite构建速度显著提升
- 代码分割和懒加载
- 更小的生产包体积
- 更快的页面加载速度

#### 🧪 兼容性
- 后端Java代码保持不变
- API接口保持不变
- 数据格式保持不变
- 支持Java 1.8+

### 📋 升级说明

**从旧版本升级**
1. 备份数据文件（data目录）
2. 执行新的构建命令：`mvn clean package`
3. 运行新的jar文件

**首次使用**
1. 确保安装JDK 1.8+和Maven 3.6+
2. 执行 `build.bat` 或 `build.sh`
3. 执行 `run.bat` 或 `run.sh`
4. 访问 http://localhost:7080/deploy/

### 🐛 修复问题
- 修复原有HTML页面的模板引擎依赖
- 优化前端资源加载
- 改善错误提示信息

### ⚠️ 注意事项
- 首次构建需要下载Node.js，可能需要5-10分钟
- 需要网络连接以下载前端依赖
- 建议使用国内镜像加速（已配置）

---

## 技术债务

### 已完成
- ✅ 前端Vue3化改造
- ✅ 前后端分离架构
- ✅ 单jar包部署
- ✅ 自动化构建流程
- ✅ 文档完善

### 计划中
- 🔄 添加单元测试
- 🔄 添加E2E测试
- 🔄 添加Docker支持
- 🔄 添加CI/CD配置
- 🔄 性能监控和日志

---

**维护者**: 开发团队  
**更新日期**: 2025-11-03  
**版本**: 1.0.0

