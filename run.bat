@echo off
chcp 65001 > nul
echo ========================================
echo 启动应用...
echo ========================================
echo.

set JAR_FILE=target\app-deploy-1.0.0.jar

if not exist "%JAR_FILE%" (
    echo 错误: 找不到JAR文件 %JAR_FILE%
    echo 请先执行 build.bat 构建项目
    pause
    exit /b 1
)

echo 应用启动中...
echo 访问地址: http://localhost:7080/deploy/
echo.

java -jar %JAR_FILE%

