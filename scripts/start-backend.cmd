@echo off
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
node "%ROOT%\scripts\run-backend.js"
