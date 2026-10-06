@echo off
setlocal
powershell.exe -NoLogo -NoProfile -File "%~dp0tools\windows-pairing\pair-phone.ps1" %*
exit /b %errorlevel%
