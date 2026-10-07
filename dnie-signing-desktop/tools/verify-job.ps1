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
function Get-AsnElementEnd([byte[]] $bytes, [int] $start, [int] $depth = 0) {
    if ($depth -gt 32 -or $start -lt 0 -or $start + 2 -gt $bytes.Length) { throw 'CMS truncado o demasiado anidado' }
    $tag = $bytes[$start]; $cursor = $start + 1
    if (($tag -band 31) -eq 31) { throw 'Etiqueta ASN no admitida' }
    $length = [long]$bytes[$cursor]; $cursor++
    if ($length -eq 128) {
        if (($tag -band 32) -eq 0) { throw 'ASN indefinido primitivo' }
        while ($true) {
            if ($cursor + 2 -gt $bytes.Length) { throw 'CMS indefinido truncado' }
            if ($bytes[$cursor] -eq 0 -and $bytes[$cursor+1] -eq 0) { return $cursor+2 }
            $cursor = Get-AsnElementEnd $bytes $cursor ($depth+1)
        }
    }
    if ($length -gt 128) {
        $count = $length -band 127
        if ($count -gt 4 -or $cursor+$count -gt $bytes.Length) { throw 'Longitud ASN invalida' }
        $length=0
        for ($i=0; $i -lt $count; $i++) { $length=$length*256+$bytes[$cursor]; $cursor++ }
    }
    if ($cursor+$length -gt $bytes.Length) { throw 'CMS truncado' }
    return [int]($cursor+$length)
}
# Extractor acotado para los PDF incrementales producidos por esta suite.
# No sustituye un validador general de semantica PDF, confianza o revocacion.
function Read-SuitePdfSignature([byte[]] $pdf, [byte[]] $original) {
    if ($pdf.Length -gt 268435456 + 1048576) { throw 'PDF excede limite del verificador' }
    if ($pdf.Length -le $original.Length) { throw 'Falta revision incremental' }
    for ($i = 0; $i -lt $original.Length; $i++) {
        if ($pdf[$i] -ne $original[$i]) { throw 'El original no se conserva como prefijo' }
    }
    $text = [Text.Encoding]::GetEncoding(28591).GetString($pdf)
    $ranges = [regex]::Matches($text, '/ByteRange\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\]')
    $candidates = @($ranges | Where-Object {
        ([long]$_.Groups[3].Value + [long]$_.Groups[4].Value) -eq $pdf.Length
    })
    if ($candidates.Count -ne 1) { throw 'Revision final ambigua o sin firma' }
    $range = $candidates[0]
    $a = [long]$range.Groups[1].Value; $b = [long]$range.Groups[2].Value
    $c = [long]$range.Groups[3].Value; $d = [long]$range.Groups[4].Value
    if ($a -ne 0 -or $b -lt $original.Length -or $c -le $b + 2 -or $c -gt $pdf.Length -or $d -le 0) { throw 'ByteRange invalido' }
    if ($pdf[$b] -ne 60 -or $pdf[$c-1] -ne 62 -or $text.Substring(0,[int]$b) -notmatch '/Contents\s*$') { throw 'Hueco de firma invalido' }
    $hex = $text.Substring([int]$b + 1,[int]($c-$b-2))
    if ($hex.Length % 2 -ne 0 -or $hex -notmatch '^[0-9a-fA-F]+$') { throw 'Contents invalido' }
    $bytes = [byte[]]::new($hex.Length / 2)
    for ($i=0; $i -lt $bytes.Length; $i++) { $bytes[$i] = [Convert]::ToByte($hex.Substring($i*2,2),16) }
    if ($bytes.Length -lt 2 -or $bytes[0] -ne 48) { throw 'CMS no es secuencia ASN' }
    $total = Get-AsnElementEnd $bytes 0
    for ($i=$total; $i -lt $bytes.Length; $i++) { if ($bytes[$i] -ne 0) { throw 'Relleno CMS no nulo' } }
    $cmsBytes=[byte[]]::new([int]$total); [Array]::Copy($bytes,0,$cmsBytes,0,$total)
    $content=[byte[]]::new([int]($b+$d))
    [Array]::Copy($pdf,0,$content,0,$b); [Array]::Copy($pdf,$c,$content,$b,$d)
    return [pscustomobject]@{ Content=$content; Cms=$cmsBytes }
}
$manifest = Get-Content -LiteralPath (Join-Path $root 'result.json') -Encoding UTF8 | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) { throw 'Esquema incompatible' }
$certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new((Join-Path $root 'certificate.cer'))
$results = @()
foreach ($item in $manifest.documents | Where-Object { $_.status -eq 'SIGNED' }) {
    $original = [IO.File]::ReadAllBytes((Resolve-JobFile $item.original))
    $encoded = [IO.File]::ReadAllBytes((Resolve-JobFile $item.signature))
    $format = 'CMS_DETACHED'
    if ($item.signature.EndsWith('_firmado.pdf', [StringComparison]::OrdinalIgnoreCase)) {
        $parsed = Read-SuitePdfSignature $encoded $original
        $original = $parsed.Content; $encoded = $parsed.Cms; $format = 'PDF_INVISIBLE'
    }
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
    $results += [ordered]@{ documentId = $item.id; format = $format; integrity = $true; certificateMatches = $true; alteredContentRejected = $true }
}
$report = [ordered]@{ jobId = $manifest.jobId; backend = $manifest.backend; complete = $manifest.complete;
    verifier = '.NET SignedCms'; revocationChecked = $false; signatures = $results }
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'independent-verification.json') -Encoding UTF8
$report | ConvertTo-Json -Depth 6
if (-not $manifest.complete -or $results.Count -ne @($manifest.documents).Count) { exit 1 }
