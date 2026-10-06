param([Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$HostLog)
$ErrorActionPreference='Stop'
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if((Split-Path $OutputDirectory -Parent) -ne 'D:\DNIeToolchain\LiveCaptures' -or (Split-Path $OutputDirectory -Leaf) -notmatch '^tailnet-reader-selector-[a-f0-9]{32}$'){throw 'Invalid output directory'}
$runtime='C:\Users\LENOVO\.dcdelivery-2.0\files\customjre\bin\java.exe'
$jar='C:\Users\LENOVO\.dcdelivery-2.0\files\dcdeliverydnie3.jar'
$classes='D:\DNIeToolchain\ReaderSelector'
$process=$null
$receipt=@{mode='official_java_process_local_reader_filter';reader='DNIe Bridge Android NFC Private Reader 0';networkNotification=$false}
try {
 if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne 'B46DD7FA9ABCC26E047F75E598FE7B5A34BE4AC44E7111E45F34698894CC67FD'){throw 'Unexpected official reader JAR'}
 # Reuse the last CAN entered in the official middleware, in memory only.
 $log=Get-Content -LiteralPath 'C:\Users\LENOVO\.dcdelivery-2.0\dcdelivery-rdr.0.log' -Raw -Encoding UTF8
 $canEntries=[regex]::Matches($log,'AppArguments\(numeroCan=([0-9]{6})\)')
 if(!$canEntries.Count){throw 'No previously entered CAN available'}
 $can=$canEntries[$canEntries.Count-1].Groups[1].Value
 $log=$null;$canEntries=$null
 $offset=(Get-Item -LiteralPath $HostLog).Length
 $until=[DateTimeOffset]::UtcNow.AddHours(2)
 Set-Content -LiteralPath (Join-Path $OutputDirectory 'selector-armed.txt') -Value 'Waiting for next card-present event; no global reader changes' -Encoding UTF8
 $ready=$false;$tail=''
 while([DateTimeOffset]::UtcNow -lt $until -and !(Test-Path -LiteralPath (Join-Path $OutputDirectory 'cancel.txt'))){
  $stream=[IO.File]::Open($HostLog,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
  try {
   if($stream.Length -lt $offset){$offset=0}
   $null=$stream.Seek($offset,[IO.SeekOrigin]::Begin)
   $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
   $new=$tail+$reader.ReadToEnd();$offset=$stream.Position;$tail=$new.Substring([Math]::Max(0,$new.Length-512))
   if($new -match 'bridge_phase=pcsc_card_present'){$ready=$true;break}
  } finally {$stream.Dispose()}
  Start-Sleep -Milliseconds 100
 }
 if(!$ready){$receipt.status='no_new_card';return}
 $info=[Diagnostics.ProcessStartInfo]::new()
 $info.FileName=$runtime
 $info.Arguments='-client -cp "'+$classes+';'+$jar+'" BridgeReaderLauncher --read'
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $process=[Diagnostics.Process]::Start($info)
 $receipt.pid=$process.Id
 $process.StandardInput.WriteLine($can);$process.StandardInput.Close();$can=$null
 $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
 if(!$process.WaitForExit(360000)){$process.Kill();$process.WaitForExit();$receipt.status='process_timeout'}
 else {$receipt.status='process_completed'}
 $text=$stdout.GetAwaiter().GetResult();$events=$stderr.GetAwaiter().GetResult()
 # Official output may contain identity/certificates: private D: artifact only.
 [IO.File]::WriteAllText((Join-Path $OutputDirectory 'official-result-private.txt'),$text,[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText((Join-Path $OutputDirectory 'selector-events.jsonl'),$events,[Text.UTF8Encoding]::new($false))
 $receipt.exitCode=$process.ExitCode
 $receipt.officialSuccess=$text.StartsWith('success:true\')
 $receipt.officialReportedFailure=$text.StartsWith('success:false\')
 $text=$null;$events=$null
} catch {$receipt.status='failed';$receipt.failureType=$_.Exception.GetType().Name}
finally {
 $can=$null
 if($process){if(!$process.HasExited){$process.Kill()};$process.Dispose()}
 $receipt.utc=[DateTimeOffset]::UtcNow.ToString('O')
 $receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'selector-result.json') -Encoding UTF8
}
