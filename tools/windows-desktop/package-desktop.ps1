param([ValidateSet('Debug','Release')][string]$Configuration='Release',
    [string]$OutputRoot='D:\DNIeToolchain\DesktopPackages')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$package=Join-Path $OutputRoot ($Configuration+'-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $package -Force | Out-Null
$runtime=Join-Path $package 'runtime'
& dotnet.exe build (Join-Path $repo 'tools/windows-pairing/WindowsPairing.csproj') -c $Configuration -o $runtime --configfile (Join-Path $repo 'tools/windows-pairing/NuGet.Config') -p:ArtifactsPath=D:/DNIeToolchain/BuildArtifacts/desktop
if($LASTEXITCODE){throw 'No se pudo compilar el host y el generador QR.'}
$desktop=Join-Path $package 'tools/windows-desktop'
$pairing=Join-Path $package 'tools/windows-pairing'
$selector=Join-Path $package 'tools/windows-reader-selector'
foreach($directory in @($desktop,$pairing,$selector)){New-Item -ItemType Directory -Path $directory -Force | Out-Null}
foreach($name in @('BridgeDesktop.ps1','BridgeDesktopController.ps1','BridgeNetworkController.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $desktop $name)}
foreach($name in @('network-session.ps1','start-pcsc-session.ps1','start-tailnet-host.ps1','install-usb-support.ps1')){Copy-Item -LiteralPath (Join-Path $repo "tools/windows-pairing/$name") -Destination (Join-Path $pairing $name)}
foreach($name in @('selector_service.py','selector_engine.py','pcsc-bridge-selector.js','README.md')){Copy-Item -LiteralPath (Join-Path $repo "tools/windows-reader-selector/$name") -Destination (Join-Path $selector $name)}
$licenses=Join-Path $package 'licenses'
New-Item -ItemType Directory -Path $licenses | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'licenses/QRCoder-LICENSE.txt') -Destination $licenses
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'licenses/UsbDk-LICENSE.txt') -Destination $licenses
$assets=Get-Content 'D:/DNIeToolchain/BuildArtifacts/desktop/obj/WindowsPairing/project.assets.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$packageCache=$assets.packageFolders.PSObject.Properties | Select-Object -First 1 -ExpandProperty Name
foreach($dependency in @('System.Drawing.Common/8.0.31','Microsoft.Win32.SystemEvents/8.0.0')) {
    $folder=Join-Path $packageCache $dependency.ToLowerInvariant()
    foreach($notice in @('LICENSE.TXT','THIRD-PARTY-NOTICES.TXT')) {
        Copy-Item -LiteralPath (Join-Path $folder $notice) -Destination (Join-Path $licenses ($dependency.Replace('/','-')+'-'+$notice))
    }
}
if($Configuration -eq 'Debug') {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'debug') -Destination (Join-Path $desktop 'debug') -Recurse
    $diagnostics=Join-Path $package 'tools/host-diagnostics'
    New-Item -ItemType Directory -Path $diagnostics | Out-Null
    Get-ChildItem (Join-Path $repo 'tools/host-diagnostics') -File | Where-Object Extension -In @('.ps1','.py','.mjs','.java','.js') | Copy-Item -Destination $diagnostics
} else {
    # No debug entry loader, diagnostic module, PDB or command switches in production package.
    $path=Join-Path $desktop 'BridgeDesktop.ps1'
    $source=[IO.File]::ReadAllText($path,[Text.Encoding]::UTF8)
    $begin=$source.IndexOf('# Production packages contain neither')
    $end=$source.IndexOf('if ($SelfTest) {',$begin)
    if($begin -lt 0 -or $end -lt 0){throw 'No se pudo separar la entrada debug del gestor.'}
    $source=$source.Remove($begin,$end-$begin)
    $tokens=$null;$parseErrors=$null
    $ast=[Management.Automation.Language.Parser]::ParseInput($source,[ref]$tokens,[ref]$parseErrors)
    if($parseErrors){throw 'Error de sintaxis antes de separar las pruebas del gestor.'}
    $testBlocks=@($ast.EndBlock.Statements | Where-Object {$_ -is [Management.Automation.Language.IfStatementAst] -and $_.Extent.Text.StartsWith('if ($SelfTest')})
    foreach($block in ($testBlocks | Sort-Object {$_.Extent.StartOffset} -Descending)) {
        $source=$source.Remove($block.Extent.StartOffset,$block.Extent.EndOffset-$block.Extent.StartOffset)
    }
    $source=$source.Replace('param([switch]$CheckOnly,[switch]$SelfTest,[switch]$LayoutTest)','param([switch]$CheckOnly)').Replace('$SelfTest','$false').Replace('$LayoutTest','$false')
    $source=$source.Replace("'sebas-laptop.tail2752e9.ts.net'","''").Replace("'sebas-poco.tail2752e9.ts.net'","''")
    [IO.File]::WriteAllText($path,$source,[Text.UTF8Encoding]::new($false))
    Get-ChildItem -LiteralPath $runtime -Filter '*.pdb' | Remove-Item
    if(Get-ChildItem -LiteralPath $package -Recurse -File | Where-Object {$_.FullName -match 'host-diagnostics|BridgeDebugMenu|pair-phone'}){throw 'Hay herramientas debug en el paquete Release.'}
}
[IO.File]::WriteAllText((Join-Path $package 'ABRIR-GESTOR.bat'),('@echo off'+[Environment]::NewLine+'powershell.exe -NoProfile -WindowStyle Hidden -File "%~dp0tools\windows-desktop\BridgeDesktop.ps1"'),[Text.Encoding]::ASCII)
$manifest=[pscustomobject]@{configuration=$Configuration;createdUtc=[DateTimeOffset]::UtcNow.ToString('O');debugIncluded=($Configuration -eq 'Debug');requires=@('UsbDk x64 para USB directo (opcional)','.NET 8','lector DNIe PC/SC instalado','Python y Frida para selector RENIEC opcional');files=@(Get-ChildItem $package -Recurse -File | ForEach-Object {[pscustomobject]@{path=$_.FullName.Substring($package.Length+1);sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash}})}
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $package 'package-manifest.json') -Encoding UTF8
Compress-Archive -Path (Join-Path $package '*') -DestinationPath ($package+'.zip')
Write-Output $package
