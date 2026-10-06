param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$BridgeLog,
    [Parameter(Mandatory)][string]$AdbDevice,
    [Parameter(Mandatory)][string]$AdbExecutable
)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$node=(Get-Command node.exe).Source
$python=(Get-Command python.exe).Source
$powershell=(Get-Command powershell.exe).Source
$seconds=1800
$edge=Start-Process $node -ArgumentList ('"'+$PSScriptRoot+'\edge-capture.mjs" --port 19371 --seconds '+$seconds+' --output "'+$OutputDirectory+'\edge.jsonl" --stop "'+$OutputDirectory+'\stop.txt"') -WindowStyle Hidden -PassThru -RedirectStandardError "$OutputDirectory\edge-error.txt" -RedirectStandardOutput "$OutputDirectory\edge-out.txt"
$hostWatcher=Start-Process $powershell -ArgumentList ('-NoProfile -File "'+$PSScriptRoot+'\watch-host.ps1" -OutputDirectory "'+$OutputDirectory+'" -Seconds '+$seconds) -WindowStyle Hidden -PassThru -RedirectStandardError "$OutputDirectory\host-error.txt" -RedirectStandardOutput "$OutputDirectory\host-out.txt"
$tail=Start-Process $python -ArgumentList ('"'+$PSScriptRoot+'\watch-live-logs.py" --output "'+$OutputDirectory+'" --host "'+$BridgeLog+'" --seconds '+$seconds) -WindowStyle Hidden -PassThru -RedirectStandardError "$OutputDirectory\live-error.txt" -RedirectStandardOutput "$OutputDirectory\live-out.txt"
$phone=Start-Process $python -ArgumentList ('"'+$PSScriptRoot+'\watch-phone-apdu.py" --adb "'+$AdbExecutable+'" --device "'+$AdbDevice+'" --output "'+$OutputDirectory+'" --seconds '+$seconds) -WindowStyle Hidden -PassThru -RedirectStandardError "$OutputDirectory\phone-error.txt" -RedirectStandardOutput "$OutputDirectory\phone-out.txt"
@{directory=$OutputDirectory;startedUtc=[DateTimeOffset]::UtcNow.ToString('O');limitSeconds=$seconds;collectorPid=$edge.Id;watcherPid=$hostWatcher.Id;tailPid=$tail.Id;phonePid=$phone.Id;bridgeLog=$BridgeLog;mode='normal_user_capture'} | ConvertTo-Json | Set-Content -LiteralPath "$OutputDirectory\session.json"
Write-Output "Captura de usuario iniciada: $OutputDirectory"
