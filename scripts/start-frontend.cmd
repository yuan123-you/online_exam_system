@echo off
set "ROOT=%~dp0.."
cd /d "%ROOT%"
"D:\nodejs\npm.cmd" run dev:web > "%ROOT%\frontend.run.log" 2>&1
