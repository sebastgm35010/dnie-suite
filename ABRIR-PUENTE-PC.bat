@echo off
rem Compatibility entry only. The native desktop shortcut is the normal entry.
if exist "%USERPROFILE%\Desktop\DNIe Bridge.lnk" (
    start "" "%USERPROFILE%\Desktop\DNIe Bridge.lnk"
) else (
    echo Abra DNIeBridge.exe e instale el servicio. Use el acceso DNIe Bridge del escritorio.
)
