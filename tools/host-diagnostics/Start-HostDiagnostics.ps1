param([switch]$Probe, [ValidateRange(1,60)][int]$Minutes = 30, [ValidateRange(1024,65535)][int]$Port = 19371)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
if (!(Test-Path -LiteralPath $edge)) { $edge = 'C:\Program Files\Microsoft\Edge\Application\msedge.exe' }
if (!(Test-Path -LiteralPath $edge)) { throw 'Microsoft Edge not found' }
if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue) { throw 'Diagnostic port is already in use' }
$capture = Join-Path $repo ('build\host-diagnostics\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $capture -Force | Out-Null
$profile = Join-Path 'D:\DNIeToolchain\EdgeDiagnostics' $(if ($Probe) {'probe-' + [Guid]::NewGuid().ToString('N')} else {'profile'})
$edgeArgs = @('--remote-debugging-address=127.0.0.1',"--remote-debugging-port=$Port", ('--user-data-dir="' + $profile + '"'), '--no-first-run', '--no-default-browser-check', 'about:blank')
if ($Probe) { $edgeArgs += '--headless=new' }
$edgeArgsText = $edgeArgs -join ' '
if ($Probe) { $edgeProcess = Start-Process -FilePath $edge -ArgumentList $edgeArgsText -WindowStyle Hidden -PassThru }
else { $edgeProcess = Start-Process -FilePath $edge -ArgumentList $edgeArgsText -PassThru }
$ready = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try { $null = Invoke-RestMethod "http://127.0.0.1:$Port/json/version" -TimeoutSec 1; $ready = $true; break } catch { Start-Sleep -Milliseconds 500 }
}
if (!$ready) { if (!$edgeProcess.HasExited) {$edgeProcess.Kill()}; throw 'Edge diagnostic startup failed' }
$listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen)
if ($listeners | Where-Object {$_.LocalAddress -notin @('127.0.0.1','::1')}) {
    if (!$edgeProcess.HasExited) {$edgeProcess.Kill()}; throw 'CDP is not restricted to loopback'
}
$node = (Get-Command node -ErrorAction Stop).Source
$collector = Join-Path $PSScriptRoot 'edge-capture.mjs'
if ($Probe) {
    & $node $collector --port $Port --probe
    if ($LASTEXITCODE -ne 0) { if (!$edgeProcess.HasExited) {$edgeProcess.Kill()}; throw 'Edge probe failed' }
    return
}
$nodeArgs = '"' + $collector + '" --port ' + $Port + ' --seconds ' + ($Minutes * 60) + ' --output "' + (Join-Path $capture 'edge.jsonl') + '" --stop "' + (Join-Path $capture 'stop.txt') + '"'
$collectorProcess = Start-Process $node -ArgumentList $nodeArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $capture 'collector-out.txt') -RedirectStandardError (Join-Path $capture 'collector-error.txt')
$powershell = (Get-Process -Id $PID).Path
$hostArgs = '-NoProfile -File "' + (Join-Path $PSScriptRoot 'watch-host.ps1') + '" -OutputDirectory "' + $capture + '" -Seconds ' + ($Minutes * 60)
$watcher = Start-Process $powershell -ArgumentList $hostArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $capture 'watcher-out.txt') -RedirectStandardError (Join-Path $capture 'watcher-error.txt')
@{port=$Port;edgePid=$edgeProcess.Id;collectorPid=$collectorProcess.Id;watcherPid=$watcher.Id;profile=$profile;directory=$capture} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $capture 'session.json') -Encoding utf8
Write-Output "Captura iniciada. Usa esta ventana de Edge para el flujo manual. Directorio: $capture"
Write-Output 'Los capturadores terminan por duración o al crear stop.txt. Cierra después esta ventana de Edge para desactivar CDP.'
