# Explicit rollback helper; not run automatically.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Requiere administrador.' }
$record = Get-Content (Join-Path $repo 'build/pcsc-deployment/development-certificate.json') -Raw | ConvertFrom-Json
if ($record.thumbprint -notmatch '^[A-Fa-f0-9]{40}$' -or $record.subject -ne 'CN=DNIe Bridge Local Development Only') { throw 'Registro de certificado invalido.' }
foreach ($device in @(Get-PnpDevice -Class SmartCardReader)) {
    $ids = (Get-PnpDeviceProperty -InstanceId $device.InstanceId -KeyName DEVPKEY_Device_HardwareIds -ErrorAction SilentlyContinue).Data
    if ($ids -notcontains 'Root\DNIeBridgeReader') { continue }
    $inf = (Get-PnpDeviceProperty -InstanceId $device.InstanceId -KeyName DEVPKEY_Device_DriverInfPath).Data
    if ($inf -notmatch '^oem\d+\.inf$') { throw 'Nombre de paquete inesperado.' }
    & pnputil.exe /remove-device $device.InstanceId
    if ($LASTEXITCODE -notin @(0,3010)) { throw 'No se pudo retirar el dispositivo propio.' }
    & pnputil.exe /delete-driver $inf
    if ($LASTEXITCODE -notin @(0,3010)) { throw 'No se pudo retirar el paquete actual.' }
}
foreach ($store in @('Root','TrustedPublisher','My')) {
    $path = "Cert:\LocalMachine\$store\$($record.thumbprint)"
    $cert = Get-Item -LiteralPath $path -ErrorAction SilentlyContinue
    if ($cert) {
        if ($cert.Subject -ne $record.subject) { throw 'Certificado no corresponde al proyecto.' }
        if ($store -eq 'My') { Remove-Item -LiteralPath $path -DeleteKey }
        else { Remove-Item -LiteralPath $path }
    }
}
Write-Output 'Dispositivo, paquete actual y confianza de pruebas retirados. No se cambio Bix ni la politica de arranque.'
