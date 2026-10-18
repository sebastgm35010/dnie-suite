@echo off
setlocal
cd /d "%~dp0"
python tools\export_source_bundle.py %*
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Ocurrio un problema al generar la exportacion.
    pause
    exit /b %ERRORLEVEL%
)
