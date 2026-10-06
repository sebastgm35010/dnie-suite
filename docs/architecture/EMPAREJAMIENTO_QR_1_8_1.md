# Emparejamiento QR 1.8.1

Entrega del 2026-10-05: la PC genera un QR local y el cliente Android lo importa desde la pantalla del puente. El motor Windows conserva independencia del renderizador; la pantalla Android no interpreta JSON ni maneja la conexión TLS directamente.

## Cambios

- `emparejar-pc.bat` inicia `tools/windows-pairing/pair-phone.ps1`. Compilación, caché de paquetes y credenciales temporales se mantienen en D:.
- `tools/windows-bridge/BridgePairingQr.cs` define el sobre `DNIEBRIDGE1:` y su vencimiento. El servidor guarda el vencimiento por separado del certificado TLS y rechaza conexiones nuevas después de caducar.
- `tools/windows-pairing` renderiza mediante QRCoder MIT una página sin recursos externos. Conserva credenciales bajo ACL privada; no imprime el token ni abre puertos en la LAN.
- `dnie-bridge-android/BridgeProvision.kt` valida tamaño, formato, contenido y vencimiento. Los archivos JSON anteriores siguen siendo una alternativa de importación.
- `app/data/bridge/BridgeQrScanner.kt` adapta Google Code Scanner; `BridgeViewModel` reutiliza el caso de importación y la UI solo muestra acciones/estado. No se alteró el flujo QR de FIDO ni el uso del CAN compartido.
- Android pasa a versionCode 34 / `1.8.1-bridge-qr` y conserva la identidad y firma del fork.

## Validación realizada

| Comprobación | Resultado |
| --- | --- |
| Windows bridge, integración TLS y políticas con bytes sintéticos | 52 comprobaciones pasaron. |
| Generador Windows y renderizador separado | Compilación sin errores ni advertencias; launcher ejecutado con `-NoOpen -NoServer`. |
| Imagen QR generada | OpenCV decodificó el PNG y verificó campos contra la configuración privada, sin publicar credenciales. |
| Emparejamiento vencido en servidor | `serve-local` rechazó la configuración caducada antes de abrir el listener. |
| Android | `:dnie-bridge-android:testDebugUnitTest`, `:dnie-bridge-core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` y `:app:exportBranchApk` completados. |
| Separación de capas | `tools/check_architecture.py` pasó. |
| Teléfono real | Sin dispositivo ADB disponible; no se instaló ni se probó el escaneo físico en esta entrega. |

Los tests de QR incluyen formato ajeno, contenido mal formado, tamaño excesivo, vencimiento y vigencia demasiado larga. Las pruebas no demuestran compatibilidad completa del middleware RENIEC: siguen pendientes el reset NFC/PACE y la autorización del backend PC/SC. El launcher inicia el transporte del usuario, no un servicio SYSTEM ni el relay nativo automático.

El intento de eliminar la carpeta privada de credenciales usada en la prueba fue bloqueado por la política de ejecución. Su token tiene vencimiento y los archivos permanecen privados en D:, fuera del repositorio.

Instrucciones de uso en [windows-pairing/README.md](../../tools/windows-pairing/README.md); estado integral en [CONTEXTO_ACTUAL.md](../CONTEXTO_ACTUAL.md).
