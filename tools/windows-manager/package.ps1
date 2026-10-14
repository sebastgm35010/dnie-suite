param(
    [string]$BuildRoot='D:/DNIeToolchain/Builds/native-products',
    [string]$OutputRoot='D:/DNIeToolchain/NativePackages',
    [string]$PackageCache='D:/DNIeToolchain/NuGetPackages'
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$build=[IO.Path]::GetFullPath($BuildRoot)
$package=Join-Path ([IO.Path]::GetFullPath($OutputRoot)) ('Release-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $package -Force | Out-Null
$env:NUGET_PACKAGES=$PackageCache
foreach($component in @(@('windows-manager','WindowsManager.csproj'),@('windows-agent','WindowsAgent.csproj'))) {
    $publication=Join-Path $build ('publish-'+$component[0])
    & dotnet.exe publish (Join-Path $repo ('tools/'+$component[0]+'/'+$component[1])) -c Release --self-contained false --artifacts-path $build -o $publication --nologo
    if($LASTEXITCODE -ne 0){throw ('No se pudo compilar '+$component[0])}
    foreach($file in Get-ChildItem -LiteralPath $publication -File | Where-Object {$_.Extension -ne '.pdb'}) {
        $target=Join-Path $package $file.Name
        if(Test-Path -LiteralPath $target) {
            if((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $file.FullName).Hash){throw ('Dependencia incompatible entre productos: '+$file.Name)}
        } else {Copy-Item -LiteralPath $file.FullName -Destination $target}
    }
}
$selector=Join-Path $package 'selector';New-Item -ItemType Directory -Path $selector | Out-Null
foreach($name in @('selector_service.py','selector_engine.py','pcsc-bridge-selector.js','README.md')) {
    Copy-Item -LiteralPath (Join-Path $repo ('tools/windows-reader-selector/'+$name)) -Destination (Join-Path $selector $name)
}
Copy-Item -LiteralPath (Join-Path $repo 'tools/windows-desktop/licenses/QRCoder-LICENSE.txt') -Destination (Join-Path $package 'LICENSE-QRCoder.txt')
$files=@(Get-ChildItem -LiteralPath $package -Recurse -File | ForEach-Object {
    [pscustomobject]@{path=$_.FullName.Substring($package.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
if($files.Count -gt 100){throw 'Paquete fuera de límites del instalador'}
[pscustomobject]@{configuration='Release';files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $package 'distribution.json') -Encoding utf8
Write-Output $package
