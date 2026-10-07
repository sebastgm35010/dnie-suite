$ErrorActionPreference='Stop'
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Ejecuta la instalacion USB como administrador.'}
$directory='D:\DNIeToolchain\Dependencies'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$msi=Join-Path $directory 'UsbDk_1.0.22_x64.msi'
if(-not(Test-Path -LiteralPath $msi)){
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest 'https://github.com/daynix/UsbDk/releases/download/v1.00-22/UsbDk_1.0.22_x64.msi' -UseBasicParsing -OutFile $msi
}
if((Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash -ne '91F6F695E1E13C656024E6D3B55620BF08D8835EF05EE0496935BA6BB62466A5'){throw 'Integridad del instalador UsbDk invalida.'}
$signature=Get-AuthenticodeSignature -LiteralPath $msi
if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notlike '*Red Hat*'){throw 'Firma del instalador UsbDk invalida.'}
$setup=Start-Process msiexec.exe -WindowStyle Hidden -ArgumentList @('/i',$msi,'/qn','/norestart','/L*v',(Join-Path $directory 'usbdk-install.log')) -Wait -PassThru
if($setup.ExitCode -notin @(0,3010)){throw 'No se pudo instalar UsbDk. Revisa usbdk-install.log.'}
Write-Host 'Soporte USB instalado. Reconecta el cable y actualiza la lista del gestor.'
if($setup.ExitCode -eq 3010){Write-Host 'Windows requiere reiniciar antes de usar el modo USB.'}
