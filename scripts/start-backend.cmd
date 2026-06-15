@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0.."
cd /d "%ROOT%"

echo Compiling backend...
call mvn -s .mvn\settings.xml -f backend\pom.xml -DskipTests compile -q
if errorlevel 1 (
    echo Maven compile failed.
    pause
    exit /b 1
)

echo Resolving dependencies...
call mvn -s .mvn\settings.xml -f backend\pom.xml dependency:build-classpath -Dmdep.outputFile=target\cp.txt -q
if errorlevel 1 (
    echo Maven dependency resolution failed.
    pause
    exit /b 1
)

echo Starting backend...

:: 读取 .env 文件中的环境变量
if exist "%ROOT%\.env" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ROOT%\.env") do (
        set "line=%%a"
        if not "!line:~0,1!"=="#" (
            set "%%a=%%b"
        )
    )
    echo   [env] Loaded .env file
)

node "%ROOT%\scripts\run-backend.js"
