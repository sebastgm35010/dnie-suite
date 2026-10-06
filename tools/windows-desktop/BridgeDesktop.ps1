param([switch]$CheckOnly,[switch]$SelfTest)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'BridgeDesktopController.ps1')
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
$desktopMutex = New-Object Threading.Mutex($false,'Local\DNIeBridgeDesktop')
$owned = $false
try { $owned = $desktopMutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $owned = $true }
if (-not $owned) { $desktopMutex.Dispose(); [Windows.Forms.MessageBox]::Show('El panel del puente ya esta abierto.','Puente DNIe') | Out-Null; return }
New-Item -ItemType Directory -Path $storage -Force | Out-Null
$control=New-BridgeDesktopControl $service $dependency $runtime $storage
function StopSelector {
    Stop-BridgeSelector $control
}
function StartHost([bool]$stopHost) {
    Start-BridgeDesktopHost $hostEntry $pcName.Text.Trim() $phoneName.Text.Trim() $stopHost
}
$form = New-Object Windows.Forms.Form
$form.Text = 'Puente DNIe - PC'
$form.ClientSize = New-Object Drawing.Size(680,490)
$form.StartPosition = 'CenterScreen'
$form.Font = New-Object Drawing.Font('Segoe UI',10)
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
function Label([string]$text,[int]$y) {
    $control=New-Object Windows.Forms.Label
    $control.Text=$text; $control.SetBounds(24,$y,632,40); $form.Controls.Add($control)
    $control
}
$title=Label 'Lector NFC para aplicaciones de Windows' 20
$title.Font=New-Object Drawing.Font('Segoe UI',15,[Drawing.FontStyle]::Bold)
$null=Label '1. Mantiene el host activo. 2. Activa el selector para Ciudadano Digital.' 65
$null=Label 'PC Tailscale' 106
$pcName=New-Object Windows.Forms.TextBox
$pcName.Text='sebas-laptop.tail2752e9.ts.net';$pcName.SetBounds(210,108,440,28);$form.Controls.Add($pcName)
$null=Label 'Telefono Tailscale' 146
$phoneName=New-Object Windows.Forms.TextBox
$phoneName.Text='sebas-poco.tail2752e9.ts.net';$phoneName.SetBounds(210,148,440,28);$form.Controls.Add($phoneName)
function Button([string]$text,[int]$x,[int]$y,[scriptblock]$action) {
    $control=New-Object Windows.Forms.Button
    $control.Text=$text;$control.SetBounds($x,$y,200,36)
    $control.Add_Click({try { & $action } catch {
        if ($SelfTest) { $form.Close() }
        else { [Windows.Forms.MessageBox]::Show($_.Exception.Message,'Puente DNIe') | Out-Null }
    }}.GetNewClosure())
    $form.Controls.Add($control);$control
}
$hostStart=Button 'Iniciar host' 24 193 { StartHost $false }
$hostStop=Button 'Detener host' 240 193 { StartHost $true }
$hostStatus=Label 'Comprobando host Tailscale...' 240
$selectorStart=Button 'Activar selector RENIEC' 24 285 {
    Start-BridgeSelector $control
    $status.Text='Preparando selector...'
}
$selectorStop=Button 'Retirar selector' 240 285 { StopSelector }
$status=Label 'Selector detenido. Activalo antes de iniciar Ciudadano Digital.' 332
$null=Label 'Conecta el DNI desde Android y mantenlo apoyado. Firma Peru usa el lector PC/SC; el selector se limita al Java de RENIEC.' 382
$null=Label 'Cerrar este panel retira el selector. El host se detiene con su boton. No modifica Windows Hello ni servicios de tarjetas.' 435
$timer=New-Object Windows.Forms.Timer
$timer.Interval=1000
$timer.Add_Tick({
    try {
        $snapshot=Get-BridgeDesktopState $control
        $listening=$snapshot.HostListening
        $hostStatus.Text=if($listening){'Puerto del host disponible por Tailscale.'}else{'Host sin escucha en el puerto Tailscale.'}
        $hostStart.Enabled=-not $listening; $hostStop.Enabled=$listening
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
$form.Add_FormClosing({StopSelector})
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
