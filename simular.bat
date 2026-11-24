@echo off
cd /d "%~dp0"
title Simulador de Comandos - Bolsa de Trabajos Grupo 04
echo ===================================================================
echo Iniciando Simulador Interactivo (Sin necesidad de Sockets SMTP)...
echo Bolsa de Trabajos UAGRM - Grupo 04
echo ===================================================================
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if not exist target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar (
    echo Compilando proyecto primero...
    call mvn clean package -DskipTests
)

java -cp "target\classes;target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar" bo.uagrm.bolsa.simulador.Simulador
pause
