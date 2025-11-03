#!/bin/bash

# 设置字符编码
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 配置变量 - 可根据需要修改
WORKSPACE="/aeye/workspace"
BUILD_DIR="/aeye/build"
PROJECT_NAME="medical-platform"
GIT_URL="http://192.168.24.21/shenxingping/medical-platform.git"
PROJECT_HOME="$WORKSPACE/$PROJECT_NAME"
REMOVE_SUFFIX="-generic-2.0.9-SNAPSHOT"

# 参数检查
BRANCH_TAG="$1"

if [ -z "$BRANCH_TAG" ]; then
    echo "错误: 分支/Tag不能为空!"
    echo "使用方法: $0 <分支/Tag名称>"
    exit 1
fi

# 清理分支/Tag名称中的特殊字符（用于文件名）
CLEAN_BRANCH_TAG=$(echo "$BRANCH_TAG" | sed 's/[\/\\:]/-/g')

# 创建必要目录
if [ ! -d "$WORKSPACE" ]; then
    echo "创建工作空间目录: $WORKSPACE"
    mkdir -p "$WORKSPACE"
    if [ $? -ne 0 ]; then
        echo "错误: 创建工作空间目录失败!"
        exit 1
    fi
fi

if [ ! -d "$BUILD_DIR" ]; then
    echo "创建构建目录: $BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    if [ $? -ne 0 ]; then
        echo "错误: 创建构建目录失败!"
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

# 进入项目目录并执行Maven打包
echo
echo "[2/3] 执行Maven打包..." 
cd "$PROJECT_HOME" || exit 1

echo "执行: mvn clean install -o -T 8 -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dgpg.skip"
mvn clean install -o -T 8 -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dgpg.skip

# 检查Maven执行结果
if [ $? -ne 0 ]; then
    echo
    echo "错误: Maven打包失败!"
    exit 1
fi

echo
echo "√ Maven打包成功"

# 查找并复制JAR文件
echo
echo "[3/3] 查找并处理JAR文件..."
JAR_COUNT=0
COPIED_COUNT=0
FOUND_JARS=0
echo
echo "开始搜索JAR文件..."

# 使用find命令查找文件
while IFS= read -r -d '' jar_file; do
    if [ -f "$jar_file" ]; then
        JAR_COUNT=$((JAR_COUNT + 1))
        FOUND_JARS=1
        echo "找到JAR文件[$JAR_COUNT]: $(basename "$jar_file")"
        echo "完整路径: $jar_file"
        
        # 提取原文件名并重命名，添加分支/Tag后缀
        original_name=$(basename "$jar_file" .jar)
        echo "原文件名: $original_name"
        
        # 使用sed进行字符串替换
        new_name=$(echo "$original_name" | sed "s/$REMOVE_SUFFIX//")
        echo "替换后文件名: $new_name"
        
        # 如果替换未生效，使用替代方法
        if [ "$new_name" = "$original_name" ]; then
            echo "字符串替换未生效，使用替代方法..."
            new_name=$(echo "$original_name" | sed 's/-generic-2.0.9-SNAPSHOT//')
            echo "替代方法结果: $new_name"
        fi
        
        new_name="${new_name}-${CLEAN_BRANCH_TAG}.jar"
        echo "最终文件名: $new_name"
        
        cp "$jar_file" "$BUILD_DIR/$new_name"
        if [ $? -ne 0 ]; then
            echo "错误: 复制失败 - $(basename "$jar_file")"
        else
            COPIED_COUNT=$((COPIED_COUNT + 1))
            echo "√ 复制成功"
        fi
        echo
    fi
done < <(find "$PROJECT_HOME" -name "*${REMOVE_SUFFIX}.jar" -type f -print0)

if [ $JAR_COUNT -eq 0 ]; then
    echo "错误: 未找到任何匹配的JAR文件!"
    echo "搜索模式: *${REMOVE_SUFFIX}.jar"
    exit 1
fi

if [ $COPIED_COUNT -eq 0 ]; then
    echo "错误: 所有JAR文件复制都失败了!"
    echo "请检查构建目录权限: $BUILD_DIR"
    exit 1
fi

echo "成功处理 ${COPIED_COUNT}/${JAR_COUNT} 个JAR文件"
echo "√ JAR文件处理完成"

echo
echo "========================================"
echo "构建完成!"
echo "分支/Tag: $BRANCH_TAG"
echo "输出目录: $BUILD_DIR"
echo "========================================"