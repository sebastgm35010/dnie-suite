param([string]$AdbDevice, [switch]$NoOpen, [switch]$NoServer, [switch]$TransportOnly, [switch]$Tailscale,
    [string]$TailnetHost = 'sebas-laptop.tail2752e9.ts.net', [string]$TailnetPeer = 'sebas-poco.tail2752e9.ts.net')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$dotnet = (Get-Command dotnet.exe -ErrorAction Stop).Source
if (-not $NoServer -and -not $TransportOnly) { & (Join-Path $PSScriptRoot 'reader-status.ps1') }
$output = 'D:\DNIeToolchain\PairingUi'
& $dotnet build (Join-Path $PSScriptRoot 'WindowsPairing.csproj') -c Release -o $output --configfile (Join-Path $PSScriptRoot 'NuGet.Config') --verbosity quiet
if ($LASTEXITCODE) { throw 'No se pudo compilar el generador QR.' }
$session = Join-Path 'D:\DNIeToolchain\Pairings' ([Guid]::NewGuid().ToString('N'))
if ($Tailscale) {
    if ($TransportOnly) { throw 'Tailscale requiere el lector PC/SC.' }
    & $dotnet (Join-Path $output 'WindowsBridge.dll') setup --output $session --host $TailnetHost --peer $TailnetPeer --transport tailscale_tcp --port 39443
} else { & $dotnet (Join-Path $output 'WindowsBridge.dll') setup --output $session --host 127.0.0.1 --port 39443 }
if ($LASTEXITCODE) { throw 'No se pudo crear el emparejamiento privado.' }
$config = Join-Path $session 'server-private.json'
& $dotnet (Join-Path $output 'WindowsPairing.dll') --config $config
if ($LASTEXITCODE) { throw 'No se pudo generar el QR.' }
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if ($AdbDevice -and -not (Test-Path -LiteralPath $adb)) { throw 'No se encontro ADB para el dispositivo elegido.' }
if (-not $Tailscale -and (Test-Path -LiteralPath $adb)) {
    $devices = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^(.+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($AdbDevice -and $devices -notcontains $AdbDevice) { throw 'El dispositivo ADB elegido no esta conectado.' }
    if (-not $AdbDevice -and $devices.Count -eq 1) { $AdbDevice = $devices[0] }
    if ($AdbDevice) {
        & $adb -s $AdbDevice reverse tcp:39443 tcp:39443
        if ($LASTEXITCODE) { throw 'No se pudo preparar el tunel ADB.' }
    }
}
if ($Tailscale) { Write-Host 'Tailscale directo preparado. Activa Tailscale en el telefono; no requiere ADB reverse.' }
elseif (-not $AdbDevice) { Write-Host 'Sin telefono ADB conectado. El QR configura el cliente; para conectar, prepara ADB inalambrico y reverse tcp:39443 tcp:39443.' }
if (-not $NoServer -and -not $TransportOnly) {
    $helper = Join-Path $PSScriptRoot 'start-pcsc-session.ps1'
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        & $helper -Session $session
    } else {
        Write-Host 'Windows pide elevacion para iniciar la sesion temporal SYSTEM que requiere el controlador. No se cambia el firewall.'
        $arguments = '-NoLogo -NoProfile -File "' + $helper + '" -Session "' + $session + '"'
        $admin = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -PassThru -Wait
        if ($admin.ExitCode -ne 0) { throw 'No se pudo iniciar el backend PC/SC.' }
    }
}
if (-not $NoServer -and $TransportOnly) {
    $listener = Start-Process -FilePath $dotnet -ArgumentList @((Join-Path $output 'WindowsBridge.dll'),'serve-local','--config',$config) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $session 'host.log') -RedirectStandardError (Join-Path $session 'host-error.log')
    Start-Sleep -Milliseconds 600
    if ($listener.HasExited) { throw 'El host TLS no pudo arrancar. Revise host-error.log en la carpeta privada.' }
    @{hostPid=$listener.Id;session=$session;expiresUtc=[DateTime]::UtcNow.AddMinutes(10).ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $session 'session.json') -Encoding UTF8
    Write-Host "Host TLS aislado iniciado (PID $($listener.Id)); este modo no conecta el controlador."
}
if (-not $NoOpen) { Start-Process -FilePath (Join-Path $session 'pairing-qr.html') -WindowStyle Hidden }
Write-Host "QR privado: $(Join-Path $session 'pairing-qr.html')"
Write-Host 'Escanea desde Puente NFC · PC y compara la huella. El QR vence en diez minutos.'
