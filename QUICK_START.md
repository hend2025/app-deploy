# 快速开始指南

## 🎯 5分钟快速上手

### 第一步：检查环境

```bash
# 检查JDK版本（需要1.8+）
java -version

# 检查Maven版本（需要3.6+）
mvn -version
```

### 第二步：构建项目

**Windows用户：**
```bash
build.bat
```

**Linux/Mac用户：**
```bash
chmod +x build.sh
./build.sh
```

**或使用Maven命令：**
```bash
mvn clean package -DskipTests
```

构建过程说明：
1. ⏳ Maven下载Node.js v18.20.4（首次构建需要几分钟）
2. ⏳ 安装前端依赖（npm install）
3. ⏳ 构建Vue3前端（npm run build）
4. 📦 复制前端文件到static目录
5. 📦 打包成jar文件

**预计时间：** 首次构建约5-10分钟，后续构建2-3分钟

### 第三步：运行应用

**Windows用户：**
```bash
run.bat
```

**Linux/Mac用户：**
```bash
chmod +x run.sh
./run.sh
```

**或直接运行jar：**
```bash
java -jar target/app-deploy-1.0.0.jar
```

### 第四步：访问应用

打开浏览器访问：**http://localhost:7080/deploy/**

## 📋 功能验证清单

访问应用后，请按以下步骤验证功能：

### ✅ 版本构建页面（首页）
- [ ] 页面正常加载
- [ ] 搜索框可用
- [ ] 版本列表显示正常
- [ ] 构建、停止、日志按钮可见

### ✅ 应用启动页面
- [ ] 点击导航栏"应用启动"
- [ ] 应用列表显示正常
- [ ] 启动、停止按钮可见
- [ ] 可以打开启动弹窗
- [ ] 可以打开停止确认弹窗

### ✅ 日志查看页面
- [ ] 点击导航栏"日志查看"
- [ ] 文件选择框可用
- [ ] 选择日志文件后内容显示
- [ ] 自动刷新、下载、清空按钮可用

### ✅ 关于页面
- [ ] 点击导航栏"关于"
- [ ] 技术栈信息显示正常
- [ ] 项目特性显示正常

## 🔍 问题排查

### 构建失败

**错误：Cannot find Maven**
```bash
# 解决方案：安装Maven或检查环境变量
# Windows: 下载Maven并配置PATH
# Linux: sudo apt install maven
# Mac: brew install maven
```

**错误：Node.js下载慢/超时**
```bash
# 解决方案：使用国内镜像（已在pom.xml中配置）
# 或手动下载Node.js放到指定目录
```

**错误：npm install失败**
```bash
# 解决方案1：清理缓存
cd frontend
rm -rf node_modules package-lock.json
cd ..
mvn clean package

# 解决方案2：使用cnpm（已在.npmrc中配置镜像）
```

### 运行失败

**错误：端口7080被占用**
```bash
# 解决方案：修改端口
java -jar target/app-deploy-1.0.0.jar --server.port=8080

# 然后访问 http://localhost:8080/deploy/
```

**错误：找不到jar文件**
```bash
# 解决方案：先构建项目
mvn clean package
```

### 页面404

**原因：前端资源未正确打包**
```bash
# 解决方案：重新构建
mvn clean package -DskipTests

# 验证：检查jar包中是否包含前端文件
jar -tf target/app-deploy-1.0.0.jar | grep static
# 应该看到：
# BOOT-INF/classes/static/index.html
# BOOT-INF/classes/static/assets/...
```

### API请求失败

**原因：后端未启动或端口错误**
```bash
# 检查应用日志
# 确认看到：Started SpringBootApplication in X seconds

# 检查端口
netstat -ano | findstr 7080  # Windows
lsof -i:7080                  # Linux/Mac
```

## 🚀 性能优化建议

### 开发环境

```bash
# 前端开发（热重载）
cd frontend
npm run dev
# 访问 http://localhost:3000

# 后端开发（自动重启）
mvn spring-boot:run
# 访问 http://localhost:7080
```

### 生产环境

```bash
# 后台运行
nohup java -jar app-deploy-1.0.0.jar > app.log 2>&1 &

# 查看日志
tail -f app.log

# 停止应用
ps aux | grep app-deploy
kill <PID>
```

## 📞 获取帮助

如果遇到问题：

1. **查看完整文档**：阅读 BUILD.md 和 README.md
2. **查看日志**：检查控制台输出或app.log
3. **验证环境**：确认JDK和Maven版本正确
4. **清理重试**：执行 `mvn clean` 后重新构建

## 🎉 成功标志

当你看到以下内容时，表示部署成功：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v2.7.18)

...
Started SpringBootApplication in X.XXX seconds
```

浏览器访问 http://localhost:7080/deploy/ 看到应用管理界面！

---

**祝使用愉快！** 🎊

