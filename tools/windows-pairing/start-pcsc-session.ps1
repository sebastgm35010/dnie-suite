param([Parameter(Mandatory=$true)][string]$Session)
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Ejecutar este helper como administrador para el backend SYSTEM del lector.' }
$Session = [IO.Path]::GetFullPath($Session)
if ((Split-Path -Parent $Session) -ne 'D:\DNIeToolchain\Pairings' -or (Split-Path -Leaf $Session) -notmatch '^[a-f0-9]{32}$') { throw 'Carpeta de emparejamiento no permitida.' }
$config = Get-Content -LiteralPath (Join-Path $Session 'server-private.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$expires = if ($config.pairingExpiresAt -is [DateTime]) {
    [DateTimeOffset]::new($config.pairingExpiresAt).ToUniversalTime()
} else { [DateTimeOffset]::Parse($config.pairingExpiresAt, [Globalization.CultureInfo]::InvariantCulture) }
if ($expires -le [DateTimeOffset]::UtcNow) { throw 'Emparejamiento vencido.' }
$config.pairingExpiresAt = $expires.ToString('O')
$tailnet = $config.transport -eq 'tailscale_tcp'
$bindAllowed = if ($tailnet) {
    $address = [Net.IPAddress]::Parse($config.host).GetAddressBytes()
    $localAddresses = @(Get-NetIPAddress -AddressFamily IPv4 | Select-Object -ExpandProperty IPAddress)
    $address.Length -eq 4 -and $address[0] -eq 100 -and $address[1] -ge 64 -and $address[1] -le 127 -and
        $localAddresses -contains $config.host -and $config.peerHost -match '^[a-z0-9-]+\.[a-z0-9-]+\.ts\.net$'
} else { $config.host -eq '127.0.0.1' }
if (-not $bindAllowed -or $config.port -ne 39443 -or [IO.Path]::GetFullPath($config.certificatePath) -ne (Join-Path $Session 'bridge.pfx')) { throw 'Configuracion fuera del perfil autorizado.' }
$protectedRoot = 'D:\DNIeBridgePrivileged'
$sid = $identity.User.Value
New-Item -ItemType Directory -Path $protectedRoot -Force | Out-Null
if ((Get-Item -LiteralPath $protectedRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'El directorio protegido no puede ser un enlace.' }
& icacls.exe $protectedRoot /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' "*$($sid):(OI)(CI)RX" | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo proteger el directorio del host.' }
& icacls.exe $protectedRoot /setowner '*S-1-5-32-544' | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo establecer propiedad administrativa.' }
$trusted = @('S-1-5-32-544','S-1-5-18',$sid)
foreach ($rule in (Get-Acl -LiteralPath $protectedRoot).Access) {
    if ($rule.AccessControlType -eq 'Allow' -and $trusted -notcontains $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value) { throw 'ACL del host contiene permisos inesperados.' }
}
$stage = Join-Path $protectedRoot (Split-Path -Leaf $Session)
if (Test-Path -LiteralPath $stage) { throw 'Esta sesion ya se preparo; crear un QR nuevo.' }
New-Item -ItemType Directory -Path $stage | Out-Null
foreach ($name in @('WindowsBridge.dll','WindowsBridge.deps.json','WindowsBridge.runtimeconfig.json')) {
    Copy-Item -LiteralPath (Join-Path 'D:\DNIeToolchain\PairingUi' $name) -Destination (Join-Path $stage $name)
}
Copy-Item -LiteralPath (Join-Path $Session 'bridge.pfx') -Destination (Join-Path $stage 'bridge.pfx')
$config.certificatePath = Join-Path $stage 'bridge.pfx'
$config | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage 'server-private.json') -Encoding UTF8
& icacls.exe $stage /setowner '*S-1-5-32-544' /T | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo establecer propiedad administrativa.' }
$taskName = 'DNIeBridge-Session-' + (Split-Path -Leaf $Session)
$command = '/c ""C:\Program Files\dotnet\dotnet.exe" ' + $stage + '\WindowsBridge.dll serve-pcsc --config ' + $stage + '\server-private.json > ' + $stage + '\host.log 2> ' + $stage + '\host-error.log"'
Import-Module ScheduledTasks
$action = New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32/cmd.exe') -Argument $command
$system = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
# Finite single session. Remove the registration after starting: the running process continues.
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 35)
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $system -Settings $settings | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Start-Sleep -Milliseconds 900
    if ((Get-ScheduledTask -TaskName $taskName).State -ne 'Running') { throw 'El host PC/SC no arranco; revisar registros protegidos.' }
    @{task=$taskName;stage=$stage;mode='serve-pcsc'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Session 'session.json') -Encoding UTF8
    Write-Host "Host PC/SC iniciado. Registros: $stage"
} finally {
    # No persistent trigger, service, firewall or reader ACL change.
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
}
