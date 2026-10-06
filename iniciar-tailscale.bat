@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList '-NoLogo -NoProfile -ExecutionPolicy Bypass -File ""%~dp0tools\windows-pairing\start-tailnet-host.ps1""'"
