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
function Get-BridgeUsbDevices {
    $runtime=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../runtime'))
    if(-not(Test-Path (Join-Path $runtime 'WindowsBridge.dll'))){$runtime='D:\DNIeToolchain\PairingUi'}
    $lines=& dotnet.exe (Join-Path $runtime 'WindowsBridge.dll') usb-devices 2>&1
    if($LASTEXITCODE){throw 'No se pudo listar USB. Instala UsbDk x64.'}
    foreach($line in $lines){if([string]$line -match '^([0-9A-F]{4}:[0-9A-F]{4}) (.+)$'){[pscustomobject]@{Address='127.0.0.1';Serial=$Matches[2];Display=$Matches[1]+' - '+$Matches[2]}}}
}
function Start-BridgeDesktopNetwork([string]$entry,[string]$method,[string]$address,[string]$phone,[bool]$stopHost) {
    if ($method -notin @('LAN','USB') -or $address -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw 'Configuracion de transporte invalida.' }
    $arguments='-NoProfile -File '+(ConvertTo-BridgeArgument $entry)+' -Method '+$method+' -Address '+$address
    if ($stopHost) { $arguments+=' -Stop' } else {$arguments+=' -Phone '+(ConvertTo-BridgeArgument $phone)}
    $log=Join-Path 'D:\DNIeToolchain\Desktop' ('transport-'+[guid]::NewGuid().ToString('N')+'.txt')
    $process=Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardError $log
    $process|Add-Member -NotePropertyName ErrorLog -NotePropertyValue $log
    return $process
}
function Read-BridgeDesktopPreferences([string]$path) {
    if (Test-Path -LiteralPath $path) {
        try { return ([IO.File]::ReadAllText($path,[Text.Encoding]::UTF8) | ConvertFrom-Json) } catch { }
    }
    return [pscustomobject]@{Method='Tailscale';Pc='';Phone='';Address=''}
}
function Save-BridgeDesktopPreferences([string]$path,$settings) {
    [IO.File]::WriteAllText($path,($settings | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
