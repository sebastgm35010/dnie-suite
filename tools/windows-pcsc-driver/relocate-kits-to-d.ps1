# Relocate only the two Microsoft kits installed in this session, via their installers.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repo 'build/pcsc-deployment'
$statusPath = Join-Path $output 'toolchain-status.json'
function Status([string]$stage, [string]$detail) {
    @{stage=$stage; detail=$detail; updatedAt=[DateTime]::UtcNow.ToString('O'); pid=$PID} |
        ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
}
Start-Transcript -Path (Join-Path $output ('relocation-{0}-{1}.log' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)) | Out-Null
try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not (New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Necesita administrador.' }
    if (@(Get-Process wdksetup,winsdksetup,vs_BuildTools -ErrorAction SilentlyContinue).Count) { throw 'Todavia hay un instalador activo. Espere a que termine antes de reubicar.' }
    $kitRoot = 'D:\DNIeToolchain\WindowsKits\10'
    $env:TEMP = 'D:\DNIeToolchain\Temp'; $env:TMP = $env:TEMP
    New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
    $registry = @('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*')
    foreach ($name in @('Windows Driver Kit - Windows 10.0.26100.6584','Windows Software Development Kit - Windows 10.0.26100.7705')) {
        $entry = @(Get-ItemProperty $registry -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -eq $name -and $_.BundleCachePath })
        if ($entry.Count -eq 0) { continue }
        if ($entry.Count -ne 1) { throw 'Registro de instalacion ambiguo; no se retiraron versiones anteriores.' }
        $installer = $entry[0].BundleCachePath
        $signature = Get-AuthenticodeSignature -LiteralPath $installer
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch '(^|,\s*)O=Microsoft Corporation(,|$)') { throw 'Instalador en cache sin firma Microsoft valida.' }
        Status 'relocating-uninstall' "Retirando solamente la instalacion nueva: $name"
        $uninstallLog = Join-Path $env:TEMP ('relocation-uninstall-{0}.log' -f [IO.Path]::GetFileNameWithoutExtension($installer))
        $removal = Start-Process -FilePath $installer -ArgumentList @('/uninstall','/quiet','/norestart','/log',$uninstallLog) -Wait -PassThru -WindowStyle Hidden
        if ($removal.ExitCode -notin @(0,3010)) { throw "Desinstalacion: codigo $($removal.ExitCode). No se borraron carpetas manualmente." }
    }
    $oldRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    $addon = @(Get-ItemProperty $registry -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -eq 'Windows SDK AddOn' -and $_.DisplayVersion -eq '10.1.0.0' })
    if ($oldRoot -and [IO.Path]::GetFullPath($oldRoot).TrimEnd('\') -ne $kitRoot -and $addon.Count) {
        # Newly added VS WDK component retains the shared C: root after SDK removal.
        # Remove it through Visual Studio Installer; let the SDK establish D: first.
        $vsSetup = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/setup.exe'
        $signature = Get-AuthenticodeSignature -LiteralPath $vsSetup
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch '(^|,\s*)O=Microsoft Corporation(,|$)') { throw 'Visual Studio Installer no tiene firma Microsoft valida.' }
        Status 'relocating-addon' 'Retirando el componente WDK nuevo de Build Tools que conserva la raiz C:, mediante Visual Studio Installer.'
        $modify = Start-Process -FilePath $vsSetup -ArgumentList @('modify','--installPath','D:\DNIeToolchain\VS2022','--remove','Component.Microsoft.Windows.DriverKit','--quiet','--norestart') -Wait -PassThru -WindowStyle Hidden
        if ($modify.ExitCode -notin @(0,3010)) { throw "Cambio del componente WDK: codigo $($modify.ExitCode)." }
        $addon = @(Get-ItemProperty $registry -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -eq 'Windows SDK AddOn' -and $_.DisplayVersion -eq '10.1.0.0' })
        if ($addon.Count) {
            # VC's SDK AddOn survives removal of the optional WDK component. This exact
            # MSI was newly installed by our Build Tools setup and owns KitsRoot10.
            if ($addon.Count -ne 1 -or $addon[0].PSChildName -ne '{13D2A7A2-1B42-4C03-8F0F-697991259AEE}') { throw 'Complemento SDK distinto del instalado en esta sesion; no se retirara.' }
            Status 'relocating-addon' 'Retirando mediante MSI el SDK AddOn nuevo que conserva la ruta C:.'
            $addonRemoval = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/msiexec.exe') -ArgumentList @('/x',$addon[0].PSChildName,'/qn','/norestart','/L*v','D:\DNIeToolchain\Temp\sdk-addon-relocation.log') -Wait -PassThru -WindowStyle Hidden
            if ($addonRemoval.ExitCode -notin @(0,3010)) { throw "SDK AddOn: codigo $($addonRemoval.ExitCode)." }
        }
    }
    # Installed Roots may retain the old location after uninstall. Do not edit it:
    # request the new path from Microsoft's installer and validate its result.
    $remaining = @(Get-ItemProperty $registry -ErrorAction SilentlyContinue | Where-Object {
        $_.BundleCachePath -and $_.DisplayName -in @('Windows Driver Kit - Windows 10.0.26100.6584','Windows Software Development Kit - Windows 10.0.26100.7705')
    })
    if ($remaining.Count) { throw 'Aun figura alguno de los kits nuevos instalado; no se iniciara otra instalacion.' }
    # Microsoft leaves KitsRoot10 after every owning product has been removed. It
    # blocks /installpath. Back up and clear only that obsolete tool-path value;
    # never touch other kits, Windows policies, driver registration or user files.
    $rootBackup = @()
    foreach ($key in @('HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots','HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots')) {
        $value = (Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue).KitsRoot10
        if ($value -and [IO.Path]::GetFullPath($value).TrimEnd('\') -eq 'C:\Program Files (x86)\Windows Kits\10') {
            $otherKits = @(Get-ItemProperty $registry -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -match '^Windows (SDK|Software Development Kit|Driver Kit|Assessment)' })
            if ($otherKits.Count) { throw 'Otro producto de kits aun registrado; se conserva su ruta y se detiene la reubicacion.' }
            if (Test-Path -LiteralPath 'C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\um\windows.h') { throw 'Todavia existen headers SDK activos en C:; no se cambiara su ruta.' }
            $rootBackup += @{key=$key; name='KitsRoot10'; value=$value}
        }
    }
    if ($rootBackup.Count) {
        $rootBackup | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $output 'sdk-root-before-relocation.json') -Encoding UTF8
        foreach ($record in $rootBackup) { Remove-ItemProperty -LiteralPath $record.key -Name KitsRoot10 -ErrorAction Stop }
    }
    $winget = (Get-Command winget.exe -CommandType Application).Source
    Status 'relocating-sdk' 'Reinstalando SDK minimo x64/x86 y firma en D:\DNIeToolchain\WindowsKits\10.'
    $options = '/quiet /norestart /installpath D:\DNIeToolchain\WindowsKits\10 /features OptionId.DesktopCPPx64 OptionId.DesktopCPPx86 OptionId.SigningTools OptionId.MSIInstallTools /log D:\DNIeToolchain\Temp\sdk-relocation-install.log'
    & $winget install --exact --id Microsoft.WindowsSDK.10.0.26100 --source winget --silent --accept-source-agreements --accept-package-agreements --disable-interactivity --override $options
    if ($LASTEXITCODE -ne 0) { throw "Reinstalacion SDK: codigo $LASTEXITCODE." }
    $actualRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    if (-not $actualRoot -or [IO.Path]::GetFullPath($actualRoot).TrimEnd('\') -ne $kitRoot) { throw 'SDK no confirmo ubicacion D:. No se instalara WDK ni se compilara.' }
    Status 'relocated-sdk-paused' 'SDK y Build Tools ubicados en D:. WDK pendiente; desarrollo detenido por solicitud del usuario.'
}
catch { Status 'relocation-failed' $_.Exception.Message; Write-Host $_.Exception.Message -ForegroundColor Red; Stop-Transcript | Out-Null; exit 1 }
Stop-Transcript | Out-Null
exit 0
