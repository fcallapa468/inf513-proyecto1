@echo off
chcp 65001 > nul
title Restablecer Base de Datos - Bolsa de Trabajos UAGRM
echo ===================================================================
echo     RESTABLECER BASE DE DATOS AL ESTADO INICIAL LIMPIO
echo                Bolsa de Trabajos UAGRM - Grupo 04
echo ===================================================================
echo.
echo Este proceso:
echo  1. Limpia los datos de prueba anteriores (esquema public).
echo  2. Recrea las 17 tablas y 11 vistas.
echo  3. Vuelve a cargar los datos semilla (usuarios, roles, planes, etc.).
echo.
if /i "%1"=="/y" goto :do_reset
if /i "%1"=="-y" goto :do_reset
if /i "%1"=="s" goto :do_reset
set /p CONFIRMAR="Deseas restablecer la base de datos ahora? (S/N): "
if /i not "%CONFIRMAR%"=="S" (
    echo Operacion cancelada por el usuario.
    pause
    exit /b 0
)
:do_reset

set "PGPASSWORD=grup004grup004*"
set "PSQL_PATH="

where psql >nul 2>nul
if %errorlevel% equ 0 (
    set "PSQL_PATH=psql"
    goto :psql_ready_res
)

for %%V in (18 17 16 15 14 13) do (
    if exist "C:\Program Files\PostgreSQL\%%V\bin\psql.exe" (
        set "PSQL_PATH=C:\Program Files\PostgreSQL\%%V\bin\psql.exe"
        goto :psql_ready_res
    )
)

:psql_ready_res
if not defined PSQL_PATH (
    echo [ERROR] No se encontro psql.exe en PATH ni en C:\Program Files\PostgreSQL\
    echo Debes tener instalado PostgreSQL o agregar su carpeta bin al PATH.
    pause
    exit /b 1
)

set "SQL_FILE=%~dp0bolsa_trabajo_bd.sql"
if not exist "%SQL_FILE%" set "SQL_FILE=%~dp0..\bolsa_trabajo_bd.sql"

echo.
echo Conectando a mail.tecnoweb.org.bo (db_grupo04sc)...
"%PSQL_PATH%" -U grupo04sc -h mail.tecnoweb.org.bo -p 5432 -d db_grupo04sc -f "%SQL_FILE%"

if errorlevel 1 (
    echo.
    echo --------------------------------------------------------------------------
    echo [ERROR] No se pudo restablecer la base de datos.
    echo Verifica tu conexion a internet o si el servidor del docente esta accesible.
    echo --------------------------------------------------------------------------
) else (
    echo.
    echo ===================================================================
    echo  ¡BASE DE DATOS RESTABLECIDA CON EXITO!
    echo  Ya puedes iniciar el flujo de prueba desde cero las veces que quieras.
    echo ===================================================================
)

pause