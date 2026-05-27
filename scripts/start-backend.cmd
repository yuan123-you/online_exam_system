@echo off
set "ROOT=%~dp0.."
cd /d "%ROOT%"
"C:\Program Files\Common Files\Oracle\Java\javapath\java.exe" -jar "%ROOT%\backend\target\online-exam-backend-1.0.0.jar" > "%ROOT%\backend.run.log" 2>&1
