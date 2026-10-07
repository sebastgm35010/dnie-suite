function New-BridgeDesktopControl([string]$service,[string]$dependency,[string]$runtime,[string]$storage) {
    [pscustomobject]@{Service=$service;Dependency=$dependency;Runtime=$runtime;Storage=$storage;Process=$null;RunDirectory=$null}
}
function ConvertTo-BridgeArgument([string]$value) {
    if ($value.Contains('"') -or $value.Contains("`n") -or $value.Contains("`r") -or $value.EndsWith('\')) { throw 'Ruta no compatible.' }
    '"' + $value + '"'
}
function Start-BridgeSelector($control) {
    if ($control.Process -and -not $control.Process.HasExited) { return }
    if (-not (Test-Path $control.Runtime) -or -not (Test-Path (Join-Path $control.Dependency 'frida'))) { throw 'Falta el runtime RENIEC o Frida. Consulta tools/windows-reader-selector/README.md.' }
    $python=(Get-Command python.exe -ErrorAction Stop).Source
    if ($control.Process) { $control.Process.Dispose() }
    $control.RunDirectory=Join-Path $control.Storage ([guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $control.RunDirectory -Force | Out-Null
    $arguments='-u '+(ConvertTo-BridgeArgument $control.Service)+' --directory '+(ConvertTo-BridgeArgument $control.RunDirectory)+' --dependency-dir '+(ConvertTo-BridgeArgument $control.Dependency)+' --runtime '+(ConvertTo-BridgeArgument $control.Runtime)
    $control.Process=Start-Process $python -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $control.RunDirectory 'service-out.txt') -RedirectStandardError (Join-Path $control.RunDirectory 'service-error.txt')
}
function Stop-BridgeSelector($control) {
    if ($control.RunDirectory -and $control.Process -and -not $control.Process.HasExited) {
        [IO.File]::WriteAllText((Join-Path $control.RunDirectory 'stop.txt'),'desktop_stop',[Text.UTF8Encoding]::new($false))
    }
}
function Get-BridgeDesktopState($control) {
    $properties=[Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties()
    $listening=[bool](@($properties.GetActiveTcpListeners() | Where-Object Port -eq 39443).Count -or @($properties.GetActiveTcpConnections() | Where-Object {$_.LocalEndPoint.Port -eq 39443 -and $_.State -eq 'Established'}).Count)
    $running=[bool]($control.Process -and -not $control.Process.HasExited)
    $phase=if($running){'starting'}else{'stopped'}
    $count=0
    if ($control.RunDirectory -and (Test-Path (Join-Path $control.RunDirectory 'status.json'))) {
        $state=[IO.File]::ReadAllText((Join-Path $control.RunDirectory 'status.json'),[Text.Encoding]::UTF8) | ConvertFrom-Json
        $phase=$state.phase;$count=$state.filteredLists
        if (-not $running -and $phase -in @('starting','running','degraded')) { $phase='failed' }
    } elseif ($control.Process -and -not $running) { $phase='failed' }
    [pscustomobject]@{HostListening=$listening;SelectorRunning=$running;Phase=$phase;FilteredLists=$count}
}
function Start-BridgeDesktopHost([string]$entry,[string]$pc,[string]$phone,[bool]$stopHost) {
    foreach ($name in @($pc,$phone)) {
        if ($name -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$') { throw 'Introduce nombres Tailscale validos.' }
    }
    $arguments='-NoProfile -File '+(ConvertTo-BridgeArgument $entry)+' -HostName '+(ConvertTo-BridgeArgument $pc)+' -PeerName '+(ConvertTo-BridgeArgument $phone)
    if ($stopHost) { $arguments+=' -Stop' }
    Start-Process powershell.exe -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden | Out-Null
}
