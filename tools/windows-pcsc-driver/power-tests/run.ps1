param([string]$OutputDirectory='D:\DNIeToolchain\ReaderPowerTests')
$ErrorActionPreference='Stop'
$compiler=Get-Command cl.exe -ErrorAction SilentlyContinue
if(-not $compiler) { throw 'Enter the x64 VS/WDK build environment first; no driver installation is performed.' }
$output=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output | Out-Null
& $compiler.Source /nologo /std:c++17 /EHsc /W4 /I $PSScriptRoot /I (Join-Path $PSScriptRoot '..') (Join-Path $PSScriptRoot 'PowerLifecycleTests.cpp') "/Fe:$output\PowerLifecycleTests.exe" "/Fo:$output\PowerLifecycleTests.obj"
if($LASTEXITCODE) { throw "Driver callback tests compilation failed: $LASTEXITCODE" }
& (Join-Path $output 'PowerLifecycleTests.exe')
if($LASTEXITCODE) { throw "Driver callback tests failed: $LASTEXITCODE" }
