param([ValidateSet('LAN','USB')][string]$Method='LAN',
    [Parameter(Mandatory=$true)][string]$Address,
    [ValidateRange(5,300)][int]$IdleSeconds=120,[switch]$Stop)
$ErrorActionPreference='Stop'
$ip=[Net.IPAddress]::Parse($Address)
$b=$ip.GetAddressBytes()
$private=$b.Length -eq 4 -and ($b[0] -eq 10 -or ($b[0] -eq 172 -and $b[1] -ge 16 -and $b[1] -le 31) -or ($b[0] -eq 192 -and $b[1] -eq 168))
if (-not $private -or @(Get-NetIPAddress -AddressFamily IPv4 | Select-Object -ExpandProperty IPAddress) -notcontains $Address) { throw 'Elige una IP privada asignada a la interfaz LAN o USB de esta PC.' }
if ($Stop) {
    $principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList ('-NoProfile -File "'+$PSCommandPath+'" -Address '+$Address+' -Stop') | Out-Null
        return
    }
    foreach ($owner in @(Get-NetTCPConnection -LocalAddress $Address -LocalPort 39443 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)) {
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$owner"
        if ($process.CommandLine -notlike '*D:\DNIeBridgePrivileged\*WindowsBridge.dll serve-pcsc*') { throw 'El puerto pertenece a otro programa. No se detuvo.' }
        Stop-Process -Id $owner -Force
    }
    return
}
# Refuse replacement of a currently listening/connected bridge, including Tailscale.
if (@(Get-NetTCPConnection -LocalPort 39443 -ErrorAction SilentlyContinue | Where-Object State -In @('Listen','Established','SynReceived')).Count) { throw 'Ya hay un host o sesion activa. Detenla antes de cambiar de transporte.' }
$runtime=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../runtime'))
if (-not (Test-Path (Join-Path $runtime 'WindowsPairing.dll'))) { $runtime='D:\DNIeToolchain\PairingUi' }
$dotnet=(Get-Command dotnet.exe -ErrorAction Stop).Source
$session=Join-Path 'D:\DNIeToolchain\Pairings' ([guid]::NewGuid().ToString('N'))
& $dotnet (Join-Path $runtime 'WindowsBridge.dll') setup --output $session --host $Address --port 39443 --idle-seconds $IdleSeconds
if ($LASTEXITCODE) { throw 'No se pudo generar el emparejamiento TLS.' }
& $dotnet (Join-Path $runtime 'WindowsPairing.dll') --config (Join-Path $session 'server-private.json')
if ($LASTEXITCODE) { throw 'No se pudo generar el QR.' }
$helper=Join-Path $PSScriptRoot 'start-pcsc-session.ps1'
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { & $helper -Session $session }
else {
    $admin=Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -PassThru -Wait -ArgumentList ('-NoProfile -File "'+$helper+'" -Session "'+$session+'"')
    if ($admin.ExitCode) { throw 'El backend PC/SC no pudo iniciar.' }
}
# Local page contains the session credential. Never served over HTTP or sent to analytics.
Start-Process (Join-Path $session 'pairing-qr.html') -WindowStyle Hidden
Write-Host "$Method TLS listo. QR valido por diez minutos. No se cambia el firewall ni se usa ADB."
