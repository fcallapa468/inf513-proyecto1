@echo off
cd /d "%~dp0"
title Compilar BolsaTrabajoMail - Grupo 04
echo =========================================================
echo Compilando Bolsa de Trabajos UAGRM (Maven + Java 17)...
echo =========================================================
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

call mvn clean package -DskipTests
if %ERRORLEVEL% equ 0 (
    echo.
    echo =========================================================
    echo [EXITO] Compilacion completada con exito.
    echo Archivo generado: target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar
    echo =========================================================
) else (
    echo.
    echo =========================================================
    echo [ERROR] Ocurrio un error en la compilacion.
    echo =========================================================
)
pause
