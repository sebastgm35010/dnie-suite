param([Parameter(Mandatory)][string]$PairingDirectory,[Parameter(Mandatory)][string]$OutputDirectory,[switch]$PublicReads,[switch]$SmallBlocks)
$ErrorActionPreference='Stop'
$pipe=$null
$logName=if($SmallBlocks){'private-small-blocks.jsonl'}elseif($PublicReads -or $SmallBlocks){'private-public-reads.jsonl'}else{'private-idle.jsonl'}
function Event($name,$fields){@{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();event=$name;fields=$fields} | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $OutputDirectory $logName)}
try {
 $deadline=[DateTime]::UtcNow.AddMinutes(10)
 $name=$null
 while([DateTime]::UtcNow -lt $deadline -and !$name){
  $text=Get-Content -LiteralPath "$PairingDirectory\host.log" -Raw -ErrorAction SilentlyContinue
  if($text -match 'Session-local provider endpoint: (DNIeBridge-[a-f0-9]{32})'){$name=$Matches[1];break}
  Start-Sleep -Milliseconds 250
 }
 if(!$name){throw 'Private endpoint unavailable'}
 $pipe=[IO.Pipes.NamedPipeClientStream]::new('.',$name,[IO.Pipes.PipeDirection]::InOut,[IO.Pipes.PipeOptions]::Asynchronous)
 $pipe.Connect(600000)
 Event 'private_connected' @{pcscAttached=$false}
 function Read-Exact($count){
  $bytes=New-Object byte[] $count;$offset=0
  while($offset -lt $count){
   $task=$pipe.ReadAsync($bytes,$offset,$count-$offset)
   if(!$task.Wait(10000)){throw 'Response deadline exceeded'}
   $read=$task.Result;if(!$read){throw 'Bridge EOF'};$offset+=$read
  }
  return ,$bytes
 }
 function Exchange($command){
  $pipe.Write([byte[]]@(($command.Length -shr 8),($command.Length -band 255)),0,2)
  $pipe.Write($command,0,$command.Length);$pipe.Flush()
  $header=Read-Exact 2;$length=([int]$header[0]*256)+$header[1]
  if($length -lt 1 -or $length -gt 4096){throw 'Invalid response length'}
  return ,(Read-Exact $length)
 }
 $reference=$null
 $cycles=if($SmallBlocks){3}else{19}
 for($i=0;$i -lt $cycles;$i++){
  if(Test-Path -LiteralPath "$OutputDirectory\stop.txt"){Event 'test_cancelled' @{};return}
  if($PublicReads -or $SmallBlocks){
   # Only public EF.CardAccess. No PC PACE, protected CLA, PIN or signature command.
   $probes=@(
    @{stage='select_ias';hex='00A4040C10A000000077030C60000000FE00000500'},
    @{stage='select_card_access';hex='00A4020C02011C'}
   )
   if($SmallBlocks){
    # 107-byte public file size measured in this DNI's previous SELECT FCI.
    for($offset=0;$offset -lt 107;$offset+=16){
     $le=[Math]::Min(16,107-$offset)
     $probes+=@{stage='read_card_access_block';hex=('00B0{0:X4}{1:X2}' -f $offset,$le);offset=$offset;le=$le}
    }
   }else{$probes+=@{stage='read_card_access';hex='00B0000000'}}
   foreach($probe in $probes){
    $command=New-Object byte[] ($probe.hex.Length/2)
    for($j=0;$j -lt $command.Length;$j++){$command[$j]=[Convert]::ToByte($probe.hex.Substring($j*2,2),16)}
    Event 'public_apdu_started' @{iteration=$i;stage=$probe.stage;offset=$probe.offset;le=$probe.le}
    $clock=[Diagnostics.Stopwatch]::StartNew();$response=Exchange $command;$clock.Stop()
    if($response.Length -lt 2){throw 'Truncated APDU response'}
    $status='{0:X2}{1:X2}' -f $response[-2],$response[-1]
    Event 'public_apdu_returned' @{iteration=$i;stage=$probe.stage;status=$status;responseBytes=$response.Length;durationMs=$clock.ElapsedMilliseconds;offset=$probe.offset;le=$probe.le}
    if($status -ne '9000'){[Array]::Clear($response,0,$response.Length);throw 'Public read rejected'}
    if($probe.stage -eq 'read_card_access_block' -and $response.Length -ne ($probe.le+2)){throw 'Unexpected block length'}
    if($probe.stage -eq 'read_card_access'){
     if($response.Length -le 2){throw 'Empty public file'}
     if($null -eq $reference){$reference=$response.Clone()}
     else {
      $same=$response.Length -eq $reference.Length
      if($same){for($j=0;$j -lt $response.Length;$j++){if($response[$j] -ne $reference[$j]){$same=$false;break}}}
      if(!$same){throw 'Public file changed between reads'}
     }
    }
    [Array]::Clear($response,0,$response.Length)
   }
   Event 'read_cycle_ok' @{iteration=$i}
   if($i -lt ($cycles-1)){Start-Sleep -Seconds 5}
   continue
  }
  # Only ATR metadata once and presence checks. No application APDU or PC PACE.
  $control=if($i -eq 0){4}else{5}
  $pipe.Write([byte[]]@(0,1,$control),0,3);$pipe.Flush()
  $header=Read-Exact 2;$length=([int]$header[0]*256)+$header[1]
  if($length -lt 1 -or $length -gt 33){throw 'Invalid control response length'}
  $response=Read-Exact $length
  if($control -eq 5 -and ($length -ne 1 -or $response[0] -ne 1)){throw 'Presence check failed'}
  Event 'control_ok' @{iteration=$i;control=$control;responseBytes=$length}
  if($i -lt ($cycles-1)){Start-Sleep -Seconds 5}
 }
 Event 'test_completed' @{expectedClose=$true}
} catch {Event 'test_failed' @{type=$_.Exception.GetType().Name};throw}
finally {if($reference){[Array]::Clear($reference,0,$reference.Length)};if($pipe){$pipe.Dispose()}}
