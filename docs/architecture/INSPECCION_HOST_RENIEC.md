# Inspección del componente oficial y middleware RENIEC

Actualización de estado: la instalación elevada posterior del 2026-10-05 terminó con exit 0. IDplug, minidrivers y perfiles Calais están instalados; ver [IDPLUG_INSTALADO_Y_PRUEBAS_FIRMADORES.md](IDPLUG_INSTALADO_Y_PRUEBAS_FIRMADORES.md). El resultado fallido siguiente conserva el registro histórico de la primera ejecución, no el inventario vigente.

Fecha: 2026-10-05. Alcance autorizado: descarga/ejecución del componente oficial Ciudadano Digital y, si corresponde, instalación del middleware oficial en este Windows. No se utilizaron DNI personales, tarjetas, CAN, PIN, tokens ni operaciones de emisión/renovación/cambio de PIN. No se cambiaron DLL oficiales, protocolos de lanzamiento, políticas UAC ni drivers sin firma.

## Resultado real

**Se descargó y ejecutó el instalador oficial del middleware IDplug 4.6.1. La instalación terminó con código 1603 y Error 1925: privilegios insuficientes para instalar para todos los usuarios. No quedó un producto IDplug registrado ni las DLL/entradas de tarjeta verificadas instaladas.** El host no estaba elevado y la ejecución silenciosa no pudo pedir credenciales. No se intentó eludir ese requisito.

El componente Ciudadano Digital es una aplicación distinta. La captura pública oficial identifica `cddnie-app.application` y editor Registro Nacional de Identificación y Estado Civil (ClickOnce). El JS devuelve su enlace mediante parámetros de una sesión autorizada. La consulta sin credenciales de `/parametros/url-validacion` respondió 401; una ruta bajo el mismo sitio con el nombre de la captura tampoco entregó un manifest público. **No se descargó ni ejecutó ese componente**, porque no se obtuvo un enlace de descarga público exacto. No se fabricó una sesión ni un token para conseguirlo. [Guía/código público](https://pki.reniec.gob.pe/ciudadanodigital/main-AU6SY7YH.js), [captura oficial](https://pki.reniec.gob.pe/ciudadanodigital/assets/img/app-validacion-dnie.png).

## Inventario previo y comprobación posterior

| Elemento | Antes | Después |
| --- | --- | --- |
| Token de proceso Windows elevado | No | No se solicitó elevación |
| Plataforma Firma Perú | 1.1.0.2, editor PCM | Sigue registrada con esa versión |
| ReFirma PCX 1.6 | 1.6.0.10, editor RENIEC | Sigue registrada con esa versión |
| Producto IDplug/IDEMIA/Ciudadano en uninstall HKLM/HKCU | No encontrado | No encontrado |
| Servicio SCardSvr | Running | Running |
| Procesos IDplug/IdentityManager/Ciudadano | No encontrados | No encontrados |
| Registro Calais `Peru DNIe Device` / `Peru DNIe Contactless Device` | No inventariado de forma individual antes | No existe en vistas verificadas |
| `HKCU\Software\IDEMIA\IDplug` / App Paths `idplug-ui.exe` | No inventariado de forma individual antes | No existe |
| `PeruMinidriver64.dll` en System32 | No inventariado individualmente antes | No existe |

No se cerraron procesos ajenos, no se desinstaló software existente y no hubo reinicio. El log muestra fallo en `InstallFinalize`, no una instalación exitosa. Windows Installer evaluó acciones de archivos y minidriver, por lo que no se afirma que nunca intentó cambiar el sistema; las verificaciones anteriores delimitan lo que se pudo comprobar tras el fallo. `CertPropSvc.Start` vale 2 después; no se capturó su valor anterior y no se atribuye causalidad. Logs y cachés normales de Windows Installer pueden haber sido creados.

No se detectaron listeners pertenecientes a procesos IDplug/Ciudadano, porque esos procesos no estaban activos. No se infiere de ello que el software carezca de endpoints locales una vez instalado.

## Origen, identidad y firma del paquete

Enlace obtenido del HTML oficial de [DNI electrónico RENIEC](https://identidad.reniec.gob.pe/dni-electronico):

[IDplug Classic 4.6.1 Perú 64 bits](https://identidad.reniec.gob.pe/documents/d/guest/idplug-classic-4-6-1-peru-es-64bits).

La descarga GET devolvió 200, `application/x-ms-installer` y `Content-Disposition` con nombre MSI. Un HEAD separado fue rechazado con 555 por la protección del sitio; no se intentó evadirla y la descarga GET ya autorizada terminó correctamente. La guía PDF previa ilustra 4.4.2, mientras que el enlace vigente ofrece 4.6.1. No se asumió que eran la misma versión.

| Dato | Valor |
| --- | --- |
| Archivo | `idplug-classic-4.6.1-Peru-es-64bits.msi` |
| Bytes | 105041920 |
| SHA-256 | `05DF8E88D17A424C0B7C435E30CD13F28EC1379A7DCFD25C2428E0F754EAEDCE` |
| Authenticode antes de ejecutar | `Valid`, Signature verified |
| Firmante | IDEMIA IDENTITY & SECURITY FRANCE SAS |
| Emisor firmante | GlobalSign GCC R45 EV CodeSigning CA 2020 |
| Huella certificado | `3416F052CA88D8CDF534A7482B9D8E60CBB2937A` |
| Timestamp signer | Globalsign TSA for Advanced - G4 |
| ProductName MSI | IN Groupe IDplug Classic 4.6.1 (64-bit) |
| ProductVersion MSI | 4.6.1.475 |
| Manufacturer MSI | IN Groupe |
| ProductCode | `{257242EA-BC50-4977-B91D-E3D8F18B5D9D}` |
| Política declarada | `ALLUSERS=1`, `REBOOT=Force` |

Se verificó firma con `Get-AuthenticodeSignature` y hash con `Get-FileHash`. La firma válida acredita integridad/editor según Windows, no prueba de compatibilidad con nuestro puente ni una auditoría funcional.

Ubicación privada, confirmada ignorada por Git:

`G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose-evolution\build\research\reniec-host\idplug-classic-4.6.1-Peru-es-64bits.msi`

Log detallado privado en la misma carpeta: `idplug-install-private.log`. No incluirlo en Git: puede contener identidad/rutas del equipo. En esta documentación solo se conservan resultados sanitizados.

## Ejecución y límite de privilegios

Se usó `Start-Process msiexec.exe -WindowStyle Hidden`, sin `-Verb RunAs`, con parámetros:

```text
/i "<ruta MSI>" /qn /norestart REBOOT=ReallySuppress /L*v "<log privado>"
```

`/norestart` y `REBOOT=ReallySuppress` se incluyeron para neutralizar el reinicio forzado declarado por el MSI. [Opciones oficiales de msiexec](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/msiexec).

Resultado: exit 1603, Error 1925. El log indica que el nivel de UI silencioso impide solicitar elevación y que `AlwaysInstallElevated` es 0. No se alteró esa política. La continuación concreta es ejecutar el paquete desde una consola Windows ya elevada por el usuario; no hace falta otra descarga.

Comando reproducible en **PowerShell como administrador**, después de volver a comprobar hash/firma:

```powershell
$reniecMsi = 'G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose-evolution\build\research\reniec-host\idplug-classic-4.6.1-Peru-es-64bits.msi'
Get-FileHash -LiteralPath $reniecMsi -Algorithm SHA256
Get-AuthenticodeSignature -LiteralPath $reniecMsi | Select-Object Status, SignerCertificate
$reniecInstallLog = Join-Path $env:TEMP 'reniec-idplug-4.6.1-install.log'
$reniecArgs = '/i "' + $reniecMsi + '" /qn /norestart REBOOT=ReallySuppress /L*v "' + $reniecInstallLog + '"'
$reniecInstall = Start-Process "$env:WINDIR\System32\msiexec.exe" -ArgumentList $reniecArgs -WindowStyle Hidden -Wait -PassThru
$reniecInstall.ExitCode
```

Aceptar como resultado instalación 0, o 3010 con reinicio pendiente; no reiniciar automáticamente. Otro código requiere revisar el log y verificar los componentes reales. Este comando todavía **no se ejecutó elevado** en este trabajo.

## Inspección estática del MSI: interfaces relevantes

Se abrió el MSI en modo lectura mediante `WindowsInstaller.Installer.OpenDatabase(..., 0)`. Se consultaron tablas Property, File, Registry, CustomAction y Media. No se invocaron funciones criptográficas del middleware ni se cargaron DLL para operar tarjetas. La tabla identifica un cabinet embebido `IDplugClassic.cab`.

| Archivo declarado | Arquitecturas / versión | Interpretación acotada |
| --- | --- | --- |
| `idplug-pkcs11.dll` | 32 y 64 bits, 2.8.3.475 | Candidato para consumidores PKCS#11; también hay variantes legacy |
| `idplug-eidapi.dll` | 32 y 64 bits, 2.1.0.475 | API eID propia, contrato/exportaciones no inspeccionados |
| `idplug-mocapi.dll` | 32 y 64 bits, 2.1.0.475 | API MoC propia; fuera de la necesidad inicial del puente |
| `PeruMinidriver.dll`, `PeruMinidriver64.dll` | 1.8.0.459 | Binding del minidriver Perú para Windows |
| `idplug-peru-minidriver.dll`, `idplug-peru-minidriver64.dll` | 1.8.0.459 | Implementación/dependencia acompañante, según nombres del paquete |
| `PeruMinidriver.inf`, `PeruMinidriver.cat` | Declarados | Instalación/identidad de driver; no se verificó una instalación efectiva |
| `idplug-ui.exe` | 1.9.4.475 | UI con entrada Run/App Paths declarada |

No se verificaron todavía exports, imports PE ni sockets en ejecución. La presencia/nombre de DLL es evidencia de superficie de integración, no una prueba de función disponible o implementación conforme.

### Contacto y contactless declarados

La tabla Registry tiene registros destinados a HKLM bajo:

`SOFTWARE\Microsoft\Cryptography\Calais\SmartCards`.

| Perfil declarado | ATR | ATRMask |
| --- | --- | --- |
| Peru DNIe Device | `3BDF96008131FE4580738421E05569785400018300900000` | `FFFFFF00FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00FFFF00` |
| Peru DNIe Contactless Device | `3B8F800180738421E0556978540001830F900000` | `FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00` |

Ambos perfiles declaran:

- `80000001` → `PeruMinidriver64.dll` o `PeruMinidriver.dll` según arquitectura.
- `Crypto Provider` → `Microsoft Base Smart Card Crypto Provider`.
- `Smart Card Key Storage Provider` → `Microsoft Smart Card Key Storage Provider`.

Son valores **del paquete**, no valores registrados exitosamente en este host. No se escribieron estos datos ni se alteró un ATR para simular compatibilidad. El paquete pretende enlazar perfiles tanto de contacto como contactless al minidriver y APIs CAPI/CNG. Hay que comprobar cuál presenta un lector NFC PC/SC real y qué secuencia PACE/APDU espera ese minidriver.

Las CustomAction incluyen instalación de minidriver Perú por `rundll32`/helper e INF. La instalación exige permisos globales; forzar `ALLUSERS` a usuario o copiar DLL manualmente no representa una instalación soportada y no se hizo.

## Implicación para 2.0 y siguiente prueba

El soporte contactless declarado permite priorizar una prueba **lector PCSC virtual con perfil contactless → middleware oficial → consumidor Windows**, frente a desarrollar inmediatamente un CSP/KSP propio. Debe preservarse la separación entre relé físico (PACE en PC) y terminación PACE Android: si el middleware oficial establece PACE, no puede recibir otro canal ya protegido como si fuera tarjeta cruda.

Después de instalar correctamente con privilegios adecuados:

1. Inventariar rutas reales/firma de DLL y registro del minidriver, versiones y arquitectura; enumerar lectores sin escribir en tarjetas.
2. Identificar qué API importa el componente `cddnie-app` cuando se disponga del enlace legítimo; no asumir que usa PKCS#11 porque el middleware incluya esa DLL.
3. Harness PCSC virtual y tarjeta simulada para estados/transacciones/ATR documentados, sin presentar DNI personal ni efectuar renovación.
4. Prueba autorizada con DNI/NFC real, logs sin CAN/PIN/APDU sensibles; determinar dueño PACE y compatibilidad efectiva antes de declarar el hito2.0.

La instalación bloqueada y la falta de enlace ClickOnce exacto son pendientes concretos; no invalidan los hallazgos estáticos y tampoco equivalen a un puente funcionando.
