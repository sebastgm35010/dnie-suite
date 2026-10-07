param([Parameter(Mandatory)][string]$PairingFile, [switch]$NoOpen)
$ErrorActionPreference='Stop'
# Reuse the already installed QRCoder; no bridge/service operation or new package installation.
$installed=Get-Content -LiteralPath 'D:/DNIeBridgeApp/installed.json' -Encoding utf8 | ConvertFrom-Json
$assembly=Join-Path $installed.directory 'QRCoder.dll'
if(-not (Test-Path -LiteralPath $assembly)){throw 'QRCoder no disponible; importa el TXT en Android.'}
[Reflection.Assembly]::LoadFrom($assembly) | Out-Null
$payload=Get-Content -LiteralPath $PairingFile -Encoding utf8 -Raw
if(-not $payload.StartsWith('DNISUITE1|') -or $payload.Length -gt 1024){throw 'Solicitud inválida'}
$qr=[QRCoder.QRCodeGenerator]::GenerateQrCode($payload.Trim(),[QRCoder.QRCodeGenerator+ECCLevel]::M)
$renderer=[QRCoder.PngByteQRCode]::new($qr)
try {
    $destination=[IO.Path]::ChangeExtension([IO.Path]::GetFullPath($PairingFile),'.png')
    $empty=[IO.File]::Open($destination,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $empty.Dispose()
    Set-Acl -LiteralPath $destination -AclObject (Get-Acl -LiteralPath $PairingFile)
    [IO.File]::WriteAllBytes($destination,$renderer.GetGraphic(6))
    Write-Output $destination
    # QR contains a short-lived bearer secret: open locally, do not publish/share to third parties.
    if(-not $NoOpen){Start-Process -FilePath $destination}
}finally{$renderer.Dispose();$qr.Dispose();$payload=$null}
