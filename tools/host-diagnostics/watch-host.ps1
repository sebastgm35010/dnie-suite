param([Parameter(Mandatory)][string]$OutputDirectory, [int]$Seconds = 1800)
$ErrorActionPreference = 'Stop'
if ($Seconds -lt 1 -or $Seconds -gt 3600) { throw 'Invalid capture duration' }
$output = Join-Path $OutputDirectory 'host.jsonl'
$stop = Join-Path $OutputDirectory 'stop.txt'
$until = (Get-Date).AddSeconds($Seconds)
$last = ''
function Write-Event($eventName, $fields) {
    @{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();category='host';event=$eventName;fields=$fields} |
        ConvertTo-Json -Depth 6 -Compress | Add-Content -LiteralPath $output -Encoding utf8
}
Write-Event 'capture_started' @{limitSeconds=$Seconds;mode='metadata_only'}
while ((Get-Date) -lt $until -and !(Test-Path -LiteralPath $stop)) {
    $cardServices = @(Get-CimInstance Win32_Service -Filter "Name='SCardSvr' OR Name='CertPropSvc'" -ErrorAction SilentlyContinue)
    $cardServicePids = @($cardServices | Where-Object { $_.ProcessId -gt 0 } | ForEach-Object { $_.ProcessId })
    $processes = @()
    foreach ($p in @(Get-Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Id -in $cardServicePids -or $_.ProcessName -match '^(idplug.*|cddnie.*|dfsvc|rundll32|msiexec|ReFirma.*|FirmaPeru.*|java|javaw|dotnet|WUDFHost)$'
    })) {
        $modules = @()
        try { $modules = @($p.Modules | Where-Object {$_.ModuleName -match '(winscard|pkcs11|idplug|PeruMinidriver|eidapi|mocapi|crypt32|ncrypt|DniBridgeReader|j2pcsc)'} |
            Select-Object -ExpandProperty ModuleName | Sort-Object -Unique) } catch { }
        $processes += @{pid=$p.Id;name=$p.ProcessName;modules=$modules}
    }
    $pids = @($processes | ForEach-Object {$_.pid})
    $sockets = @()
    try {
        if ($pids.Count -gt 0) {
            $sockets = @(Get-NetTCPConnection -ErrorAction SilentlyContinue | Where-Object {$_.OwningProcess -in $pids} |
                ForEach-Object { @{pid=$_.OwningProcess;state=[string]$_.State;localPort=$_.LocalPort;
                    remotePort=$_.RemotePort;localOnly=($_.LocalAddress -in @('127.0.0.1','::1'))} })
        }
    } catch { }
    $services = @()
    foreach ($name in @('SCardSvr','CertPropSvc')) {
        $s = Get-Service -Name $name -ErrorAction SilentlyContinue
        if ($s) { $services += @{name=$name;status=[string]$s.Status} }
    }
    $readers = @(& (Join-Path $PSScriptRoot 'pcsc-presence.ps1'))
    $fields = @{processes=$processes;services=$services;sockets=$sockets;readers=$readers}
    $current = $fields | ConvertTo-Json -Depth 6 -Compress
    if ($current -ne $last) { Write-Event 'snapshot' $fields; $last = $current }
    Start-Sleep -Seconds 2
}
Write-Event 'capture_stopped' @{}
