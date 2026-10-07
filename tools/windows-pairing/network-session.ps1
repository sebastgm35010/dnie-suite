param([ValidateSet('LAN','USB')][string]$Method='LAN', [string]$Address='127.0.0.1', [string]$Phone='', [switch]$Stop)
$ErrorActionPreference='Stop'
if ($Stop) {
    $principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList ('-NoProfile -File "'+$PSCommandPath+'" -Stop') | Out-Null
        return
    }
    $owners=@(Get-NetTCPConnection -LocalPort 39443 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
    $statePath='D:\DNIeToolchain\Desktop\host-state.json'
    if(Test-Path -LiteralPath $statePath){$record=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8|ConvertFrom-Json;$owners+=@([int]$record.pid)}
    foreach ($owner in @($owners | Select-Object -Unique)) {
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$owner"
        if(-not $process){continue}
        if ($process.CommandLine -notmatch 'D:\\DNIeBridgePrivileged\\[^ ]+\\WindowsBridge.dll serve-(pcsc|tailnet) ') { throw 'El puerto o PID pertenece a otro programa. No se detuvo.' }
        Stop-Process -Id $owner -Force
    }
    if(Test-Path -LiteralPath $statePath){Remove-Item -LiteralPath $statePath}
    return
}
$stateFile='D:\DNIeToolchain\Desktop\host-state.json'
if ($Stop) { return }
if (@(Get-NetTCPConnection -LocalPort 39443 -ErrorAction SilentlyContinue | Where-Object State -In @('Listen','Established','SynReceived')).Count) { throw 'Ya hay un host activo. Detenlo antes de cambiar de transporte.' }
if(Test-Path -LiteralPath $stateFile){$saved=Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8|ConvertFrom-Json;if(Get-Process -Id $saved.pid -ErrorAction SilentlyContinue){throw 'Ya hay un host USB o LAN activo. Detenlo primero.'}}
$runtime=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../runtime'))
if (-not (Test-Path (Join-Path $runtime 'WindowsPairing.dll'))) { $runtime='D:\DNIeToolchain\PairingUi' }
$dotnet=(Get-Command dotnet.exe -ErrorAction Stop).Source
$session=Join-Path 'D:\DNIeToolchain\Pairings' ([guid]::NewGuid().ToString('N'))
$identity=Join-Path 'D:\DNIeToolchain\PcIdentities' ([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)
$transport=if($Method -eq 'USB'){'usb_accessory'}else{'tls_reverse'}
if($Method -eq 'USB'){
    $Address='127.0.0.1'
    if($Phone -notmatch '^[a-zA-Z0-9_&.-]{1,200}$'){throw 'Selecciona la serie USB del telefono.'}
    if(-not(Test-Path 'C:\Program Files\UsbDk Runtime Library\UsbDkHelper.dll')){throw 'Falta UsbDk x64. Instala el componente USB firmado.'}
}else{
    $b=[Net.IPAddress]::Parse($Address).GetAddressBytes()
    $private=$b.Length -eq 4 -and ($b[0] -eq 10 -or ($b[0] -eq 172 -and $b[1] -ge 16 -and $b[1] -le 31) -or ($b[0] -eq 192 -and $b[1] -eq 168))
    if (-not $private -or @(Get-NetIPAddress -AddressFamily IPv4 | Select-Object -ExpandProperty IPAddress) -notcontains $Address) { throw 'Elige una IP privada de la interfaz LAN de esta PC.' }
    $b=[Net.IPAddress]::Parse($Phone).GetAddressBytes()
    if($b.Length -ne 4 -or -not ($b[0] -eq 10 -or ($b[0] -eq 172 -and $b[1] -ge 16 -and $b[1] -le 31) -or ($b[0] -eq 192 -and $b[1] -eq 168))){throw 'Introduce la IP Wi-Fi privada del telefono.'}
}
& $dotnet (Join-Path $runtime 'WindowsBridge.dll') setup --output $session --identity $identity --host $Address --phone $Phone --transport $transport --port 39443
if ($LASTEXITCODE) { throw 'No se pudo preparar la identidad permanente de la PC.' }
& $dotnet (Join-Path $runtime 'WindowsPairing.dll') --config (Join-Path $session 'server-private.json')
if ($LASTEXITCODE) { throw 'No se pudo generar el QR.' }
$helper=Join-Path $PSScriptRoot 'start-pcsc-session.ps1'
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { & $helper -Session $session }
else {
    $admin=Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -PassThru -Wait -ArgumentList ('-NoProfile -File "'+$helper+'" -Session "'+$session+'"')
    if ($admin.ExitCode) { throw 'El backend PC/SC no pudo iniciar.' }
}
Start-Process (Join-Path $session 'pairing-qr.html') -WindowStyle Hidden
Write-Host "$Method listo. Identidad permanente; sin limite de inactividad. Escanea el QR una vez y autoriza Android."
