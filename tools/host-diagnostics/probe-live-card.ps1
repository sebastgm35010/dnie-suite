param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class DniLiveProbe {
 [StructLayout(LayoutKind.Sequential)] public struct Pci {public uint Protocol, Length;}
 [DllImport("winscard.dll")] public static extern uint SCardEstablishContext(uint scope,IntPtr a,IntPtr b,out IntPtr ctx);
 [DllImport("winscard.dll")] public static extern uint SCardReleaseContext(IntPtr ctx);
 [DllImport("winscard.dll",CharSet=CharSet.Unicode)] public static extern uint SCardConnectW(IntPtr ctx,string reader,uint share,uint protocols,out IntPtr card,out uint protocol);
 [DllImport("winscard.dll")] public static extern uint SCardReconnect(IntPtr card,uint share,uint protocols,uint initialization,out uint protocol);
 [DllImport("winscard.dll")] public static extern uint SCardBeginTransaction(IntPtr card);
 [DllImport("winscard.dll")] public static extern uint SCardEndTransaction(IntPtr card,uint disposition);
 [DllImport("winscard.dll")] public static extern uint SCardDisconnect(IntPtr card,uint disposition);
 [DllImport("winscard.dll")] public static extern uint SCardTransmit(IntPtr card,ref Pci pci,byte[] command,uint length,IntPtr receivePci,byte[] response,ref uint responseLength);
}
'@
$output=Join-Path $OutputDirectory 'native-probe.jsonl'
function Record($eventName,$fields) {
    $row=@{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();category='native_live_probe';event=$eventName;fields=$fields}
    $json=$row | ConvertTo-Json -Compress
    Add-Content -LiteralPath $output -Value $json -Encoding utf8
    Write-Output $json
}
function Check([uint32]$code,$operation) {if($code -ne 0){Record 'pcsc_error' @{operation=$operation;code=('0x{0:X8}' -f $code)};throw "PCSC $operation failed"}}
$reader='DNIe Bridge Android NFC Private Reader 0'
$ctx=[IntPtr]::Zero; $card=[IntPtr]::Zero; $protocol=[uint32]0; $transaction=$false
try {
    Check ([DniLiveProbe]::SCardEstablishContext(2,[IntPtr]::Zero,[IntPtr]::Zero,[ref]$ctx)) 'context'
    Check ([DniLiveProbe]::SCardConnectW($ctx,$reader,2,2,[ref]$card,[ref]$protocol)) 'connect'
    Record 'connected' @{reader=$reader;protocol=$protocol}
    $timer=[Diagnostics.Stopwatch]::StartNew()
    Check ([DniLiveProbe]::SCardReconnect($card,2,2,1,[ref]$protocol)) 'warm_reset'
    Record 'warm_reset_completed' @{durationMs=$timer.ElapsedMilliseconds;protocol=$protocol}
    Check ([DniLiveProbe]::SCardBeginTransaction($card)) 'begin_transaction'; $transaction=$true
    foreach($probe in @(
        @{label='ias_v3';hex='00A4040C10A000000077030C60000000FE00000500'},
        @{label='ias_java_p2_04';hex='00A4040410A000000077030C60000000FE0000050000'},
        @{label='pki_generic';hex='00A4040C0EE828BD080FD25047656E65726963'},
        @{label='certificate_ce81';hex='00A4020C02CE81'},
        @{label='certificate_read_16';hex='00B0000010'}
    )) {
        [byte[]]$command=for($i=0;$i -lt $probe.hex.Length;$i+=2){[Convert]::ToByte($probe.hex.Substring($i,2),16)}
        $pci=New-Object DniLiveProbe+Pci; $pci.Protocol=$protocol; $pci.Length=8
        $response=New-Object byte[] 4096; $size=[uint32]$response.Length
        $timer.Restart()
        Check ([DniLiveProbe]::SCardTransmit($card,[ref]$pci,$command,$command.Length,[IntPtr]::Zero,$response,[ref]$size)) 'transmit'
        $status=if($size -ge 2){'{0:X2}{1:X2}' -f $response[$size-2],$response[$size-1]}else{'missing'}
        Record 'select_result' @{label=$probe.label;status=$status;responseBytes=$size;durationMs=$timer.ElapsedMilliseconds}
    }
} finally {
    if($transaction){$code=[DniLiveProbe]::SCardEndTransaction($card,0); Record 'transaction_ended' @{code=('0x{0:X8}' -f $code)}}
    if($card -ne [IntPtr]::Zero){$code=[DniLiveProbe]::SCardDisconnect($card,0);Record 'disconnected_leave_card' @{code=('0x{0:X8}' -f $code)}}
    if($ctx -ne [IntPtr]::Zero){$null=[DniLiveProbe]::SCardReleaseContext($ctx)}
}
