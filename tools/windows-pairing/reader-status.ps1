$ErrorActionPreference = 'Stop'
if (-not ('DniBridgeReaderStatus' -as [type])) {
    Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class DniBridgeReaderStatus {
    [DllImport("winscard.dll")] public static extern uint SCardEstablishContext(uint scope, IntPtr a, IntPtr b, out IntPtr context);
    [DllImport("winscard.dll")] public static extern uint SCardReleaseContext(IntPtr context);
    [DllImport("winscard.dll", CharSet=CharSet.Unicode)] public static extern uint SCardListReadersW(IntPtr context, string groups, [Out] char[] readers, ref uint size);
}
'@
}
$context = [IntPtr]::Zero
try {
    $status = [DniBridgeReaderStatus]::SCardEstablishContext(2, [IntPtr]::Zero, [IntPtr]::Zero, [ref]$context)
    if ($status) { throw ('PC/SC no disponible: 0x{0:X8}' -f $status) }
    [uint32]$length = 0
    $status = [DniBridgeReaderStatus]::SCardListReadersW($context, $null, $null, [ref]$length)
    if ($status) { throw ('Sin lectores enumerables: 0x{0:X8}' -f $status) }
    $buffer = New-Object char[] $length
    $status = [DniBridgeReaderStatus]::SCardListReadersW($context, $null, $buffer, [ref]$length)
    if ($status) { throw 'No se pudo leer la lista PC/SC.' }
    $readers = @((-join $buffer).Split([char]0) | Where-Object { $_ })
    Write-Host 'Lectores PC/SC en el orden entregado por Windows:'
    $readers | ForEach-Object { Write-Host "  $_" }
    if (-not ($readers | Where-Object { $_ -like 'DNIe Bridge Android NFC*' })) { throw 'El lector DNIe Bridge no aparece; revisar instalacion del controlador.' }
    if ($readers[0] -notlike 'DNIe Bridge Android NFC*') {
        Write-Warning 'cddnie elige el primer lector y podria abrir otro dispositivo. Este diagnostico no deshabilita otros lectores.'
    } else { Write-Host 'DNIe Bridge es el primer lector: compatible con la seleccion inicial de cddnie.' }
} finally {
    if ($context -ne [IntPtr]::Zero) { [DniBridgeReaderStatus]::SCardReleaseContext($context) | Out-Null }
}
