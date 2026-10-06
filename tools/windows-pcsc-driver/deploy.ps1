param(
    [ValidateSet('Diagnostico','Compilar','Instalar')][string]$Mode = 'Diagnostico',
    [string]$PackageDirectory
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'package-utils.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$logDirectory = Join-Path $repo 'build/pcsc-deployment'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$logPath = Join-Path $logDirectory ('deployment-{0}-{1}.log' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)
Start-Transcript -Path $logPath | Out-Null
function Find-Tool([string]$name) {
    $tool = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($tool) { return $tool.Source }
    # Fall back to SDK/WDK directories when tools are absent from the active PATH.
    $registeredRoot = (Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots' -ErrorAction SilentlyContinue).KitsRoot10
    $roots = @($env:WindowsSdkDir, $env:WDKContentRoot, $registeredRoot, 'D:\DNIeToolchain\WindowsKits\10', (Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
    foreach ($base in $roots) {
        $found = Get-ChildItem -LiteralPath (Join-Path $base 'bin') -Filter $name -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '[\\/]x64[\\/]' } | Sort-Object FullName -Descending | Select-Object -First 1
        if ($found) { return $found.FullName }
        if (-not $found) {
            $found = Get-ChildItem -LiteralPath (Join-Path $base 'Tools') -Filter $name -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -match '[\\/]x64[\\/]' } | Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }
    return $null
}
function Run-Tool([string]$path, [string[]]$toolArguments, [int[]]$accepted = @(0)) {
    & $path @toolArguments
    if ($LASTEXITCODE -notin $accepted) { throw ('Herramienta {0}: codigo {1}.' -f (Split-Path $path -Leaf), $LASTEXITCODE) }
    if ($LASTEXITCODE -eq 1 -or $LASTEXITCODE -eq 3010) { Write-Host 'Windows indica reinicio pendiente; no se reiniciara automaticamente.' }
}
function Get-BridgeNodes {
    Get-PnpDevice -Class SmartCardReader -ErrorAction Stop | Where-Object {
        $ids = (Get-PnpDeviceProperty -InstanceId $_.InstanceId -KeyName DEVPKEY_Device_HardwareIds -ErrorAction SilentlyContinue).Data
        $ids -contains 'Root\DNIeBridgeReader'
    }
}
try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $admin = (New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    Write-Host "Modo: $Mode | Administrador: $admin"
    foreach ($name in @('dotnet.exe','msbuild.exe','cl.exe','signtool.exe','infverif.exe','devcon.exe')) {
        $path = Find-Tool $name
        if ($path) { Write-Host "${name}: $path" } else { Write-Host "${name}: NO ENCONTRADO" }
    }
    if ($Mode -eq 'Compilar') {
        if (-not (Find-Tool 'msbuild.exe') -or -not (Find-Tool 'cl.exe') -or (-not $env:WindowsSdkDir -and -not $env:VCTargetsPath)) {
            throw 'Falta el entorno MSVC/WDK. Abra LaunchBuildEnv.cmd del EWDK o una terminal Developer con WDK y vuelva a ejecutar este BAT. Administrador no sustituye estas herramientas.'
        }
        if (-not (Find-Tool 'infverif.exe')) { throw 'WDK pendiente. No se ejecutara compilacion hasta completar su instalacion en D:.' }
        $dotnet = Find-Tool 'dotnet.exe'
        if (-not $dotnet) { throw 'Falta .NET SDK 8 para compilar el backend.' }
        Run-Tool $dotnet @('build', (Join-Path $repo 'tools/windows-bridge/tests/WindowsBridge.Tests.csproj'), '-c', 'Release', '--verbosity', 'quiet')
        Run-Tool $dotnet @((Join-Path $repo 'tools/windows-bridge/tests/bin/Release/net8.0/WindowsBridge.Tests.dll'))
        & (Join-Path $PSScriptRoot 'build.ps1') -Configuration Release
        Write-Host 'Compilacion terminada. El controlador requiere validacion WDK y un paquete firmado antes de instalarse.'
    }
    elseif ($Mode -eq 'Instalar') {
        if (-not $admin) { throw 'La instalacion PnP necesita ejecutar este BAT como administrador.' }
        if (-not $PackageDirectory) { throw 'Indique la carpeta del paquete firmado.' }
        $package = (Resolve-Path -LiteralPath $PackageDirectory).Path
        if (-not (Test-Path -LiteralPath $package -PathType Container)) { throw 'El paquete debe ser una carpeta.' }
        $inf = Join-Path $package 'DniBridgeReader.inf'
        $dll = Join-Path $package 'DniBridgeReader.dll'
        $cat = Join-Path $package 'DniBridgeReader.cat'
        foreach ($file in @($inf,$dll,$cat)) { if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Falta archivo del paquete: $(Split-Path $file -Leaf). No se instalo nada." } }
        # Reject arbitrary INF files, security grants and dependencies: only our reviewed INF.
        $expected = Get-PackageFileHash (Join-Path $PSScriptRoot 'DniBridgeReader.inf')
        if ((Get-PackageFileHash $inf) -ne $expected) {
            throw 'El INF no coincide con el revisado en esta rama. Revisar cambios de empaquetado antes de instalar.'
        }
        $signTool = Find-Tool 'signtool.exe'; $infVerif = Find-Tool 'infverif.exe'; $devcon = Find-Tool 'devcon.exe'
        if (-not $signTool -or -not $infVerif -or -not $devcon) { throw 'Faltan SignTool, InfVerif o DevCon del WDK. No se instalo nada.' }
        foreach ($tool in @($signTool,$infVerif,$devcon)) {
            $signature = Get-AuthenticodeSignature -LiteralPath $tool
            if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch '(^|,\s*)O=Microsoft Corporation(,|$)') {
                throw 'Una herramienta WDK no tiene firma valida de Microsoft. No se instalo nada.'
            }
        }
        if ((Get-AuthenticodeSignature -LiteralPath $cat).Status -ne 'Valid') { throw 'El catalogo no tiene firma confiable. No se instalo nada.' }
        Run-Tool $infVerif @('/w', $inf)
        # This reviewed INF installs only a UMDF DLL and the inbox WUDFRd reflector.
        # Validate Authenticode trust and actual catalog membership, not kernel policy.
        Run-Tool $signTool @('verify', '/pa', '/c', $cat, $inf)
        Run-Tool $signTool @('verify', '/pa', '/c', $cat, $dll)
        $existing = @(Get-BridgeNodes)
        if ($existing.Count -gt 1) { throw 'Hay varios dispositivos DNIeBridgeReader. Resolver duplicados antes de instalar.' }
        if ($existing.Count -eq 1) {
            Run-Tool (Join-Path $env:SystemRoot 'System32/pnputil.exe') @('/add-driver', $inf, '/install') @(0,3010)
        }
        else {
            # Add-driver alone does not create a root-enumerated devnode.
            Run-Tool $devcon @('install', $inf, 'Root\DNIeBridgeReader') @(0,1)
        }
        $reader = @(Get-BridgeNodes)
        if ($reader.Count -ne 1 -or $reader[0].Status -ne 'OK') { throw 'Instalacion solicitada, pero PnP no confirma un lector operativo. Revise este log y setupapi.dev.log.' }
        Write-Host 'Dispositivo PnP presente. Falta comprobar SCardListReaders y el flujo NFC/reset. PnP OK no demuestra compatibilidad con RENIEC.'
    }
    else {
        Write-Host 'Diagnostico sin cambios de sistema. Siguiente paso: lector-pcsc.bat compilar; el BAT carga Build Tools x64 automaticamente.'
    }
    Write-Host "Registro: $logPath"
    Stop-Transcript | Out-Null
    exit 0
}
catch {
    Write-Host "DETENIDO: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Registro: $logPath"
    Stop-Transcript | Out-Null
    exit 1
}
