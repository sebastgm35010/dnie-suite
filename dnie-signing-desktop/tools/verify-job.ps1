param([Parameter(Mandatory = $true)][string] $JobDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security.Cryptography.Pkcs
$root = [IO.Path]::GetFullPath($JobDirectory)
$prefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
function Resolve-JobFile([string] $relative) {
    if ([IO.Path]::IsPathRooted($relative)) { throw 'El manifest debe contener rutas relativas' }
    $resolved = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Ruta fuera del trabajo' }
    return $resolved
}
$manifest = Get-Content -LiteralPath (Join-Path $root 'result.json') -Encoding UTF8 | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) { throw 'Esquema incompatible' }
$certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new((Join-Path $root 'certificate.cer'))
$results = @()
foreach ($item in $manifest.documents | Where-Object { $_.status -eq 'SIGNED' }) {
    $original = [IO.File]::ReadAllBytes((Resolve-JobFile $item.original))
    $encoded = [IO.File]::ReadAllBytes((Resolve-JobFile $item.signature))
    $cms = [Security.Cryptography.Pkcs.SignedCms]::new([Security.Cryptography.Pkcs.ContentInfo]::new($original), $true)
    $cms.Decode($encoded)
    $cms.CheckSignature($true)
    if ($cms.SignerInfos.Count -ne 1 -or $cms.SignerInfos[0].Certificate.Thumbprint -ne $certificate.Thumbprint) {
        throw 'El firmante no coincide con el certificado del trabajo'
    }
    $altered = if ($original.Length -eq 0) { [byte[]]@(1) } else { [byte[]]$original.Clone() }
    if ($original.Length -gt 0) { $altered[0] = $altered[0] -bxor 1 }
    $tampered = [Security.Cryptography.Pkcs.SignedCms]::new([Security.Cryptography.Pkcs.ContentInfo]::new([byte[]]$altered), $true)
    $tampered.Decode($encoded)
    $rejected = $false
    try { $tampered.CheckSignature($true) } catch { $rejected = $true }
    if (-not $rejected) { throw 'Contenido alterado aceptado' }
    $results += [ordered]@{ documentId = $item.id; integrity = $true; certificateMatches = $true; alteredContentRejected = $true }
}
$report = [ordered]@{ jobId = $manifest.jobId; backend = $manifest.backend; complete = $manifest.complete;
    verifier = '.NET SignedCms'; revocationChecked = $false; signatures = $results }
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'independent-verification.json') -Encoding UTF8
$report | ConvertTo-Json -Depth 6
if (-not $manifest.complete -or $results.Count -ne @($manifest.documents).Count) { exit 1 }
