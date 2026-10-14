# Red coordinada R6 · contexto de implementación

2026-10-14, America/Bogota. Usuario autoriza R6 y pide coordinación entre equipos sin cambiar IP manualmente. Base APK81 / 3.1.0, checkpoint `checkpoint/pre-network-r6`. Referencia recibida: `D:/Winodws files/Descargas/especificacion_r6_unificacion_red_y_simplificacion_qr.md`; se corrige su referencia a APK80.

## Contrato

Bridge presenta Red (LAN/Tailscale) y USB. Red usa siempre TLS fijado por huella + token persistente. La identidad no es una IP. La vinculación inicial requiere QR/TXT confirmado; los emparejamientos LAN existentes se conservan. Tailscale anterior sin TLS permanece solo como compatibilidad backend, sin seleccionarlo como modo nuevo.

Descubrimiento previo a TLS mediante datagramas acotados autenticados por HMAC-SHA256 con token de emparejamiento. No transmite token, PIN/CAN, APDU, certificados personales ni documentos. Consulta incluye servicio, huella PC y nonce aleatorio de 32 bytes; respuesta autentica el nonce y direcciones candidatas. La respuesta solo descubre una ruta; TLS/huella/token siguen siendo obligatorios antes de cualquier operación de tarjeta. No da alta nuevas identidades.

Android publica ofertas solo mientras el usuario prepara la conexión. Bridge y Suite tienen identidades independientes y comparten un respondedor UDP por proceso, con servicios separados. Windows busca mediante broadcasts de interfaces LAN y consultas unicast a candidatos conocidos por el servicio local Tailscale, sin modificar firewall ni driver. Los destinos resueltos/recibidos se limitan a IPv4 privada/CGNAT; CGNAT no demuestra pertenencia a Tailscale. DNS/historial manual permanecen como ayudas avanzadas si una red bloquea descubrimiento.

La PC elige la ruta vigente al conectar, sin fijar una interfaz anterior. El descubrimiento puede repetirse antes de autenticar o al preparar otra conexión. Una pérdida de TCP/NFC invalida esa sesión; no migra APDU en vuelo ni reenvía firmas. Reconectar recupera la ruta, no reproduce trabajos privados.

## Trabajo autorizado

1. Núcleo JVM de descubrimiento y implementación C# interoperable, con vectores y pruebas de falsificación/replay/cancelación/límites.
2. Integración en Bridge y Suite antes de TLS; vida/cancelación de ofertas independiente de NFC.
3. Dos modos visibles Bridge, direcciones opcionales/historial, ruta automática y estado sin popups en Suite.
4. Retirar QR del sello y acceso al cotejador, migrar preferencias; conservar QR de emparejamiento y valores históricos del modelo.
5. Preview Android con anotaciones existentes, fuera de UI y con límites de recursos; conservar firmas originales.
6. Builds/pruebas, APK82, gestor/servicio y Suite. Conservar identidad, datos y driver2.0.0.4. Actualizar SQL, contexto y evidencias; distinguir pruebas sintéticas y físicas.



## Resultado ejecutado

2026-10-14 · Red R6 / APK82 (3.1.0) instalada: Bridge Red (Wi-Fi/Tailscale) + USB; descubrimiento autenticado y TLS fijado por huella/token, IP opcional en Suite/gestor y CLI. Vista previa Android muestra anotaciones existentes. QR de sello/cotejador retirados de UI; QR de emparejamiento conservado. 283 pruebas JVM pasan, 3 optativas/privilegios omitidas; 261 Bridge + 48 Control + 41 Manager + 3 servicio instalado pasan; 8 instrumentadas Android pasan. Descubrimiento Windows→Android verificado por LAN y Tailscale por separado, credenciales sintéticas, sin DNI. Gestor/servicio/Suite actualizados, identidad conservada y driver sin reinstalar. Firma NFC y cambio físico de red pendientes.

### Capas y rutas

- `dnie-network-core`: formato HMAC, respondedor compartido y cliente UDP JVM puro, sin Android/UI/NFC. `dnie-signing-link` lo expone a Suite; Bridge Android usa la misma oferta encapsulando sus credenciales.
- `app/data/network/AndroidNetworkPresence`: vida de oferta y permiso normal de recepción Wi-Fi. Sin nuevas pantallas de credenciales ni servicio en segundo plano permanente.
- `tools/windows-bridge/NetworkDiscovery.cs`: implementación interoperable .NET; consulta acotada del daemon Tailscale local y broadcasts LAN.
- `ReverseTlsExchange` y `PersistentPcscHost`: descubren ruta antes de TLS. No cambian autorización por lote, presupuesto ni semántica de firmas.
- `AndroidStampPreview`: IO, PDFBox con anotaciones cuando existen, representación nativa en otros casos, entrada 256 MiB/memoria mixta 32 MiB/bitmap máx. 1800 por lado. PDF original sin cambios.

### Comprobaciones verificables

- Compilación offline debug/release + auxiliar Android y ambas distribuciones JVM: `BUILD SUCCESSFUL`, 346 tareas, 3m02s; cierre final por fallback de descubrimiento: 333 tareas, 2m44s.
- XML JUnit: 286 casos en 78 suites, 283 pasan/0 fallos/0 errores/3 omitidos. Omitidos: enlace de directorio Windows sin privilegio; fixtures opcionales `CrossLayerInteropTest` y `WindowsInteropTest`. No se presenta esto como integración PCSC/navegador nueva.
- Vectores Python/JVM/.NET idénticos; rechazo de adulteración byte a byte, nonce antiguo, servicio incorrecto, token incorrecto, destino público y tamaños inválidos.
- Windows: 261 Bridge, 48 Control, 41 presentación Manager (0 llamadas de servicio/tarjeta), 3 inicio/parada del servicio instalado con IP vacías. Producción rechaza entradas de diagnóstico probadas.
- Android físico: 8 instrumentadas pasan (11,51 s); preview real del adaptador conserva widget sintético, QR histórico decodificable y CMS verificado. Prueba QR solo asegura representación histórica; la opción nueva de generar/cotejar QR ya no aparece. Bridge/Suite muestran su sesión independiente.
- Dos ventanas instrumentadas de 40 s con oferta sintética y 0 operaciones de tarjeta. LAN aislada descubre por broadcast, Tailscale aislado por peers del daemon, sin IP manual. Son pruebas de descubrimiento HMAC; no sustituyen firma física ni handshake TLS real nuevo de Suite/Bridge.
- APK82: mismo certificado de paquete que81, versión3.1.0, reemplazo ADB sin borrar datos. Auxiliar retirado, app arrancada con PID observado. Se conservan81+82 en AndroidPackages;80 movida a checkpoint D.
- Release Android sin diagnóstico ni cotejador retirado; paquete nativo sin PDB. Arquitectura y diff whitespace pasan.
- Baseline Aether: 136 archivos comparados, seis cambios esperados de transporte/build/tests/aplicación Suite. Núcleos `dnie-core`, `dnie-protocol`, autorización de firma, CMS/TSA y driver sin modificaciones. Los motores PDF solo omiten el digest adicional del QR cuando no se pide QR; callback de firma/verificación/TSA intacto.
- Instalación conserva hashes de `identity.json` y `bridge.pfx` (2 archivos), coincide con binarios del paquete. Servicio Running, estado final de puente Stopped, ReaderPublished true, CardPresent false. Driver2.0.0.4 sin reinstalación; PnP lector propio OK. No se hizo comparación binaria runtime del driver (el filtro inicial no encontró su DLL); la evidencia acredita fuentes intactas y cero comandos de instalación.

### Distribución local

```text
D:/DNIeToolchain/AndroidPackages/
  dnie-evolution-3.1.0-v81-qr-r5-debug.apk
  dnie-evolution-3.1.0-v82-network-r6-debug.apk
D:/DNIeToolchain/Builds/network-r6/
  signing-ui/install/dnie-signing-ui/
  signing-desktop/install/dnie-signing-desktop/
  native-package/Release-72df1c07ae5d4389aa1a41f7b7d8d571/
D:/DNIeBridgeApp/versions/525934d77e6648499b6df22ad95dce1c/
  DNIeBridge.exe
G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-red-r6-20261014/
  resultados.json
  evidencias/
  SHA256SUMS.json
```

Accesos directos Suite y Bridge actualizados. Consola admite `suite --workspace <dir> --identity <dpapi>` sin `--pc` ni `--phone`; formato anterior se conserva. Gestor Red no requiere IP. Usar PC guardada en Android; para antigua vinculación Tailscale sin TLS, importar una vez QR/TXT nuevo confirmado de la misma PC. Nunca se confía una identidad solo porque se descubra en red.

### Límites y siguiente prueba

Descubrimiento depende de conectividad/ACL existentes: UDP39445 y TCP39444 Bridge o39446 Suite. No se modificó firewall, ACL Tailscale ni SCardSvr. Si la red bloquea broadcast o UDP, puede usarse una dirección/MagicDNS como ayuda avanzada. Si UDP39445 ya está ocupado, se conserva el listener TLS y esa alternativa; `DiscoveryOptionalTest` comprueba escucha/cancelación sin credenciales reales. Un fallo de bind cierra su socket incompleto. Solo IPv4 privada/CGNAT, sin soporte IPv6 nuevo. CGNAT por sí solo no acredita Tailscale.

Pendiente iniciar conexión real guardada, acercar DNI y firmar con Suite y puente por LAN/Tailscale; comprobar cable USB y cambio real de IP entre sesiones. No se promete migración de conexión TCP activa. La pérdida de NFC/TCP invalida el canal; un trabajo parcialmente firmado no se reenvía. Suspensión del driver continúa aplazada.

Último ajuste: fallback de descubrimiento compilado debug/release y probado en JVM (1 caso adicional). Se repitieron las 8 instrumentadas sobre la APK82 final: pasan en 11,51 s. El auxiliar inicialmente bloqueado fue aceptado tras desbloquear y retirado al terminar; APK principal reinstalada sin borrar datos. Cinco pruebas del registro SQL pasan.
