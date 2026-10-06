param([switch]$Stop,
    [string]$HostName = 'sebas-laptop.tail2752e9.ts.net',
    [string]$PeerName = 'sebas-poco.tail2752e9.ts.net')
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Ejecutar como administrador para el backend PC/SC.' }
foreach ($endpointName in @($HostName,$PeerName)) {
    if ($endpointName -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$') { throw 'Nombre Tailscale invalido.' }
}
$address = [Net.Dns]::GetHostAddresses($hostName) | Where-Object { $b=$_.GetAddressBytes(); $b.Length -eq 4 -and $b[0] -eq 100 -and $b[1] -ge 64 -and $b[1] -le 127 } | Select-Object -First 1
if (-not $address) { throw 'Activa Tailscale en la laptop.' }
$listeners = @(Get-NetTCPConnection -State Listen -LocalAddress $address.IPAddressToString -LocalPort 39443 -ErrorAction SilentlyContinue)
foreach ($listener in $listeners) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($process.CommandLine -notlike '*D:\DNIeBridgePrivileged\*WindowsBridge.dll*') { throw 'El puerto esta ocupado por otro programa; no se detuvo.' }
    Stop-Process -Id $listener.OwningProcess -Force
}
if ($Stop) { Write-Host 'Host Tailscale detenido.'; return }
$root = 'D:\DNIeBridgePrivileged'
New-Item -ItemType Directory -Path $root -Force | Out-Null
if ((Get-Item -LiteralPath $root).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'El directorio protegido no puede ser un enlace.' }
$sid = $identity.User.Value
& icacls.exe $root /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' "*$($sid):(OI)(CI)RX" | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo proteger el host.' }
& icacls.exe $root /setowner '*S-1-5-32-544' | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo establecer la propiedad.' }
foreach ($rule in (Get-Acl -LiteralPath $root).Access) {
    if ($rule.AccessControlType -eq 'Allow' -and @('S-1-5-32-544','S-1-5-18',$sid) -notcontains $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value) { throw 'Permisos inesperados en el host.' }
}
$stage = Join-Path $root ('tailnet-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($name in @('WindowsBridge.dll','WindowsBridge.deps.json','WindowsBridge.runtimeconfig.json')) {
    Copy-Item -LiteralPath (Join-Path 'D:\DNIeToolchain\PairingUi' $name) -Destination (Join-Path $stage $name)
}
& icacls.exe $stage /setowner '*S-1-5-32-544' /T | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo establecer la propiedad del host.' }
$taskName = 'DNIeBridge-Tailnet-' + [Guid]::NewGuid().ToString('N')
$command = '/c ""C:\Program Files\dotnet\dotnet.exe" ' + $stage + '\WindowsBridge.dll serve-tailnet --host ' + $hostName + ' --peer ' + $peerName + ' > ' + $stage + '\host.log 2> ' + $stage + '\host-error.log"'
Import-Module ScheduledTasks
$action = New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32/cmd.exe') -Argument $command
$system = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero)
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $system -Settings $settings | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Start-Sleep -Milliseconds 900
    if ((Get-ScheduledTask -TaskName $taskName).State -ne 'Running') { throw "El host no arranco. Revisar $stage" }
    Write-Host "Host Tailscale reutilizable iniciado. Registros: $stage"
} finally { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue }
