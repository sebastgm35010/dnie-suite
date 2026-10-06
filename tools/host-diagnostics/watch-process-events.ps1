param([Parameter(Mandatory)][string]$OutputDirectory, [ValidateRange(1,3600)][int]$Seconds=1800)
$ErrorActionPreference='Stop'
$output=Join-Path $OutputDirectory 'process-events.jsonl'
$stop=Join-Path $OutputDirectory 'stop.txt'
$stream=[IO.File]::Open($output,[IO.FileMode]::Append,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
$writer=New-Object IO.StreamWriter($stream,(New-Object Text.UTF8Encoding($false)))
$writer.AutoFlush=$true
function Write-Record($eventName,$fields) {
    $record=@{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();category='process';event=$eventName;fields=$fields} | ConvertTo-Json -Compress
    $writer.WriteLine($record)
}
$prefix='DnieCapture-'+[Guid]::NewGuid().ToString('N')
try {
    Register-WmiEvent -Query 'SELECT * FROM Win32_ProcessStartTrace' -SourceIdentifier ($prefix+'-start') | Out-Null
    Register-WmiEvent -Query 'SELECT * FROM Win32_ProcessStopTrace' -SourceIdentifier ($prefix+'-stop') | Out-Null
    Write-Record 'capture_started' @{limitSeconds=$Seconds;mode='system_process_metadata_only'}
    $until=[DateTimeOffset]::UtcNow.AddSeconds($Seconds)
    while([DateTimeOffset]::UtcNow -lt $until -and !(Test-Path -LiteralPath $stop)) {
        $null=Wait-Event -Timeout 1
        foreach($item in @(Get-Event | Where-Object {$_.SourceIdentifier -like "$prefix-*"})) {
            $data=$item.SourceEventArgs.NewEvent
            $fields=@{name=[string]$data.ProcessName;pid=[int]$data.ProcessID;eventTimeUtc=[DateTime]::FromFileTimeUtc([long]$data.TIME_CREATED).ToString('O')}
            if($item.SourceIdentifier -eq ($prefix+'-start')) {$fields.parentPid=[int]$data.ParentProcessID; $kind='started'}
            else {$fields.exitStatus=[long]$data.ExitStatus; $kind='stopped'}
            Write-Record $kind $fields
            Remove-Event -EventIdentifier $item.EventIdentifier -ErrorAction SilentlyContinue
        }
        if((Get-Item -LiteralPath $output).Length -gt 20MB) {Write-Record 'size_limit' @{};break}
    }
} finally {
    Unregister-Event -SourceIdentifier ($prefix+'-start') -ErrorAction SilentlyContinue
    Unregister-Event -SourceIdentifier ($prefix+'-stop') -ErrorAction SilentlyContinue
    Write-Record 'capture_stopped' @{}
    $writer.Dispose()
}
