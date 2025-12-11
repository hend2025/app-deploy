@echo off
chcp 65001 > nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
echo ========================================
echo 开始构建项目...
echo ========================================
echo.

echo [1/3] 清理旧的构建文件...
call mvn clean
if %ERRORLEVEL% NEQ 0 (
    echo 清理失败！
    pause
    exit /b 1
)

echo.
echo [2/3] 构建前端和后端...
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo 构建失败！
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建成功！
echo ========================================
echo.
echo JAR文件位置: target\app-deploy-1.0.0.jar
echo.
echo 运行应用请执行: java -jar target\app-deploy-1.0.0.jar
echo.
pause

