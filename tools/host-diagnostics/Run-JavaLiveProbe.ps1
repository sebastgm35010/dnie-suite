param([string]$OutputDirectory = ('D:\DNIeToolchain\JavaLiveProbes\'+[Guid]::NewGuid().ToString('N')), [switch]$WaitForCard, [switch]$ReadersOnly)
$ErrorActionPreference='Stop'
if($WaitForCard -and $ReadersOnly){throw 'Choose waiting probe or readers-only metadata, not both'}
$compiler='C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\javac.exe'
$runtime=Join-Path $env:USERPROFILE '.dcdelivery-2.0\files\customjre\bin\java.exe'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
& $compiler -source 8 -target 8 -d $OutputDirectory (Join-Path $PSScriptRoot 'LiveCardProbe.java')
if($LASTEXITCODE){throw 'Java probe compilation failed'}
$probeArgs=@('-client','-cp',$OutputDirectory,'LiveCardProbe')
if($WaitForCard){$probeArgs+='--wait'}
if($ReadersOnly){$probeArgs+='--readers-only'}
& $runtime @probeArgs | Tee-Object -FilePath (Join-Path $OutputDirectory 'java-probe.jsonl')
if($LASTEXITCODE -eq 2){Write-Output 'Nuestro lector no tiene tarjeta activa; no se intento conectar a otro lector.'}
elseif($LASTEXITCODE){throw 'Java runtime failed'}
Write-Output "Registro: $OutputDirectory"
