param([ValidateSet('Release','Debug')][string]$Configuration='Release', [string]$SdkVersion,
    [string]$OutputDirectory = 'D:\DNIeToolchain\ReaderBuild')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'package-utils.ps1')
$msbuildCommand = Get-Command msbuild.exe -ErrorAction SilentlyContinue
if (-not $msbuildCommand) {
    throw 'No MSBuild C++/WDK environment. Enter an EWDK LaunchBuildEnv.cmd shell first. This script does not install tools or drivers.'
}
if (-not $env:VCTargetsPath -and -not $env:WindowsSdkDir) {
    throw 'WDK/C++ environment not detected. dotnet MSBuild alone cannot compile this driver.'
}
$output = [IO.Path]::GetFullPath($OutputDirectory).TrimEnd('\') + '\'
New-Item -ItemType Directory -Path $output -Force | Out-Null
# PDB/link output must be local: Google Drive's virtual filesystem can reject mapping.
$arguments = @((Join-Path $PSScriptRoot 'DniBridgeReader.vcxproj'), '/m', "/p:Configuration=$Configuration", '/p:Platform=x64', '/p:SignMode=Off', '/p:EnableTestSign=false', "/p:OutDir=$output", "/p:IntDir=$($output)obj\")
if ($SdkVersion) { $arguments += "/p:WindowsTargetPlatformVersion=$SdkVersion" }
& $msbuildCommand.Source @arguments
if ($LASTEXITCODE -ne 0) { throw "Driver compilation failed: exit $LASTEXITCODE" }
$package = Join-Path $output 'DniBridgeReader'
foreach ($name in @('DniBridgeReader.dll','DniBridgeReader.inf','dnibridgereader.cat')) {
    if (-not (Test-Path -LiteralPath (Join-Path $package $name) -PathType Leaf)) { throw "Build did not produce $name." }
}
if ((Get-PackageFileHash (Join-Path $PSScriptRoot 'DniBridgeReader.inf')) -ne
    (Get-PackageFileHash (Join-Path $package 'DniBridgeReader.inf'))) { throw 'Packaged INF differs from reviewed source.' }
Write-Output 'Compilation completed. No driver was installed, signed, or registered. Validate DLL/INF and IOCTL behavior before deployment.'
Write-Output "Local driver artifacts: $output"
