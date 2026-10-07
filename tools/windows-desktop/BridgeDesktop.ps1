param([switch]$CheckOnly,[switch]$SelfTest,[switch]$LayoutTest)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'BridgeDesktopController.ps1')
. (Join-Path $PSScriptRoot 'BridgeNetworkController.ps1')
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$service = Join-Path $repo 'tools\windows-reader-selector\selector_service.py'
$hostEntry = Join-Path $repo 'tools\windows-pairing\start-tailnet-host.ps1'
$dependency = if ($env:DNIE_FRIDA_DIR) { $env:DNIE_FRIDA_DIR } else { 'D:\DNIeToolchain\Instrumentation\python' }
$runtime = Join-Path $env:USERPROFILE '.dcdelivery-2.0\files\customjre\bin\java.exe'
$storage = if (Test-Path 'D:\DNIeToolchain') { 'D:\DNIeToolchain\Desktop' } else { Join-Path $env:LOCALAPPDATA 'DNIeBridge\Desktop' }
if ($CheckOnly) {
    [pscustomobject]@{ServiceExists=(Test-Path $service); RuntimeExists=(Test-Path $runtime); DependencyExists=(Test-Path (Join-Path $dependency 'frida')); PythonAvailable=[bool](Get-Command python.exe -ErrorAction SilentlyContinue)}
    return
}
$desktopMutex = New-Object Threading.Mutex($false,$(if ($SelfTest -or $LayoutTest) {'Local\DNIeBridgeDesktopSelfTest'} else {'Local\DNIeBridgeDesktop'}))
$owned = $false
try { $owned = $desktopMutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $owned = $true }
if (-not $owned) { $desktopMutex.Dispose(); [Windows.Forms.MessageBox]::Show('El panel del puente ya esta abierto.','Puente DNIe') | Out-Null; return }
if($LayoutTest){$storage=Join-Path $storage ('layout-'+[guid]::NewGuid().ToString('N'))}
New-Item -ItemType Directory -Path $storage -Force | Out-Null
$control=New-BridgeDesktopControl $service $dependency $runtime $storage
$networkEntry=Join-Path $repo 'tools\windows-pairing\network-session.ps1'
$preferencePath=Join-Path $storage 'settings.json'
$preferences=Read-BridgeDesktopPreferences $preferencePath
$script:networkOperation=$null
function StopSelector {
    Stop-BridgeSelector $control
}
function StartHost([bool]$stopHost) {
    if ($stopHost) {
        $script:networkOperation=Start-BridgeDesktopNetwork $networkEntry 'LAN' '127.0.0.1' '' $true
        return
    }
    if ($method.SelectedItem -eq 'Tailscale') {
        Start-BridgeDesktopHost $hostEntry $pcName.Text.Trim() $phoneName.Text.Trim() $stopHost
    } else {
        if (-not $addresses.SelectedItem) { throw $(if($method.SelectedItem -eq 'USB directo'){'Reconecta el cable y actualiza la lista. Si UsbDk acaba de instalarse, Windows puede requerir reiniciar.'}else{'Actualiza la lista y elige la interfaz LAN.'}) }
        $kind=if($method.SelectedItem -eq 'LAN TLS'){'LAN'}else{'USB'}
        $peer=if($kind -eq 'USB'){$addresses.SelectedItem.Serial}else{$phoneName.Text.Trim()}
        $script:networkOperation=Start-BridgeDesktopNetwork $networkEntry $kind $addresses.SelectedItem.Address $peer $stopHost
    }
}
$form = New-Object Windows.Forms.Form
$form.Text = 'Puente DNIe - PC'
$form.ClientSize = New-Object Drawing.Size(680,740)
$form.StartPosition = 'CenterScreen'
$form.Font = New-Object Drawing.Font('Segoe UI',10)
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
function Label([string]$text,[int]$y) {
    $control=New-Object Windows.Forms.Label
    $control.Text=$text; $control.SetBounds(24,$y,632,40); $form.Controls.Add($control); $control.SendToBack()
    $control
}
$title=Label 'Lector NFC para aplicaciones de Windows' 20
$title.Font=New-Object Drawing.Font('Segoe UI',15,[Drawing.FontStyle]::Bold)
$null=Label '1. Mantiene el host activo. 2. Activa el selector para Ciudadano Digital.' 65
$method=New-Object Windows.Forms.ComboBox
$method.DropDownStyle='DropDownList';$method.SetBounds(210,106,440,28)
$method.Items.AddRange(@('Tailscale','LAN TLS','USB directo'));$method.SelectedIndex=0;$form.Controls.Add($method)
$null=Label 'Conexion' 106
$null=Label 'PC Tailscale' 146
$pcName=New-Object Windows.Forms.TextBox
$pcName.Text='sebas-laptop.tail2752e9.ts.net';$pcName.SetBounds(210,148,440,28);$form.Controls.Add($pcName)
$phoneLabel=Label 'Telefono Tailscale' 186
$phoneName=New-Object Windows.Forms.TextBox
$phoneName.Text='sebas-poco.tail2752e9.ts.net';$phoneName.SetBounds(210,188,440,28);$form.Controls.Add($phoneName)
$null=Label 'Interfaz LAN / USB' 226
$addresses=New-Object Windows.Forms.ComboBox
$addresses.DropDownStyle='DropDownList';$addresses.DisplayMember='Display';$addresses.SetBounds(210,228,440,28);$form.Controls.Add($addresses)
$null=Label 'Identidad permanente de la PC. Sin cierre por inactividad.' 268
function RefreshAddresses {
    $selected=if($addresses.SelectedItem){$addresses.SelectedItem.Address}else{$preferences.Address}
    $addresses.Items.Clear()
    try{$items=if($method.SelectedItem -eq 'USB directo'){@(Get-BridgeUsbDevices)}else{@(Get-BridgeNetworkInterfaces)}}catch{$items=@();if(-not $LayoutTest){[Windows.Forms.MessageBox]::Show($_.Exception.Message,'USB directo')|Out-Null}}
    foreach($item in $items) { $null=$addresses.Items.Add($item); if($item.Address -eq $selected){$addresses.SelectedItem=$item} }
    if ($addresses.SelectedIndex -lt 0 -and $addresses.Items.Count) {$addresses.SelectedIndex=0}
}
RefreshAddresses
if($preferences.Method -in @('Tailscale','LAN TLS','USB directo')) {$method.SelectedItem=$preferences.Method}
RefreshAddresses
if($preferences.Pc) {$pcName.Text=$preferences.Pc};if($preferences.Phone){$phoneName.Text=$preferences.Phone}
$method.Add_SelectedIndexChanged({$network=$method.SelectedItem -ne 'Tailscale';$addresses.Enabled=$network;$pcName.Enabled=-not $network;$phoneName.Enabled=$method.SelectedItem -ne 'USB directo';$phoneLabel.Text=if($network){'IP Wi-Fi del telefono'}else{'Telefono Tailscale'};RefreshAddresses})
$network=$method.SelectedItem -ne 'Tailscale';$addresses.Enabled=$network;$pcName.Enabled=-not $network;$phoneName.Enabled=$method.SelectedItem -ne 'USB directo'
$phoneLabel.Text=if($method.SelectedItem -eq 'LAN TLS'){'IP Wi-Fi del telefono'}elseif($method.SelectedItem -eq 'USB directo'){'USB sin compartir red'}else{'Telefono Tailscale'}
function Button([string]$text,[int]$x,[int]$y,[scriptblock]$action) {
    $control=New-Object Windows.Forms.Button
    $control.Text=$text;$control.SetBounds($x,$y,200,36)
    $control.Add_Click({try { & $action } catch {
        if ($SelfTest) { $form.Close() }
        else { [Windows.Forms.MessageBox]::Show($_.Exception.Message,'Puente DNIe') | Out-Null }
    }}.GetNewClosure())
    $form.Controls.Add($control);$control
}
$hostStart=Button 'Iniciar host' 24 310 { StartHost $false }
$hostStop=Button 'Detener host' 240 310 { StartHost $true }
$refresh=Button 'Actualizar interfaces' 456 310 { RefreshAddresses }
$usbInstall=Button 'Instalar soporte USB' 456 226 {Start-Process powershell.exe -WindowStyle Hidden -Verb RunAs -ArgumentList ('-NoProfile -File '+(ConvertTo-BridgeArgument (Join-Path $repo 'tools/windows-pairing/install-usb-support.ps1')))}
$usbInstall.Visible=$false
$null=Label 'LAN: introduce la IP Wi-Fi del telefono y autoriza Android. USB directo: selecciona el telefono y acepta el accesorio USB. Si no aparece, reconecta el cable. QR una sola vez por PC.' 570
$hostStatus=Label 'Comprobando host...' 365
$selectorStart=Button 'Activar selector RENIEC' 24 420 {
    Start-BridgeSelector $control
    $status.Text='Preparando selector...'
}
$selectorStop=Button 'Retirar selector' 240 420 { StopSelector }
$status=Label 'Selector detenido. Activalo antes de iniciar Ciudadano Digital.' 466
$null=Label 'Conecta el DNI desde Android y mantenlo apoyado. Firma Peru usa el lector PC/SC; el selector se limita al Java de RENIEC.' 520
$null=Label 'Cerrar este panel retira el selector. El host se detiene con su boton. No modifica Windows Hello ni servicios de tarjetas.' 660
$timer=New-Object Windows.Forms.Timer
$timer.Interval=1000
$timer.Add_Tick({
    try {
        $snapshot=Get-BridgeDesktopState $control
        $listening=$snapshot.HostListening
        $hostStatus.Text=if($listening){'Host disponible o sesion de tarjeta activa.'}else{'No hay host ni sesion en el puerto del puente.'}
        $preparing=$script:networkOperation -and -not $script:networkOperation.HasExited
        $hostStart.Enabled=-not $listening -and -not $preparing; $hostStop.Enabled=$listening -and -not $preparing
        $method.Enabled=-not $listening -and -not $preparing
        $network=$method.SelectedItem -ne 'Tailscale'
        $usbInstall.Visible=$method.SelectedItem -eq 'USB directo' -and -not(Test-Path 'C:\Program Files\UsbDk Runtime Library\UsbDkHelper.dll')
        $usbInstall.Enabled=-not $listening -and -not $preparing
        $addresses.Width=if($usbInstall.Visible){230}else{440}
        $addresses.Enabled=$network -and -not $listening -and -not $preparing
        $refresh.Enabled=-not $listening -and -not $preparing
        $pcName.Enabled=-not $network -and -not $listening -and -not $preparing
        $phoneName.Enabled=$method.SelectedItem -ne 'USB directo' -and -not $listening -and -not $preparing
        if($script:networkOperation -and $script:networkOperation.HasExited) {
            if($script:networkOperation.ExitCode -ne 0){[Windows.Forms.MessageBox]::Show($(if(Test-Path $script:networkOperation.ErrorLog){[IO.File]::ReadAllText($script:networkOperation.ErrorLog)}else{'No se pudo iniciar el transporte.'}),'Puente DNIe') | Out-Null}
            $script:networkOperation.Dispose();$script:networkOperation=$null
        }
        $running=$snapshot.SelectorRunning
        $selectorStart.Enabled=-not $running;$selectorStop.Enabled=[bool]$running
            $status.Text=switch($snapshot.Phase){
                'running' { "Selector activo. Listas filtradas: $($snapshot.FilteredLists). Abre el flujo RENIEC." }
                'degraded' { 'No se pudo aplicar alguna seleccion. Deten y revisa events.jsonl antes de probar.' }
                'failed' { 'Selector no iniciado. Revisa la dependencia y el registro local.' }
                'stopped' { 'Selector retirado. Los procesos vuelven a ver sus lectores originales.' }
                default { 'Preparando selector...' }
            }
    } catch { $status.Text='No se pudo actualizar el estado del panel.' }
})
$form.Add_FormClosing({
    StopSelector
    Save-BridgeDesktopPreferences $preferencePath ([pscustomobject]@{Method=[string]$method.SelectedItem;Pc=$pcName.Text.Trim();Phone=$phoneName.Text.Trim();Address=$(if($addresses.SelectedItem){$addresses.SelectedItem.Address}else{''})})
})
# Production packages contain neither this module nor any diagnostic scripts.
$debugEntry=Join-Path $PSScriptRoot 'debug\BridgeDebugMenu.ps1'
if(Test-Path -LiteralPath $debugEntry) { . $debugEntry; Add-BridgeDebugMenu $form $repo $storage }
if ($LayoutTest) {
    $form.ShowInTaskbar=$false;$form.Opacity=0
    $form.Add_Shown({
        $bitmap=New-Object Drawing.Bitmap($form.Width,$form.Height)
        try { $form.DrawToBitmap($bitmap,[Drawing.Rectangle]::new(0,0,$form.Width,$form.Height));$bitmap.Save((Join-Path $storage 'layout.png')) } finally {$bitmap.Dispose()}
        [pscustomobject]@{passed=$true;connectionMethods=$method.Items.Count;interfaces=$addresses.Items.Count;developmentButton=[bool]($form.Controls | Where-Object Text -eq 'Modo debug');scope='layout_only_no_card_or_host_change'} | ConvertTo-Json | Set-Content (Join-Path $storage 'layout-test.json') -Encoding UTF8
        Write-Output $storage
        $form.Close()
    })
}
if ($SelfTest) {
    $form.ShowInTaskbar=$false;$form.Opacity=0
    $script:testStarted=Get-Date;$script:testStopping=$false
    $script:testPassed=$false
    $form.Add_Shown({$selectorStart.PerformClick()})
    $timer.Add_Tick({
        $snapshot=Get-BridgeDesktopState $control
        if ($snapshot.Phase -eq 'running' -and -not $script:testStopping) {
            $script:testStopping=$true;$selectorStop.PerformClick()
        }
        if ($script:testStopping -and -not $snapshot.SelectorRunning -and $snapshot.Phase -eq 'stopped') {
            $script:testPassed=$true
            [IO.File]::WriteAllText((Join-Path $control.RunDirectory 'desktop-self-test.json'),'{"passed":true,"scope":"selector_start_stop_no_card"}',[Text.UTF8Encoding]::new($false))
            Write-Host 'Desktop selector start/stop self-test passed.';$form.Close()
        } elseif (((Get-Date)-$script:testStarted).TotalSeconds -gt 20) {
            StopSelector;$form.Close()
        }
    })
}
try { $timer.Start(); [Windows.Forms.Application]::Run($form) }
finally { StopSelector; $timer.Stop(); $timer.Dispose(); $form.Dispose(); $desktopMutex.ReleaseMutex(); $desktopMutex.Dispose() }
if ($SelfTest -and -not $script:testPassed) { throw 'Desktop self-test failed or timed out.' }
