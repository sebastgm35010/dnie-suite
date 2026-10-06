@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\windows-pairing\pair-phone.ps1" -Tailscale
