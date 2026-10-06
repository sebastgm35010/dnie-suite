# Explicitly authorized local development signing; never changes boot policy.
$ErrorActionPreference = 'Stop'
Import-Module PKI
. (Join-Path $PSScriptRoot 'package-utils.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$record = Join-Path $repo 'build/pcsc-deployment/test-signing-status.json'
function Record([string]$stage, [string]$detail) {
    @{stage=$stage;detail=$detail;updatedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath $record -Encoding UTF8
}
Start-Transcript -Path (Join-Path $repo 'build/pcsc-deployment/test-signing.log') -Append | Out-Null
try {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Requiere administrador.' }
    $package = 'D:\DNIeToolchain\ReaderTestPackage'
    New-Item -ItemType Directory -Path $package -Force | Out-Null
    & icacls.exe $package /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo proteger el paquete.' }
    foreach ($name in @('DniBridgeReader.dll','DniBridgeReader.inf')) {
        Copy-Item -LiteralPath (Join-Path 'D:\DNIeToolchain\ReaderBuild\DniBridgeReader' $name) -Destination $package
    }
    $kit = 'D:\DNIeToolchain\WindowsKits\10'
    $sign = Join-Path $kit 'bin/10.0.26100.0/x64/signtool.exe'
    $inf2cat = Join-Path $kit 'bin/10.0.26100.0/x86/inf2cat.exe'
    foreach ($tool in @($sign)) {
        $signature = Get-AuthenticodeSignature -LiteralPath $tool
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'Herramienta Microsoft no validada.' }
    }
    # Inf2Cat's embedded Microsoft internal-build certificate is not a public root.
    # Verify provenance against the author-signed official WDK NuGet instead.
    $nupkg = 'D:\DNIeToolchain\NuGet\wdk-x64-26100.6584.nupkg'
    & dotnet.exe nuget verify $nupkg --all
    if ($LASTEXITCODE -ne 0) { throw 'Firma del paquete oficial WDK no validada.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($nupkg)
    try {
        $entry = $archive.GetEntry('c/bin/10.0.26100.0/x86/Inf2Cat.exe')
        if (-not $entry) { throw 'Falta Inf2Cat en el paquete oficial.' }
        $stream = $entry.Open(); $sha = [Security.Cryptography.SHA256]::Create()
        try { $expected = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
        finally { $sha.Dispose(); $stream.Dispose() }
        if ((Get-PackageFileHash $inf2cat) -ne $expected) { throw 'Inf2Cat no coincide con el WDK oficial firmado.' }
    } finally { $archive.Dispose() }
    Record 'signing' 'Creando certificado exclusivo de desarrollo (90 dias), clave no exportable.'
    $certificateRecord = Join-Path $repo 'build/pcsc-deployment/development-certificate.json'
    if (Test-Path -LiteralPath $certificateRecord) {
        $metadata = Get-Content -LiteralPath $certificateRecord -Raw | ConvertFrom-Json
        $cert = Get-Item -LiteralPath ("Cert:\LocalMachine\My\" + $metadata.thumbprint)
        if ($cert.Subject -ne 'CN=DNIe Bridge Local Development Only' -or -not $cert.HasPrivateKey) { throw 'Certificado previo no corresponde al proyecto.' }
    } else {
        $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject 'CN=DNIe Bridge Local Development Only' -CertStoreLocation Cert:\LocalMachine\My -KeyAlgorithm RSA -KeyLength 3072 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddDays(90)
    }
    $public = Join-Path $package 'development-signing.cer'
    Export-Certificate -Cert $cert -FilePath $public | Out-Null
    # Local trust is explicitly authorized for this test certificate; no other CA imported.
    Import-Certificate -FilePath $public -CertStoreLocation Cert:\LocalMachine\Root | Out-Null
    Import-Certificate -FilePath $public -CertStoreLocation Cert:\LocalMachine\TrustedPublisher | Out-Null
    @{thumbprint=$cert.Thumbprint;subject=$cert.Subject;expiresUtc=$cert.NotAfter.ToUniversalTime().ToString('O');package=$package;stores=@('LocalMachine/My','LocalMachine/Root','LocalMachine/TrustedPublisher')} | ConvertTo-Json | Set-Content (Join-Path $repo 'build/pcsc-deployment/development-certificate.json') -Encoding UTF8
    & $sign sign /sm /s My /sha1 $cert.Thumbprint /fd SHA256 (Join-Path $package 'DniBridgeReader.dll')
    if ($LASTEXITCODE -ne 0) { throw 'Firma DLL fallo.' }
    & $inf2cat "/driver:$package" /os:10_X64
    if ($LASTEXITCODE -ne 0) { throw 'Generacion catalogo fallo.' }
    & $sign sign /sm /s My /sha1 $cert.Thumbprint /fd SHA256 (Join-Path $package 'dnibridgereader.cat')
    if ($LASTEXITCODE -ne 0) { throw 'Firma catalogo fallo.' }
    Record 'installing' 'Paquete firmado. Validando confianza, INF y catalogo antes de crear dispositivo.'
    & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'deploy.ps1') -Mode Instalar -PackageDirectory $package
    if ($LASTEXITCODE -ne 0) { throw 'Instalacion no confirmada; revisar registro PnP.' }
    Record 'installed' 'Paquete instalado. Pendiente prueba sintetica PC/SC. Sin cambios de Secure Boot ni modo de pruebas.'
} catch { Record 'failed' $_.Exception.Message; Write-Host $_.Exception.Message; Stop-Transcript | Out-Null; exit 1 }
Stop-Transcript | Out-Null
