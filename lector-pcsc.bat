@echo off
setlocal
if exist "D:\DNIeToolchain\VS2022\Common7\Tools\VsDevCmd.bat" (
  call "D:\DNIeToolchain\VS2022\Common7\Tools\VsDevCmd.bat" -no_logo -arch=x64 -host_arch=x64
  if errorlevel 1 exit /b 1
)
if /i "%~1"=="diagnostico" goto diagnostico
if /i "%~1"=="compilar" goto compilar
if /i "%~1"=="instalar" goto instalar
if /i "%~1"=="herramientas" goto herramientas
if not "%~1"=="" goto ayuda
echo.
echo DNIe - lector PC/SC propio
echo 1. Diagnosticar herramientas y privilegios
echo 2. Compilar backend, pruebas y controlador
echo 3. Ver instrucciones para instalar un paquete firmado
choice /c 123 /n /m "Selecciona 1, 2 o 3: "
if errorlevel 3 goto ayuda
if errorlevel 2 goto compilar
goto diagnostico
:diagnostico
powershell.exe -NoLogo -NoProfile -File "%~dp0tools\windows-pcsc-driver\deploy.ps1" -Mode Diagnostico
goto fin
:compilar
powershell.exe -NoLogo -NoProfile -File "%~dp0tools\windows-pcsc-driver\deploy.ps1" -Mode Compilar
goto fin
:instalar
if "%~2"=="" goto ayuda
powershell.exe -NoLogo -NoProfile -File "%~dp0tools\windows-pcsc-driver\deploy.ps1" -Mode Instalar -PackageDirectory "%~2"
goto fin
:herramientas
powershell.exe -NoLogo -NoProfile -File "%~dp0tools\windows-pcsc-driver\install-toolchain.ps1"
goto fin
:ayuda
echo Uso:
echo   lector-pcsc.bat diagnostico
echo   lector-pcsc.bat compilar
echo   lector-pcsc.bat herramientas
echo   lector-pcsc.bat instalar "C:\ruta\paquete-firmado"
echo La compilacion necesita MSVC y WDK. La instalacion necesita un paquete firmado y DevCon del WDK.
echo No habilita test-signing ni cambia firewall o politicas de ejecucion.
exit /b 2
:fin
set "lector_exit=%errorlevel%"
echo.
echo Resultado: %lector_exit%
if "%~1"=="" pause
exit /b %lector_exit%
