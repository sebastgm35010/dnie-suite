param([Parameter(Mandatory)][string]$OutputDirectory,[ValidateRange(15,300)][int]$Seconds=300)
$ErrorActionPreference='Stop'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
if(-not ([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Administrative ETW capture required'}
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if((Split-Path $OutputDirectory -Parent) -ne 'D:\DNIeToolchain\LiveCaptures' -or (Split-Path $OutputDirectory -Leaf) -notmatch '^[a-f0-9]{32}$'){throw 'Invalid capture directory'}
if((Get-Item -LiteralPath $OutputDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'No linked capture directory'}
$prefix='DNIeAttribution-'+[Guid]::NewGuid().ToString('N')
$sessions=@()
$watcher=$null
$started=[DateTimeOffset]::UtcNow
function Record($name,$fields){@{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();event=$name;fields=$fields} | ConvertTo-Json -Depth 5 -Compress | Add-Content -LiteralPath "$OutputDirectory\attribution.jsonl" -Encoding UTF8}
function Start-Trace($suffix,$providers,$size){
 $name="$prefix-$suffix"
 $providerFile=Join-Path $OutputDirectory "$suffix-providers.txt"
 $providers | Set-Content -LiteralPath $providerFile -Encoding ASCII
 $output=& logman.exe create trace $name -pf $providerFile -o "$OutputDirectory\$suffix.etl" -f bincirc -max $size -bs 64 -nb 8 32 -ft 1 -ets 2>&1
 $code=$LASTEXITCODE
 Record 'trace_start' @{name=$name;code=$code;sizeLimitMB=$size}
 if($code){throw "ETW start failed: $suffix"}
 $script:sessions+= $name
}
try {
 Start-Trace 'system' @(
  '{22FB2CD6-0E7B-422B-A0C7-2FAD1FD0E716} 0x50 5',
  '{4FCBF664-A33A-4652-B436-9D558983D955} 0xffffffffffffffff 5',
  '{AAEAC398-3028-487C-9586-44EACAD03637} 0xffffffffffffffff 5',
  '{09AC07B9-6AC9-43BC-A50F-58419A797C69} 0xffffffffffffffff 5',
  '{2E35AAEB-857F-4BEB-A418-2E6C0E54D988} 0xffffffffffffffff 5',
  '{5BBCA4A8-B209-48DC-A8C7-B23D3E5216FB} 0xffffffffffffffff 5'
 ) 32
 # Microsoft documented WPP GUIDs. Symbols/TMF may be needed to decode payloads.
 Start-Trace 'pcsc-wpp' @(
  '{13038e47-ffec-425d-bc69-5707708075fe} 0xffff 5',
  '{3fce7c5f-fb3b-4bce-a9d8-55cc0ce1cf01} 0xffff 5',
  '{133a980d-035d-4e2d-b250-94577ad8fced} 0x7 5',
  '{fb36caf4-582b-4604-8841-9263574c4f2c} 0x7 5',
  '{30eae751-411f-414c-988b-a8bfa8913f49} 0xffff 5',
  '{eed7f3c9-62ba-400e-a001-658869df9a91} 0xffff 5'
 ) 64
 Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,Name,ExecutablePath | ConvertTo-Json | Set-Content -LiteralPath "$OutputDirectory\process-baseline.json" -Encoding UTF8
 Get-CimInstance Win32_Service | Select-Object Name,State,ProcessId | ConvertTo-Json | Set-Content -LiteralPath "$OutputDirectory\service-baseline.json" -Encoding UTF8
 $watcher=Start-Process powershell.exe -ArgumentList ('-NoProfile -File "'+$PSScriptRoot+'\watch-process-events.ps1" -OutputDirectory "'+$OutputDirectory+'" -Seconds '+$Seconds) -WindowStyle Hidden -PassThru -RedirectStandardError "$OutputDirectory\process-error.txt" -RedirectStandardOutput "$OutputDirectory\process-out.txt"
 $seen=@{}
 Record 'ready' @{seconds=$Seconds;sessions=$sessions;processWatcherPid=$watcher.Id}
 Set-Content -LiteralPath "$OutputDirectory\attribution-ready.txt" $started.ToString('O') -Encoding UTF8
 while([DateTimeOffset]::UtcNow -lt $started.AddSeconds($Seconds) -and !(Test-Path -LiteralPath "$OutputDirectory\stop.txt")){
  foreach($process in @(Get-Process -ErrorAction SilentlyContinue)){
   try {
    $modules=@($process.Modules | Where-Object ModuleName -match '(winscard|idplug|PeruMini|pkcs11|basecsp|scksp|msclmd|certprop|scardsvr|DniBridgeReader)' | Select-Object ModuleName,FileName)
    if(!$modules.Count){continue}
    $key="$($process.Id)|$($modules.ModuleName -join ',')"
    if(!$seen.ContainsKey($key)){$seen[$key]=$true;Record 'smartcard_modules' @{pid=$process.Id;name=$process.ProcessName;modules=$modules}}
   } catch { }
  }
  Start-Sleep -Milliseconds 1500
 }
} catch {Record 'capture_failed' @{type=$_.Exception.GetType().Name};throw}
finally {
 Set-Content -LiteralPath "$OutputDirectory\stop.txt" 'attribution_finished' -Encoding UTF8
 foreach($session in $sessions){$null=& logman.exe stop $session -ets 2>&1;Record 'trace_stop' @{name=$session;code=$LASTEXITCODE}}
 if($watcher -and !$watcher.HasExited){if(!$watcher.WaitForExit(5000)){Stop-Process -Id $watcher.Id -ErrorAction SilentlyContinue}}
 Record 'finished' @{startUtc=$started.ToString('O');endUtc=[DateTimeOffset]::UtcNow.ToString('O')}
}
