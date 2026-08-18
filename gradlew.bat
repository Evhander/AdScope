@echo off
setlocal
set "GRADLE_VERSION=8.13"
set "PROJECT_DIR=%~dp0"
set "DIST_DIR=%USERPROFILE%\.gradle\wrapper\dists\adscope-gradle-%GRADLE_VERSION%"
set "GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
if errorlevel 1 exit /b 1

echo Extracting Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_FILE%' '%DIST_DIR%'"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_HOME%\bin\gradle.bat" -p "%PROJECT_DIR%" %*
endlocal
