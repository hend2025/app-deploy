#!/bin/bash

echo "========================================"
echo "启动应用..."
echo "========================================"
echo

JAR_FILE="target/app-deploy-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到JAR文件 $JAR_FILE"
    echo "请先执行 build.sh 构建项目"
    exit 1
fi

echo "应用启动中..."
echo "访问地址: http://localhost:7080/deploy/"
echo

java -jar $JAR_FILE

