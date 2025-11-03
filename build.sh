#!/bin/bash

echo "========================================"
echo "开始构建项目..."
echo "========================================"
echo

echo "[1/3] 清理旧的构建文件..."
mvn clean
if [ $? -ne 0 ]; then
    echo "清理失败！"
    exit 1
fi

echo
echo "[2/3] 构建前端和后端..."
mvn package -DskipTests
if [ $? -ne 0 ]; then
    echo "构建失败！"
    exit 1
fi

echo
echo "========================================"
echo "构建成功！"
echo "========================================"
echo
echo "JAR文件位置: target/app-deploy-1.0.0.jar"
echo
echo "运行应用请执行: java -jar target/app-deploy-1.0.0.jar"
echo

