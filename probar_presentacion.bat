@echo off
chcp 65001 > nul
cd /d "%~dp0"
title Verificación de Flujo de Presentación - Grupo 04
echo ===================================================================
echo   VERIFICACION AUTOMATICA DE LA DEMOSTRACION PARA LA PRESENTACION
echo                 Bolsa de Trabajos UAGRM - Grupo 04
echo ===================================================================
echo.
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

java -cp "target\classes;target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar" bo.uagrm.bolsa.simulador.PruebaFlujoPresentacion
echo.
pause
