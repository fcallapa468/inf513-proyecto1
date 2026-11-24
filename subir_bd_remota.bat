@echo off
chcp 65001 > nul
echo ========================================================
echo    ACTUALIZAR BASE DE DATOS REMOTA - TECNOWEB (UAGRM)
echo ========================================================
echo Servidor: mail.tecnoweb.org.bo : 5432
echo Base de Datos: db_grupo04sc
echo Usuario: grupo04sc
echo.

set "PGPASSWORD=grup004grup004*"
set "PSQL_PATH="

where psql >nul 2>nul
if %errorlevel% equ 0 (
    set "PSQL_PATH=psql"
    goto :psql_ready
)

for %%V in (18 17 16 15 14 13) do (
    if exist "C:\Program Files\PostgreSQL\%%V\bin\psql.exe" (
        set "PSQL_PATH=C:\Program Files\PostgreSQL\%%V\bin\psql.exe"
        goto :psql_ready
    )
)

:psql_ready
if not defined PSQL_PATH (
    echo [ERROR] No se encontro psql.exe en PATH ni en C:\Program Files\PostgreSQL\
    echo Debes tener instalado PostgreSQL o agregar su carpeta bin al PATH.
    pause
    exit /b 1
)

echo [1/2] Probando conexion con el servidor...
"%PSQL_PATH%" -U grupo04sc -h mail.tecnoweb.org.bo -p 5432 -d db_grupo04sc -c "SELECT current_database(), current_user, version();"
if errorlevel 1 (
    echo.
    echo --------------------------------------------------------------------------
    echo [ERROR] No se pudo conectar a la base de datos en mail.tecnoweb.org.bo:5432.
    echo.
    echo Causas comunes:
    echo 1. Tu IP actual esta bloqueada por el firewall/fail2ban del servidor.
    echo    - Prueba conectarte usando datos moviles [Hotspot del celular] o reinicia tu router.
    echo 2. El servidor del docente tiene los puertos cerrados temporalmente por el ataque.
    echo --------------------------------------------------------------------------
    pause
    exit /b 1
)

set "SQL_FILE=%~dp0bolsa_trabajo_bd.sql"
if not exist "%SQL_FILE%" set "SQL_FILE=%~dp0..\bolsa_trabajo_bd.sql"

echo.
echo [2/2] Ejecutando bolsa_trabajo_bd.sql en el servidor remoto...
"%PSQL_PATH%" -U grupo04sc -h mail.tecnoweb.org.bo -p 5432 -d db_grupo04sc -f "%SQL_FILE%"

if errorlevel 1 (
    echo.
    echo [ERROR] Hubo un error al ejecutar el script SQL.
) else (
    echo.
    echo ========================================================
    echo  ¡ESTRUCTURA Y DATOS SUBIDOS CON EXITO AL SERVIDOR!
    echo ========================================================
)

pause