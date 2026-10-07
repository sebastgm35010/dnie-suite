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
    if ($method.SelectedItem -eq 'Tailscale') {
        Start-BridgeDesktopHost $hostEntry $pcName.Text.Trim() $phoneName.Text.Trim() $stopHost
    } else {
        if (-not $addresses.SelectedItem) { throw 'Actualiza la lista y elige una interfaz de red.' }
        $kind=if($method.SelectedItem -eq 'LAN TLS'){'LAN'}else{'USB'}
        $script:networkOperation=Start-BridgeDesktopNetwork $networkEntry $kind $addresses.SelectedItem.Address ([int]$idle.Value) $stopHost
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
$method.Items.AddRange(@('Tailscale','LAN TLS','USB TLS'));$method.SelectedIndex=0;$form.Controls.Add($method)
$null=Label 'Conexion' 106
$null=Label 'PC Tailscale' 146
$pcName=New-Object Windows.Forms.TextBox
$pcName.Text='sebas-laptop.tail2752e9.ts.net';$pcName.SetBounds(210,148,440,28);$form.Controls.Add($pcName)
$null=Label 'Telefono Tailscale' 186
$phoneName=New-Object Windows.Forms.TextBox
$phoneName.Text='sebas-poco.tail2752e9.ts.net';$phoneName.SetBounds(210,188,440,28);$form.Controls.Add($phoneName)
$null=Label 'Interfaz LAN / USB' 226
$addresses=New-Object Windows.Forms.ComboBox
$addresses.DropDownStyle='DropDownList';$addresses.DisplayMember='Display';$addresses.SetBounds(210,228,440,28);$form.Controls.Add($addresses)
$idle=New-Object Windows.Forms.NumericUpDown
$idle.Minimum=5;$idle.Maximum=300;$idle.Value=120;$idle.SetBounds(460,268,100,28);$form.Controls.Add($idle)
$null=Label 'Avanzado: inactividad TLS (segundos)' 268
function RefreshAddresses {
    $selected=if($addresses.SelectedItem){$addresses.SelectedItem.Address}else{$preferences.Address}
    $addresses.Items.Clear()
    foreach($item in @(Get-BridgeNetworkInterfaces)) { $null=$addresses.Items.Add($item); if($item.Address -eq $selected){$addresses.SelectedItem=$item} }
    if ($addresses.SelectedIndex -lt 0 -and $addresses.Items.Count) {$addresses.SelectedIndex=0}
}
RefreshAddresses
if($preferences.Method -in @('Tailscale','LAN TLS','USB TLS')) {$method.SelectedItem=$preferences.Method}
if($preferences.Pc) {$pcName.Text=$preferences.Pc};if($preferences.Phone){$phoneName.Text=$preferences.Phone}
if($preferences.Idle -ge 5 -and $preferences.Idle -le 300){$idle.Value=$preferences.Idle}
$method.Add_SelectedIndexChanged({$network=$method.SelectedItem -ne 'Tailscale';$addresses.Enabled=$network;$idle.Enabled=$network;$pcName.Enabled=-not $network;$phoneName.Enabled=-not $network})
$network=$method.SelectedItem -ne 'Tailscale';$addresses.Enabled=$network;$idle.Enabled=$network;$pcName.Enabled=-not $network;$phoneName.Enabled=-not $network
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
$null=Label 'LAN: misma red. USB: activar Compartir Internet por USB en Android. Ambos usan QR y TLS, sin ADB. Permite la conexion en red privada si Windows lo solicita.' 570
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
        $addresses.Enabled=$network -and -not $listening -and -not $preparing
        $idle.Enabled=$addresses.Enabled;$refresh.Enabled=-not $listening -and -not $preparing
        $pcName.Enabled=-not $network -and -not $listening -and -not $preparing
        $phoneName.Enabled=$pcName.Enabled
        if($script:networkOperation -and $script:networkOperation.HasExited) {
            if($script:networkOperation.ExitCode -ne 0){[Windows.Forms.MessageBox]::Show('No se pudo iniciar o detener el host de red. Revisa la interfaz, el puerto y acepta la elevacion del backend.','Puente DNIe') | Out-Null}
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
    Save-BridgeDesktopPreferences $preferencePath ([pscustomobject]@{Method=[string]$method.SelectedItem;Pc=$pcName.Text.Trim();Phone=$phoneName.Text.Trim();Address=$(if($addresses.SelectedItem){$addresses.SelectedItem.Address}else{''});Idle=[int]$idle.Value})
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
