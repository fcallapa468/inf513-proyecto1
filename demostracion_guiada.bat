@echo off
chcp 65001 > nul
cd /d "%~dp0"
title Demostracion Guiada de Flujo Completo - Grupo 04
echo ===================================================================
echo     DEMOSTRACION GUIADA DE FLUJO COMPLETO - GRUPO 04 TECNOWEB
echo          (Recorrido Paso a Paso - Sin enviar correos)
echo ===================================================================
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

java -cp "target\classes;target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar" bo.uagrm.bolsa.simulador.DemostracionGuiada
echo.
pause
