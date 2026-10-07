function Show-BridgeDebugParameters($parent,[string]$entry,[string]$storage) {
    if([IO.Path]::GetExtension($entry) -ne '.ps1'){throw 'Este lanzador ejecuta modulos PowerShell. Otros motores se abren desde el catalogo.'}
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile($entry,[ref]$tokens,[ref]$errors)
    if($errors){throw 'Modulo con errores de sintaxis.'}
    $dialog=New-Object Windows.Forms.Form
    $dialog.Text='Parametros - '+[IO.Path]::GetFileName($entry);$dialog.ClientSize=New-Object Drawing.Size(700,520)
    $dialog.AutoScroll=$true;$dialog.StartPosition='CenterParent'
    $fields=@();$y=20
    foreach($parameter in $ast.ParamBlock.Parameters) {
        $name=$parameter.Name.VariablePath.UserPath
        if($name -notmatch '^[A-Za-z][A-Za-z0-9]*$'){throw 'Parametro no permitido.'}
        $isSwitch=[bool]($parameter.Attributes | Where-Object {$_ -is [Management.Automation.Language.TypeConstraintAst] -and $_.TypeName.FullName -eq 'switch'})
        $mandatory=[bool]($parameter.Attributes | Where-Object {$_ -is [Management.Automation.Language.AttributeAst] -and $_.TypeName.FullName -eq 'Parameter'} | ForEach-Object {$_.NamedArguments} | Where-Object {$_.ArgumentName -eq 'Mandatory' -and $_.Argument.Extent.Text -ne '$false'})
        $label=New-Object Windows.Forms.Label;$label.Text=$name+$(if($mandatory){' *'}else{''});$label.SetBounds(16,$y,200,26);$dialog.Controls.Add($label)
        $input=if($isSwitch){New-Object Windows.Forms.CheckBox}else{New-Object Windows.Forms.TextBox}
        $input.SetBounds(225,$y,445,26);$dialog.Controls.Add($input)
        $fields+= [pscustomobject]@{Name=$name;Input=$input;Switch=$isSwitch;Mandatory=$mandatory};$y+=40
    }
    $notice=New-Object Windows.Forms.Label
    $notice.Text='Vacio conserva el valor por defecto del modulo. Los sondeos y pruebas de controlador necesitan una sesion detenida. Los errores se guardan en la carpeta privada de esta ejecucion.'
    $notice.SetBounds(16,$y,654,60);$dialog.Controls.Add($notice);$y+=65
    $run=New-Object Windows.Forms.Button;$run.Text='Ejecutar modulo';$run.SetBounds(16,$y,200,36)
    $run.Add_Click({
        try {
            $arguments='-NoProfile -NonInteractive -File '+(ConvertTo-BridgeArgument $entry)
            foreach($field in $fields) {
                if($field.Mandatory -and (($field.Switch -and -not $field.Input.Checked) -or (-not $field.Switch -and -not $field.Input.Text.Trim()))) {throw ('Falta el parametro '+$field.Name)}
                if($field.Switch){if($field.Input.Checked){$arguments+=' -'+$field.Name}}
                elseif($field.Input.Text.Trim()){$arguments+=' -'+$field.Name+' '+(ConvertTo-BridgeArgument $field.Input.Text.Trim())}
            }
            $directory=Join-Path $storage ('debug-run-'+[guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $directory | Out-Null
            $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
            & icacls.exe $directory /inheritance:r /grant:r "*$($sid):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
            if($LASTEXITCODE){throw 'No se pudo proteger el registro.'}
            Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $directory 'output.txt') -RedirectStandardError (Join-Path $directory 'error.txt') | Out-Null
            [Windows.Forms.MessageBox]::Show(('Modulo iniciado. Registros: '+$directory),'Debug') | Out-Null
            $dialog.Close()
        } catch {[Windows.Forms.MessageBox]::Show($_.Exception.Message,'Debug') | Out-Null}
    }.GetNewClosure())
    $dialog.Controls.Add($run)
    try{$dialog.ShowDialog($parent) | Out-Null}finally{$dialog.Dispose()}
}
