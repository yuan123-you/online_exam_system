@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
title 在线考试系统

echo ==========================================
echo   在线考试系统 - 启动脚本
echo ==========================================
echo.

:: 检查 Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 未检测到 Java，请安装 JDK 21 并配置环境变量。
    pause
    exit /b 1
)

:: 检查 Maven
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 未检测到 Maven，请安装 Maven 并配置环境变量。
    pause
    exit /b 1
)

:: 编译打包（跳过测试）
echo [1/3] 正在构建前端...
call npx vite build
if %ERRORLEVEL% neq 0 (
    echo [错误] 前端构建失败，请检查错误信息。
    pause
    exit /b 1
)

echo [2/3] 正在编译后端...
call mvn -s .mvn\settings.xml -f backend\pom.xml -DskipTests package -q
if %ERRORLEVEL% neq 0 (
    echo [错误] 编译失败，请检查错误信息。
    pause
    exit /b 1
)

:: 启动应用
echo [3/3] 正在启动应用...
echo.

:: 读取 .env 文件中的环境变量
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "line=%%a"
        if not "!line:~0,1!"=="#" (
            set "%%a=%%b"
        )
    )
    echo   [env] Loaded .env file
)

echo   后端地址: http://localhost:8080
echo   前端开发: npm run dev:web (另开终端)
echo   停止服务: Ctrl+C
echo.
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar backend\target\online-exam-backend-1.0.0.jar

pause
