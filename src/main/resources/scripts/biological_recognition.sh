#!/bin/bash

# 设置字符编码
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 配置变量 - 可根据需要修改
WORKSPACE="/aeye/workspace"
PROJECT_NAME="biological_recognition"
GIT_URL="http://192.168.24.21/superman/biological_recognition.git"
PROJECT_HOME="$WORKSPACE/$PROJECT_NAME"

# 参数检查
BRANCH_TAG="$1"

if [ -z "$BRANCH_TAG" ]; then
    echo "错误: 分支/Tag不能为空!"
    echo "使用方法: $0 <分支/Tag名称>"
    exit 1
fi

# 创建必要目录
if [ ! -d "$WORKSPACE" ]; then
    echo "创建工作空间目录: $WORKSPACE"
    if ! mkdir -p "$WORKSPACE"; then
        echo "错误: 创建工作空间目录失败!"
        exit 1
    fi
fi

# 克隆或更新代码
echo
echo "[1/3] 克隆/更新代码库..."
if [ -d "$PROJECT_HOME" ]; then
    echo "项目目录已存在，执行更新操作..."
    cd "$PROJECT_HOME" || exit 1
    
    # 丢失所有未提交的修改
    git reset --hard HEAD
    
    # 获取远程更新
    git fetch origin
    
    # 检查目标是否存在
    if ! git show-ref --verify --quiet "refs/remotes/origin/$BRANCH_TAG" && \
       ! git show-ref --verify --quiet "refs/tags/$BRANCH_TAG"; then
        echo "错误: 分支/Tag '$BRANCH_TAG' 在远程不存在!"
        exit 1
    fi
    
    # 切换到目标分支/Tag
    if git checkout "$BRANCH_TAG" 2>/dev/null; then
        echo "√ 切换到 $BRANCH_TAG 成功"
        
        # 如果是分支，拉取最新代码
        if git symbolic-ref -q HEAD >/dev/null; then
            echo "正在拉取最新代码..."
            if git pull origin "$BRANCH_TAG"; then
                echo "√ 代码更新完成"
            else
                echo "警告: 代码拉取失败，继续构建..."
            fi
        fi
    else
        echo "错误: 无法切换到指定分支/Tag: $BRANCH_TAG"
        exit 1
    fi
    
else
    echo "项目目录不存在，执行克隆操作..."
    cd "$WORKSPACE" || exit 1
    
    # 验证分支/Tag是否存在
    if git ls-remote --exit-code "$GIT_URL" "$BRANCH_TAG" >/dev/null 2>&1; then
        echo "正在克隆代码库..."
        if git clone -b "$BRANCH_TAG" "$GIT_URL" "$PROJECT_NAME"; then
            echo "√ 克隆成功"
        else
            echo "错误: Git克隆失败!"
            exit 1
        fi
    else
        echo "错误: 分支/Tag '$BRANCH_TAG' 在远程仓库中不存在!"
        exit 1
    fi
fi

echo "√ 代码库操作完成"

# 检查必要的命令是否存在
echo
echo "[2/3] 检查构建环境..."
if [ -s "$HOME/.nvm/nvm.sh" ]; then
    source "$HOME/.nvm/nvm.sh"
    echo "√ nvm 已加载"
else
    echo "错误: nvm 未安装或未找到!"
    echo "请先安装 nvm:"
    echo "  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash"
    exit 1
fi

echo "√ 环境检查完成"

echo
echo "[3/3] 执行NPM打包..." 
cd "$PROJECT_HOME" || exit 1

# 使用 nvm 并设置 Node.js 版本
if nvm use v14.18.3; then
    echo "当前 Node.js 版本: $(node --version)"
else
    echo "错误: Node.js v14.18.3 未安装!"
    echo "请安装: nvm install v14.18.3"
    exit 1
fi

# 执行构建
if yarn build; then
    echo
    echo "√ yarn打包成功"   
else
    echo
    echo "× yarn打包失败!"
    exit 1
fi

chmod -R 755 bosg-ui

echo
echo "🎉 所有步骤已完成!"
