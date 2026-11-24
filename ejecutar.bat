@echo off
cd /d "%~dp0"
title Servidor de Correo - BolsaTrabajoMail Grupo 04
echo =========================================================
echo Iniciando Servicio de Correo (POP3 / SMTP Listener)...
echo Bolsa de Trabajos UAGRM - Grupo 04
echo =========================================================
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if not exist target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar (
    echo El archivo JAR no existe. Compilando primero...
    call mvn clean package -DskipTests
)

java -jar target\bolsa-trabajo-mail-1.0-jar-with-dependencies.jar
pause