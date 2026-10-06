$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$directory = Join-Path 'D:\DNIeToolchain\CrossLayerTests' ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $directory | Out-Null
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $directory /inheritance:r /grant:r "*$($sid):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE) { throw 'No se pudo proteger la carpeta de prueba.' }
Write-Output "Cross-layer output: $directory"
& dotnet build (Join-Path $repo 'tools/windows-pcsc-driver/synthetic-tests/ReaderSynthetic.Tests.csproj') -c Release -o D:/DNIeToolchain/ReaderSynthetic --verbosity quiet
if ($LASTEXITCODE) { throw 'No se pudo compilar la prueba nativa.' }
& "$env:JAVA_HOME\bin\javac.exe" -source 8 -target 8 -d $directory (Join-Path $PSScriptRoot 'ReaderMetadata.java')
if ($LASTEXITCODE) { throw 'No se pudo compilar el inspector Java.' }
$pane = @(tmux capture-pane -p -t 0:0.0 -S -3)
$last = @($pane | Where-Object {$_.Trim().Length -gt 0}) | Select-Object -Last 1
if ($last -notmatch '^PS .*>\s*$') { throw 'La terminal administrativa no esta disponible.' }
$helper = Join-Path $repo 'tools/windows-pcsc-driver/run-synthetic-tests.ps1'
tmux send-keys -t 0:0.0 -l "& '$helper' -CrossLayerDirectory '$directory'"
tmux send-keys -t 0:0.0 Enter
$limit = [DateTime]::UtcNow.AddSeconds(150)
$wrapper = Join-Path $directory 'run-mobile.ps1'
# Only paths and a test id enter the environment. The pairing secret is never a command argument.
$script = "`$env:JAVA_HOME='$env:JAVA_HOME'`n`$env:DNIE_CROSS_LAYER_FIXTURE='$directory'`nSet-Location -LiteralPath '$repo'`n& '.\gradlew.bat' ':dnie-bridge-android:testDebugUnitTest' '--tests' '*CrossLayerInteropTest' '--console=plain'`nexit `$LASTEXITCODE"
Set-Content -LiteralPath $wrapper -Value $script -Encoding UTF8
$runner = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-File',$wrapper) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'gradle.log') -RedirectStandardError (Join-Path $directory 'gradle-error.log')
$edgeProcess = $null
try {
    while (-not (Test-Path -LiteralPath (Join-Path $directory 'browser-ready.json'))) {
        if ($runner.HasExited -or [DateTime]::UtcNow -gt $limit) { throw 'El motor movil no llego a adjuntar la tarjeta sintetica.' }
        Start-Sleep -Milliseconds 500
    }
    & 'C:\Users\LENOVO\.dcdelivery-2.0\files\customjre\bin\java.exe' -client -cp $directory ReaderMetadata | Set-Content (Join-Path $directory 'java-readers.jsonl') -Encoding UTF8
    if ($LASTEXITCODE) { throw 'El inspector no pudo usar el runtime oficial.' }
    $port = 19472
    if (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue) { throw 'Puerto CDP ocupado.' }
    $edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
    $arguments = "--headless=new --remote-debugging-address=127.0.0.1 --remote-debugging-port=$port --user-data-dir=$directory\edge-profile --no-first-run --no-default-browser-check about:blank"
    $edgeProcess = Start-Process -FilePath $edge -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $ready = $false
    for ($i=0;$i -lt 30;$i++) {
        try { $null=Invoke-RestMethod "http://127.0.0.1:$port/json/version" -TimeoutSec 1; $ready=$true; break } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $ready) { throw 'Edge no inicio CDP.' }
    if (Get-NetTCPConnection -LocalPort $port -State Listen | Where-Object {$_.LocalAddress -notin @('127.0.0.1','::1')}) { throw 'CDP fuera de loopback.' }
    $http = (Get-Content (Join-Path $directory 'browser-ready.json') -Raw | ConvertFrom-Json).port
    & node (Join-Path $PSScriptRoot 'cross-layer-browser.mjs') $port "http://127.0.0.1:$http/" (Join-Path $directory 'edge.jsonl')
    if ($LASTEXITCODE) { throw 'Fallo la prueba iniciada por el navegador.' }
    $runner.WaitForExit(15000) | Out-Null
    if (-not $runner.HasExited -or $runner.ExitCode -ne 0) { throw 'Fallo la prueba del motor movil.' }
    $rows = @()
    foreach ($name in @('edge.jsonl','controller.jsonl','mobile-engine.jsonl','java-readers.jsonl')) {
        foreach ($line in Get-Content -LiteralPath (Join-Path $directory $name)) { if ($line.Trim()) { $rows += $line | ConvertFrom-Json } }
    }
    $rows | Sort-Object time | ForEach-Object { $_ | ConvertTo-Json -Depth 8 -Compress } | Set-Content (Join-Path $directory 'timeline.jsonl') -Encoding UTF8
    Write-Output 'PASS: Edge, controlador instalado, TLS y motor Kotlin con NFC/PACE simulados; timeline guardado.'
} finally {
    if ($edgeProcess -and -not $edgeProcess.HasExited) { $edgeProcess.Kill() }
    if (-not $runner.HasExited) { $runner.Kill() }
}
