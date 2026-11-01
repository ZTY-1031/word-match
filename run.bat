@echo off
chcp 65001 >nul
setlocal
title 接词挑战

set "ROOT=%~dp0"
set "JAR=%ROOT%backend\target\word-match-0.1.0.jar"

if not exist "%JAR%" (
  echo [x] 还没打包。先在 backend 目录执行:
  echo     mvn -DskipTests clean package
  pause
  exit /b 1
)

rem 优先用 JDK 17+；本机默认 java 可能是 1.8，所以顺手探测一下常见路径
set "JAVA=java"
if exist "E:\jdk17\bin\java.exe" set "JAVA=E:\jdk17\bin\java.exe"

echo 启动中: http://localhost:8080
start "" http://localhost:8080
"%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --server.port=8080

echo.
echo 服务已退出。
pause
