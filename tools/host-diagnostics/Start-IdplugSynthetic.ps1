param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference='Stop'
$principal=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Admin required for the existing reader backend'}
$Directory=[IO.Path]::GetFullPath($Directory)
if((Split-Path -Parent $Directory) -ne 'D:\DNIeToolchain\IdplugSynthetic' -or (Split-Path -Leaf $Directory) -notmatch '^[a-f0-9]{32}$'){throw 'Invalid synthetic directory'}
if((Get-Item -LiteralPath $Directory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'No directory links'}
$stage=Join-Path 'D:\DNIeBridgePrivileged' ('idplug-synthetic-'+(Split-Path -Leaf $Directory))
if(Test-Path -LiteralPath $stage){throw 'Use a fresh synthetic directory'}
New-Item -ItemType Directory -Path $stage | Out-Null
& icacls.exe $stage /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if($LASTEXITCODE){throw 'Stage ACL failed'}
Get-ChildItem -LiteralPath 'D:\DNIeToolchain\ReaderSynthetic' -File | Where-Object {$_.Extension -in '.dll','.json' -and $_.Name -ne 'result.json'} | Copy-Item -Destination $stage
$task='DNIeBridge-IdplugSynthetic-'+(Split-Path -Leaf $Directory)
$command='/c ""C:\Program Files\dotnet\dotnet.exe" '+$stage+'\ReaderSynthetic.Tests.dll --idplug-fixture '+$Directory+' > '+$Directory+'\fixture-out.txt 2> '+$Directory+'\fixture-error.txt"'
Import-Module ScheduledTasks
$action=New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32/cmd.exe') -Argument $command
$identity=New-ScheduledTaskPrincipal -UserId SYSTEM -LogonType ServiceAccount -RunLevel Highest
$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 5)
try {
 Register-ScheduledTask -TaskName $task -Action $action -Principal $identity -Settings $settings | Out-Null
 Start-ScheduledTask -TaskName $task
 Start-Sleep -Seconds 1
 Write-Output "Synthetic detection fixture started; four-minute lifetime. Capture: $Directory"
} finally {Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue}
