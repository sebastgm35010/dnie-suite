param([string] $JavaHome = $env:JAVA_HOME)
$ErrorActionPreference='Stop'
$distribution=Split-Path -Parent $PSScriptRoot
$java=if($JavaHome) { Join-Path $JavaHome 'bin/javaw.exe' } else { (Get-Command javaw.exe -ErrorAction Stop).Source }
if(-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Se requiere Java 17 con javaw.exe' }
$shell=New-Object -ComObject WScript.Shell
$shortcut=$shell.CreateShortcut((Join-Path $distribution 'DNIe Suite.lnk'))
$shortcut.TargetPath=$java
$shortcut.Arguments='-Dfile.encoding=UTF-8 -cp "'+(Join-Path $distribution 'lib/*')+'" pe.gob.reniec.dnie.desktop.ui.MainKt'
$shortcut.WorkingDirectory=$distribution
$shortcut.Description='Suite privada de firma DNIe'
$shortcut.Save()
Write-Output (Join-Path $distribution 'DNIe Suite.lnk')
