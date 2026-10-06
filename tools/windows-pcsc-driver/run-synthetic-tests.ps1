param([switch]$SessionRelay, [string]$CrossLayerDirectory)
# Run only the local synthetic test harness as SYSTEM for the initial reader ACL.
$ErrorActionPreference = 'Stop'
Import-Module ScheduledTasks
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out = Join-Path $repo 'build/pcsc-deployment'
$protected = 'D:\DNIeToolchain\ReaderSyntheticProtected'
$taskName = 'DNIeBridge-Synthetic-Test-Temporary'
$prefix = if ($SessionRelay) { 'reader-relay-synthetic' } else { 'reader-synthetic' }
if ($CrossLayerDirectory) {
    $CrossLayerDirectory = [IO.Path]::GetFullPath($CrossLayerDirectory)
    if ((Split-Path -Parent $CrossLayerDirectory) -ne 'D:\DNIeToolchain\CrossLayerTests' -or (Split-Path -Leaf $CrossLayerDirectory) -notmatch '^[a-f0-9]{32}$') { throw 'Ruta de prueba cruzada no permitida.' }
    $prefix = 'reader-browser-synthetic'
}
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { throw 'Ya existe la tarea de pruebas; revisar antes de ejecutar.' }
New-Item -ItemType Directory -Path $protected -Force | Out-Null
& icacls.exe $protected /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo proteger el ejecutable de prueba.' }
Get-ChildItem 'D:\DNIeToolchain\ReaderSynthetic' -File | Where-Object Extension -in @('.dll','.json') | Where-Object Name -ne 'result.json' | Copy-Item -Destination $protected -Force
$result = Join-Path $protected 'result.json'
if ($CrossLayerDirectory) { $result = Join-Path $CrossLayerDirectory 'result.json' }
if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
$mode = if ($SessionRelay) { '--session-relay ' } else { '' }
if ($CrossLayerDirectory) { $mode = '--browser-fixture ' }
$target = if ($CrossLayerDirectory) { $CrossLayerDirectory } else { $result }
$command = '/c ""C:\Program Files\dotnet\dotnet.exe" D:\DNIeToolchain\ReaderSyntheticProtected\ReaderSynthetic.Tests.dll ' + $mode + $target + ' > D:\DNIeToolchain\ReaderSyntheticProtected\output.log 2>&1"'
$action = New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32/cmd.exe') -Argument $command
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$limit = if ($CrossLayerDirectory) { 200 } else { 75 }
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Seconds ($limit + 15))
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Settings $settings | Out-Null
    Start-ScheduledTask -TaskName $taskName
    $deadline = (Get-Date).AddSeconds($limit)
    while (-not (Test-Path -LiteralPath $result) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
    if (-not (Test-Path -LiteralPath $result)) { throw 'La prueba no produjo resultado dentro del plazo.' }
    # Result is synthetic only. Export for review, not the privileged binaries.
    Copy-Item -LiteralPath $result -Destination (Join-Path $out ($prefix + '-result.json')) -Force
    Get-Content -LiteralPath $result
} finally {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath (Join-Path $protected 'output.log')) { Copy-Item -LiteralPath (Join-Path $protected 'output.log') -Destination (Join-Path $out ($prefix + '-output.log')) -Force }
}
