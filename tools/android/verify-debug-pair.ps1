param(
    [Parameter(Mandatory=$true)][string] $AppApk,
    [Parameter(Mandatory=$true)][string] $TestApk,
    [string] $PreviousApk,
    [string] $SdkRoot = $env:ANDROID_HOME,
    [string] $BuildToolsVersion = '34.0.0'
)
$ErrorActionPreference = 'Stop'
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$signer = Join-Path $SdkRoot ('build-tools/' + $BuildToolsVersion + '/apksigner.bat')
$aapt = Join-Path $SdkRoot ('build-tools/' + $BuildToolsVersion + '/aapt.exe')
function Read-ApkIdentity([string] $Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).ProviderPath
    $verification = & $signer verify --print-certs $resolved
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $digests = @($verification | Select-String '^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$' | ForEach-Object { $_.Matches[0].Groups[1].Value.ToLowerInvariant() })
    if ($digests.Count -ne 1) { throw 'Expected exactly one signing certificate' }
    $manifest = & $aapt dump badging $resolved
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK manifest' }
    $packageLine = @($manifest | Select-String "^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'")
    if ($packageLine.Count -ne 1) { throw 'Cannot identify APK package/version' }
    $groups = $packageLine[0].Matches[0].Groups
    [pscustomobject]@{ Package=$groups[1].Value; VersionCode=$groups[2].Value; VersionName=$groups[3].Value; CertificateSha256=$digests[0] }
}
$appIdentity = Read-ApkIdentity $AppApk
$testIdentity = Read-ApkIdentity $TestApk
if ($appIdentity.Package -ne 'pe.gob.reniec.dnie.evolution.debug') { throw 'Expected the evolution debug application' }
if ($testIdentity.Package -ne ($appIdentity.Package + '.test')) { throw 'Unexpected instrumentation package' }
if ($appIdentity.CertificateSha256 -ne $testIdentity.CertificateSha256) { throw 'Application and test APK signing certificates differ; do not install' }
if ($PreviousApk) {
    $previousIdentity = Read-ApkIdentity $PreviousApk
    if ($appIdentity.Package -ne $previousIdentity.Package -or $appIdentity.CertificateSha256 -ne $previousIdentity.CertificateSha256) {
        throw 'Update identity differs from previous APK; do not uninstall user data to work around it'
    }
    if ([long]$appIdentity.VersionCode -le [long]$previousIdentity.VersionCode) { throw 'A new delivery must increment versionCode' }
}
[pscustomobject]@{ Verified=$true; SameSigner=$true; App=$appIdentity; Test=$testIdentity; PreviousUpdateCompatible=[bool]$PreviousApk } | ConvertTo-Json -Depth 4
