@echo off
REM ---------------------------------------------------------------------------
REM Simplified Maven Wrapper for Windows.
REM Downloads Apache Maven 3.9.9 to a local cache on first run, then invokes it.
REM Fallback if the download fails:  winget install Apache.Maven   (then use mvn)
REM ---------------------------------------------------------------------------

setlocal enableextensions

set MAVEN_VERSION=3.9.9
set MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
set CACHE_DIR=%USERPROFILE%\.m2\w\apache-maven-%MAVEN_VERSION%
set MAVEN_HOME=%CACHE_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd

if not exist "%MAVEN_CMD%" (
    echo [mvnw] Downloading Apache Maven %MAVEN_VERSION% to "%CACHE_DIR%"
    if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ErrorActionPreference='Stop'; Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%CACHE_DIR%\maven.zip'; Expand-Archive -Path '%CACHE_DIR%\maven.zip' -DestinationPath '%CACHE_DIR%' -Force; Remove-Item '%CACHE_DIR%\maven.zip'"
    if errorlevel 1 (
        echo [mvnw] ERROR: failed to download Maven. Install it manually:  winget install Apache.Maven
        exit /b 1
    )
    if not exist "%MAVEN_CMD%" (
        echo [mvnw] ERROR: Maven downloaded but mvn.cmd not found at "%MAVEN_CMD%"
        exit /b 1
    )
)

"%MAVEN_CMD%" %*
