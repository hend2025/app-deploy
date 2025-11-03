#!/bin/bash

# 杀死相关进程
echo "正在停止相关服务..."
pkill -f "app-deploy-1.0.0.jar" 
 
sleep 3

# 检查是否还有相关进程运行，如果有则强制杀死
LOGS_PID=$(pgrep -f "app-deploy-1.0.0.jar") 

if [ ! -z "$LOGS_PID" ]; then
	echo "强制杀死logs进程: $LOGS_PID"
	kill -9 $LOGS_PID
fi
 
# 启动新的服务
echo "启动新的服务..."

nohup java -jar -Xmx4096m -Xms512m -Dapp.directory.data=/aeye/data -Dapp.directory.logs=/aeye/logs -Dapp.directory.release=/aeye/release /aeye/app-deploy-1.0.0.jar > /aeye/logs/app-deploy.log 2>/dev/null &

sleep 5

echo "服务重启完成！"
