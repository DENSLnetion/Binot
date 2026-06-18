@echo off
REM Minimal Gradle Wrapper launcher for Windows

set SCRIPT_DIR=%~dp0
set WRAPPER_JAR=%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo ERROR: gradle-wrapper.jar tidak ditemukan di %SCRIPT_DIR%gradle\wrapper\
  echo Silakan unduh gradle-wrapper.jar dari distribusi Gradle dan tempatkan di gradle\wrapper\
  exit /b 1
)

java -jar "%WRAPPER_JAR%" %*
