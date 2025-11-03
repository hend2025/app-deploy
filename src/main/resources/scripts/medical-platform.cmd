@echo off
setlocal enabledelayedexpansion

REM 设置UTF-8编码
chcp 65001 >nul
title Git拉取与Maven打包部署工具

REM 配置变量 - 可根据需要修改
set "WORKSPACE=D:\home\workspace"
set "BUILD_DIR=D:\home\build"
set "PROJECT_NAME=medical-platform" 
set "GIT_URL=http://192.168.24.21/shenxingping/medical-platform.git"
set "PROJECT_HOME=%WORKSPACE%\!PROJECT_NAME!"
set "REMOVE_SUFFIX=-generic-2.0.9-SNAPSHOT"

REM 参数定义
set "BRANCH_TAG=%~1"
 
if "%BRANCH_TAG%"=="" (
    echo 错误: 分支/Tag不能为空!
    echo 使用方法: %~nx0 ^<分支/Tag名称^>
    exit /b 1
) 

REM 清理分支/Tag名称中的特殊字符（用于文件名）
set "CLEAN_BRANCH_TAG=%BRANCH_TAG%"
set "CLEAN_BRANCH_TAG=!CLEAN_BRANCH_TAG:/=-!"
set "CLEAN_BRANCH_TAG=!CLEAN_BRANCH_TAG:\=-!"
set "CLEAN_BRANCH_TAG=!CLEAN_BRANCH_TAG::=-!"

REM 创建必要目录
if not exist "%WORKSPACE%" (
    echo 创建工作空间目录: %WORKSPACE%
    mkdir "%WORKSPACE%"
    if errorlevel 1 (
        echo 错误: 创建工作空间目录失败!
        exit /b 1
    )
)

if not exist "%BUILD_DIR%" (
    echo 创建构建目录: %BUILD_DIR%
    mkdir "%BUILD_DIR%"
    if errorlevel 1 (
        echo 错误: 创建构建目录失败!
        exit /b 1
    )
)

REM 克隆或更新代码
echo.
echo [1/3] 克隆/更新代码库...
if exist "!PROJECT_HOME!" (
    echo 项目目录已存在，执行更新操作...
    cd /d "!PROJECT_HOME!"
    
    REM 检查当前是否在正确的分支
    set "current_branch="
    for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set "current_branch=%%i"
    
    echo 当前分支: !current_branch!
    echo 目标分支: %BRANCH_TAG%
    
    if "!current_branch!"=="%BRANCH_TAG%" (
        echo 已在目标分支，执行拉取...
        git pull origin %BRANCH_TAG%
        if errorlevel 1 (
            echo 警告: Git拉取失败，尝试继续构建...
        )
    ) else (
        echo 切换到分支/Tag: %BRANCH_TAG%
        
        REM 先尝试切换到已有分支或Tag
        git checkout %BRANCH_TAG% 2>nul
        if errorlevel 1 (
            echo 分支/Tag不存在，尝试从origin拉取...
            git fetch origin
            
            REM 尝试创建并切换到新分支
            git checkout -b %BRANCH_TAG% origin/%BRANCH_TAG% 2>nul
            if errorlevel 1 (
                REM 可能是tag，直接checkout
                git checkout %BRANCH_TAG% 2>nul
                if errorlevel 1 (
                    echo 错误: 无法切换到指定分支/Tag: %BRANCH_TAG%
                    echo 请确认分支/Tag是否存在
                    exit /b 1
                )
            )
        )
        
        REM 拉取最新代码（如果是分支）
        git pull origin %BRANCH_TAG% 2>nul
        if errorlevel 1 (
            echo 警告: 拉取失败，可能Tag不需要拉取，继续构建...
        )
    )
) else (
    echo 项目目录不存在，执行克隆操作...
    cd /d "%WORKSPACE%"
    echo 执行: git clone -b %BRANCH_TAG% "%GIT_URL%" "!PROJECT_NAME!"
    
    git clone -b %BRANCH_TAG% "%GIT_URL%" "!PROJECT_NAME!"
    if errorlevel 1 (
        echo 错误: Git克隆失败!
        echo 请检查: 
        echo   - Git地址是否正确: %GIT_URL%
        echo   - 分支/Tag是否存在: %BRANCH_TAG%  
        echo   - 网络连接是否正常
        echo   - 是否有访问权限
        exit /b 1
    )
)

echo √ 代码库操作完成

REM 进入项目目录并执行Maven打包
echo.
echo [2/3] 执行Maven打包... 
cd /d "!PROJECT_HOME!"

if errorlevel 1 (
    echo 错误: 无法进入项目目录: !PROJECT_HOME!
    exit /b 1
)
 
echo 执行: call mvn clean install -o -T 8 -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dgpg.skip
call mvn clean install -o -T 8 -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dgpg.skip

REM 检查Maven执行结果
if errorlevel 1 (
    echo.
    echo 错误: Maven打包失败!
    exit /b 1
)

echo.
echo √ Maven打包成功

REM 查找并复制JAR文件
echo.
echo [3/3] 查找并处理JAR文件...
set "JAR_COUNT=0"
set "COPIED_COUNT=0"
set "FOUND_JARS=0"
echo.
echo 开始搜索JAR文件...

REM 使用dir命令查找文件
for /f "delims=" %%i in ('dir /b /s "*%REMOVE_SUFFIX%.jar" 2^>nul') do (
    if exist "%%i" (
        set /a JAR_COUNT+=1
        set /a FOUND_JARS=1
        echo 找到JAR文件[!JAR_COUNT!]: %%~nxi
        echo 完整路径: %%i         
		
        REM 提取原文件名并重命名，添加分支/Tag后缀
        set "original_name=%%~ni"
        echo 原文件名: !original_name!		 
        
        REM 方法1: 使用字符串替换 - 这是正确的方法
        set "new_name=!original_name:%REMOVE_SUFFIX%=!"
        echo 替换后文件名: !new_name!
        
        REM 方法2: 如果方法1不工作，使用这个替代方法
        if "!new_name!"=="!original_name!" (
            echo 字符串替换未生效，使用替代方法...
            set "new_name=!original_name:-generic-2.0.9-SNAPSHOT=!"
            echo 替代方法结果: !new_name!
        )
        
        set "new_name=!new_name!-!CLEAN_BRANCH_TAG!.jar"
        echo 最终文件名: !new_name!
		       
        copy "%%i" "%BUILD_DIR%\!new_name!" >nul
        if errorlevel 1 (
            echo 错误: 复制失败 - %%~nxi
        ) else (
            set /a COPIED_COUNT+=1
            echo √ 复制成功
        )
        echo.
    )
)

if !JAR_COUNT! equ 0 (
    echo 错误: 未找到任何匹配的JAR文件!
    echo 搜索模式: *%REMOVE_SUFFIX%.jar
    exit /b 1
)

if !COPIED_COUNT! equ 0 (
    echo 错误: 所有JAR文件复制都失败了!
    echo 请检查构建目录权限: %BUILD_DIR%
    exit /b 1
)

echo 成功处理 !COPIED_COUNT!/!JAR_COUNT! 个JAR文件
echo √ JAR文件处理完成

echo.
echo ========================================
echo 构建完成!
echo 分支/Tag: %BRANCH_TAG%
echo 输出目录: %BUILD_DIR%
echo ========================================

endlocal