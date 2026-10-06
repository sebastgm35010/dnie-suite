# Only development tools. Never installs our reader or changes driver-signing policies.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repo 'build/pcsc-deployment'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$statusPath = Join-Path $output 'toolchain-status.json'
function Status([string]$stage, [string]$detail) {
    @{ stage=$stage; detail=$detail; updatedAt=[DateTime]::UtcNow.ToString('O'); pid=$PID } |
        ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
}
$transcript = Join-Path $output ('toolchain-{0}-{1}.log' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)
Start-Transcript -Path $transcript | Out-Null
try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not (New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Esta instalacion requiere la terminal de administrador.'
    }
    $winget = (Get-Command winget.exe -CommandType Application -ErrorAction Stop).Source
    $installRoot = 'D:\DNIeToolchain'
    if (-not (Test-Path -LiteralPath 'D:\')) { throw 'No se encontro D: para las herramientas. No se cambiaron rutas del sistema.' }
    if ((Get-PSDrive C).Free -lt 8GB -or (Get-PSDrive D).Free -lt 15GB) { throw 'Espacio insuficiente para instalar las herramientas en D: con componentes compartidos en C:.' }
    New-Item -ItemType Directory -Path (Join-Path $installRoot 'Temp') -Force | Out-Null
    # These environment changes are restricted to this process and its installers.
    $env:TEMP = Join-Path $installRoot 'Temp'; $env:TMP = $env:TEMP
    $common = @('--exact','--source','winget','--accept-source-agreements','--accept-package-agreements','--disable-interactivity','--silent')
    $kitRoot = 'D:\DNIeToolchain\WindowsKits\10'
    $existingRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    if ($existingRoot -and [IO.Path]::GetFullPath($existingRoot).TrimEnd('\') -ne $kitRoot) {
        throw 'Ya existe un SDK fuera de D:. Reubicar los kits nuevos primero; no se instalara mas contenido en C:.'
    }
    Status 'build-tools' 'Instalando MSVC x64 y Build Tools 2022 en D:\DNIeToolchain.'
    $vsOptions = '--wait --quiet --norestart --nocache --installPath "D:\DNIeToolchain\VS2022" --path cache="D:\DNIeToolchain\VSCache" --path shared="D:\DNIeToolchain\VSShared" --add Microsoft.VisualStudio.Workload.VCTools --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64 --add Microsoft.VisualStudio.Component.VC.Runtimes.x86.x64.Spectre'
    if (-not (Get-ChildItem -Path 'D:\DNIeToolchain\VS2022\VC\Tools\MSVC\*\bin\Hostx64\x64\cl.exe' -ErrorAction SilentlyContinue)) {
        & $winget install --id Microsoft.VisualStudio.2022.BuildTools @common --override $vsOptions
        if ($LASTEXITCODE -ne 0) { throw "Build Tools: codigo $LASTEXITCODE. Revise el registro; no se continuara con el controlador." }
    }
    Status 'sdk' 'Instalando SDK Windows de la familia 26100.'
    if (-not (Test-Path -LiteralPath (Join-Path $kitRoot 'Include/10.0.26100.0/um/windows.h'))) {
        $sdkOptions = '/quiet /norestart /installpath "D:\DNIeToolchain\WindowsKits\10" /features OptionId.DesktopCPPx64 OptionId.DesktopCPPx86 OptionId.SigningTools OptionId.MSIInstallTools'
        & $winget install --id Microsoft.WindowsSDK.10.0.26100 @common --override $sdkOptions
        if ($LASTEXITCODE -ne 0) { throw "Windows SDK: codigo $LASTEXITCODE." }
    }
    $sdkRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    if (-not $sdkRoot -or [IO.Path]::GetFullPath($sdkRoot).TrimEnd('\') -ne $kitRoot) { throw 'El SDK no confirmo ubicacion en D:. Se detiene WDK para proteger C:.' }
    $umdfToolset = Join-Path $installRoot 'VS2022/MSBuild/Microsoft/VC/v170/Platforms/x64/PlatformToolsets/WindowsUserModeDriver10.0/Toolset.props'
    if (-not (Test-Path -LiteralPath $umdfToolset)) {
        Status 'wdk-component' 'Agregando soporte WDK de Build Tools despues de fijar el SDK en D:.'
        $setup = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/setup.exe'
        $signature = Get-AuthenticodeSignature -LiteralPath $setup
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'No se pudo validar el instalador de Visual Studio.' }
        $modify = Start-Process -FilePath $setup -ArgumentList @('modify','--installPath','D:\DNIeToolchain\VS2022','--add','Component.Microsoft.Windows.DriverKit.BuildTools','--quiet','--norestart') -Wait -PassThru -WindowStyle Hidden
        if ($modify.ExitCode -notin @(0,3010)) { throw "Componente WDK de Build Tools: codigo $($modify.ExitCode)." }
    }
    $sdkRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    if (-not $sdkRoot -or [IO.Path]::GetFullPath($sdkRoot).TrimEnd('\') -ne $kitRoot) { throw 'El componente WDK cambio la ruta del SDK. Se detiene la instalacion.' }
    Status 'wdk' 'Instalando WDK 26100 compatible con Visual Studio 2022.'
    if (-not (Test-Path -LiteralPath (Join-Path $kitRoot 'Include/10.0.26100.0/km/ntddk.h'))) {
        & $winget install --id Microsoft.WindowsWDK.10.0.26100 @common --override '/quiet /norestart /installpath "D:\DNIeToolchain\WindowsKits\10"'
        if ($LASTEXITCODE -ne 0) { throw "Windows WDK: codigo $LASTEXITCODE." }
    }
    $sdkRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    if (-not $sdkRoot -or [IO.Path]::GetFullPath($sdkRoot).TrimEnd('\') -ne $kitRoot) { throw 'El WDK no confirmo ubicacion en D:.' }
    foreach ($required in @($umdfToolset, (Join-Path $kitRoot 'Include/wdf/umdf/2.15/wdf.h'), (Join-Path $kitRoot 'Tools/10.0.26100.0/x64/InfVerif.exe'))) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Instalacion incompleta: falta $required." }
    }
    Status 'installed' 'Instaladores terminados. Falta verificar MSVC/WDK y compilar el proyecto.'
}
catch { Status 'failed' $_.Exception.Message; Write-Host $_.Exception.Message -ForegroundColor Red; Stop-Transcript | Out-Null; exit 1 }
Stop-Transcript | Out-Null
exit 0
