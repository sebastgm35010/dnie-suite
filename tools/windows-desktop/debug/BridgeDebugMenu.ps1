. (Join-Path $PSScriptRoot 'BridgeDebugRunner.ps1')
function Add-BridgeDebugMenu($parent,[string]$repo,[string]$storage) {
    $button=New-Object Windows.Forms.Button
    $button.Text='Modo debug';$button.SetBounds(456,420,200,36)
    $button.Add_Click({ Show-BridgeDebugWindow $parent $repo $storage }.GetNewClosure())
    $parent.Controls.Add($button)
}
function Show-BridgeDebugWindow($parent,[string]$repo,[string]$storage) {
    $window=New-Object Windows.Forms.Form
    $window.Text='Desarrollo del puente - solo paquete Debug';$window.ClientSize=New-Object Drawing.Size(780,560)
    $window.StartPosition='CenterParent'
    $catalog=New-Object Windows.Forms.ListBox
    $catalog.SetBounds(16,16,748,290);$catalog.DisplayMember='Display'
    $diagnostics=Join-Path $repo 'tools\host-diagnostics'
    foreach($file in @(Get-ChildItem -LiteralPath $diagnostics -File | Where-Object Extension -In @('.ps1','.py','.mjs','.java','.js') | Sort-Object Name)) {
        $null=$catalog.Items.Add([pscustomobject]@{Display=$file.Name;Path=$file.FullName})
    }
    $window.Controls.Add($catalog)
    $notes=New-Object Windows.Forms.Label
    $notes.Text='Catalogo de modulos: observadores PC/SC, navegador auditable, correlacion APDU, PACE, pruebas sinteticas y captura de procesos. Abrir un modulo no lo ejecuta. Los sondeos de tarjeta y pruebas del controlador requieren una sesion detenida.'
    $notes.SetBounds(16,315,748,65);$window.Controls.Add($notes)
    $open=New-Object Windows.Forms.Button
    $open.Text='Abrir modulo seleccionado';$open.SetBounds(16,395,235,36)
    $open.Add_Click({if($catalog.SelectedItem){Start-Process notepad.exe -ArgumentList ('"'+$catalog.SelectedItem.Path+'"')}}.GetNewClosure())
    $window.Controls.Add($open)
    $execute=New-Object Windows.Forms.Button
    $execute.Text='Ejecutar PS1 con parametros';$execute.SetBounds(16,505,235,36)
    $execute.Add_Click({
        if($catalog.SelectedItem -and [IO.Path]::GetExtension($catalog.SelectedItem.Path) -eq '.ps1') {
            Show-BridgeDebugParameters $window $catalog.SelectedItem.Path $storage
        }
    }.GetNewClosure())
    $window.Controls.Add($execute)
    $presence=New-Object Windows.Forms.Button
    $presence.Text='Lectores: solo metadatos';$presence.SetBounds(267,395,235,36)
    $presence.Add_Click({
        $output=Join-Path $storage ('reader-metadata-'+[guid]::NewGuid().ToString('N')+'.txt')
        $errorFile=[IO.Path]::ChangeExtension($output,'.error.txt')
        $probe=Join-Path $diagnostics 'pcsc-presence.ps1'
        $job=Start-Process powershell.exe -WindowStyle Hidden -PassThru -ArgumentList ('-NoProfile -File "'+$probe+'"') -RedirectStandardOutput $output -RedirectStandardError $errorFile
        $job.WaitForExit(5000) | Out-Null
        Start-Process notepad.exe -ArgumentList ('"'+$output+'"')
    }.GetNewClosure())
    $window.Controls.Add($presence)
    $browser=New-Object Windows.Forms.Button
    $browser.Text='Navegador auditable (5 min)';$browser.SetBounds(518,395,245,36)
    $browser.Add_Click({
        $entry=Join-Path $diagnostics 'Start-HostDiagnostics.ps1'
        Start-Process powershell.exe -WindowStyle Hidden -ArgumentList ('-NoProfile -File "'+$entry+'" -Minutes 5')
    }.GetNewClosure())
    $window.Controls.Add($browser)
    $private=New-Object Windows.Forms.Label
    $private.Text='Los registros de desarrollo pueden contener datos personales. No se incluyen en el paquete de produccion. La captura se inicia manualmente; no se activa al conectar un DNI.'
    $private.SetBounds(16,460,748,65);$window.Controls.Add($private)
    try { $window.ShowDialog($parent) | Out-Null } finally { $window.Dispose() }
}
