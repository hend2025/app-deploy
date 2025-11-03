#!/bin/bash

# 设置字符编码
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 配置变量 - 可根据需要修改
WORKSPACE="/aeye/workspace"
PROJECT_NAME="scene_institution"
GIT_URL="http://192.168.24.21/superman/scene_institution.git"
PROJECT_HOME="$WORKSPACE/$PROJECT_NAME"

# 设置严格错误检查
set -e

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
    mkdir -p "$WORKSPACE"
    if [ $? -ne 0 ]; then
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
    
    # 检查当前是否在正确的分支
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
    
    echo "当前分支: $CURRENT_BRANCH"
    echo "目标分支: $BRANCH_TAG"
    
    if [ "$CURRENT_BRANCH" = "$BRANCH_TAG" ]; then
        echo "已在目标分支，执行拉取..."
        git pull origin "$BRANCH_TAG"
        if [ $? -ne 0 ]; then
            echo "警告: Git拉取失败，尝试继续构建..."
        fi
    else
        echo "切换到分支/Tag: $BRANCH_TAG"
        
        # 先尝试切换到已有分支或Tag
        git checkout "$BRANCH_TAG" 2>/dev/null
        if [ $? -ne 0 ]; then
            echo "分支/Tag不存在，尝试从origin拉取..."
            git fetch origin
            
            # 尝试创建并切换到新分支
            git checkout -b "$BRANCH_TAG" "origin/$BRANCH_TAG" 2>/dev/null
            if [ $? -ne 0 ]; then
                # 可能是tag，直接checkout
                git checkout "$BRANCH_TAG" 2>/dev/null
                if [ $? -ne 0 ]; then
                    echo "错误: 无法切换到指定分支/Tag: $BRANCH_TAG"
                    echo "请确认分支/Tag是否存在"
                    exit 1
                fi
            fi
        fi
        
        # 拉取最新代码（如果是分支）
        git pull origin "$BRANCH_TAG" 2>/dev/null
        if [ $? -ne 0 ]; then
            echo "警告: 拉取失败，可能Tag不需要拉取，继续构建..."
        fi
    fi
else
    echo "项目目录不存在，执行克隆操作..."
    cd "$WORKSPACE" || exit 1
    echo "执行: git clone -b $BRANCH_TAG \"$GIT_URL\" \"$PROJECT_NAME\""
    
    git clone -b "$BRANCH_TAG" "$GIT_URL" "$PROJECT_NAME"
    if [ $? -ne 0 ]; then
        echo "错误: Git克隆失败!"
        echo "请检查: "
        echo "  - Git地址是否正确: $GIT_URL"
        echo "  - 分支/Tag是否存在: $BRANCH_TAG"  
        echo "  - 网络连接是否正常"
        echo "  - 是否有访问权限"
        exit 1
    fi
fi

echo "√ 代码库操作完成"

# 检查必要的命令是否存在
echo
echo "[2/3] 检查构建环境..."
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo "错误: $1 命令未找到!"
        echo "请安装 $1 或检查环境变量"
        return 1
    fi
    echo "√ $1 命令可用"
    return 0
}

# 检查 nvm
if [ -s "$HOME/.nvm/nvm.sh" ]; then
    source "$HOME/.nvm/nvm.sh"
    echo "√ nvm 已加载"
elif [ -s "/usr/local/opt/nvm/nvm.sh" ]; then
    source "/usr/local/opt/nvm/nvm.sh"
    echo "√ nvm 已加载"
else
    echo "错误: nvm 未安装或未找到!"
    echo "请先安装 nvm:"
    echo "  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash"
    exit 1
fi

# 检查 yarn
if ! check_command "yarn"; then
    echo "请安装 yarn:"
    echo "  npm install -g yarn"
    exit 1
fi

echo "√ 环境检查完成"

echo
echo "[3/3] 执行NPM打包..." 
cd "$PROJECT_HOME" || exit 1

# 使用 nvm 并设置 Node.js 版本
nvm use v14.18.3 || {
    echo "错误: Node.js v14.18.3 未安装!"
    echo "请安装: nvm install v14.18.3"
    exit 1
}

echo "当前 Node.js 版本: $(node --version)"
echo "当前 Yarn 版本: $(yarn --version)"

# 执行构建
yarn build

if [ $? -eq 0 ]; then
    echo
    echo "√ NPM打包成功"
else
    echo
    echo "× NPM打包失败!"
    exit 1
fi
