function Get-BridgeNetworkInterfaces {
    foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($adapter.OperationalStatus -ne 'Up') { continue }
        foreach ($unicast in $adapter.GetIPProperties().UnicastAddresses) {
            $address=$unicast.Address; $b=$address.GetAddressBytes()
            if ($b.Length -eq 4 -and ($b[0] -eq 10 -or ($b[0] -eq 172 -and $b[1] -ge 16 -and $b[1] -le 31) -or ($b[0] -eq 192 -and $b[1] -eq 168))) {
                [pscustomobject]@{Address=$address.IPAddressToString; Name=$adapter.Name; Display=($adapter.Name+' - '+$address.IPAddressToString)}
            }
        }
    }
}
function Start-BridgeDesktopNetwork([string]$entry,[string]$method,[string]$address,[int]$idle,[bool]$stopHost) {
    if ($method -notin @('LAN','USB') -or $address -notmatch '^\d{1,3}(\.\d{1,3}){3}$' -or $idle -lt 5 -or $idle -gt 300) { throw 'Configuracion de red invalida.' }
    $arguments='-NoProfile -File '+(ConvertTo-BridgeArgument $entry)+' -Method '+$method+' -Address '+$address+' -IdleSeconds '+$idle
    if ($stopHost) { $arguments+=' -Stop' }
    $process=Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru
    return $process
}
function Read-BridgeDesktopPreferences([string]$path) {
    if (Test-Path -LiteralPath $path) {
        try { return ([IO.File]::ReadAllText($path,[Text.Encoding]::UTF8) | ConvertFrom-Json) } catch { }
    }
    return [pscustomobject]@{Method='Tailscale';Pc='';Phone='';Address='';Idle=120}
}
function Save-BridgeDesktopPreferences([string]$path,$settings) {
    [IO.File]::WriteAllText($path,($settings | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
