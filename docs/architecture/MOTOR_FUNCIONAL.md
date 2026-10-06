# Motor funcional: responsabilidades y puntos de entrada

La referencia de comportamiento es checkpoint/firma-windows-1.8.14. Firma Windows validada en RESULTADO_ANDROID21_FIRMA_PERU_VALIDADA.md. Esta guia describe codigo propio; no incorpora implementaciones propietarias inspeccionadas.

| Responsabilidad | Propietario / entrada |
| --- | --- |
| APDU y criptografia compartida | dnie-core |
| Secuencia IAS/PKI y firma | dnie-protocol: V3PkiSession, DnieTransceiver |
| PACE/SM fisico | dnie-protocol: PaceHandler, PaceSecureChannel, PaceBridgeChannel |
| Traduccion PACE del PC | dnie-protocol: PaceTranslationEndpoint, TerminalSecureMessaging |
| Fragmentos para PC | dnie-protocol: PcResponseFragments |
| Ruta publica de certificados | dnie-protocol: PlainCertificateReadRoute |
| NFC Android | dnie-nfc: IsoDepCardChannel |
| Contrato lector y framing | dnie-bridge-core: BridgeCard, TransparentBridge, VpcdFrames |
| Conexion y ciclo de vida | dnie-bridge-android: BridgeConnection, RestartablePaceCard, TranslatedPaceCard |
| Host Windows | tools/windows-bridge: PcscHostSession, PcscBackend, WindowsPcscDevice |
| Lector virtual | tools/windows-pcsc-driver |
| Presentacion / coordinacion | app/ui/bridge: BridgeViewModel y pantallas |

Los modulos JVM no importan Android/Compose ni UI/data de app. Adaptadores convierten APIs de plataforma en contratos; ViewModels coordinan sesiones y acciones. El dominio no usa ViewModels como servicios. Herramientas host-diagnostics recopilan evidencia, no son dependencia del motor.

## Flujos comprobados

Firma Peru → PC/SC → host Tailscale → BridgeCard → comandos logicos → PACE fisico Android → DNI. En Android21 el PC no abrio PACE propio; consultasPIN y firma recibieron respuestas reales de la tarjeta.

Java RENIEC → selector exacto por proceso → PC/SC → PACE PC terminado en endpoint → comandos logicos → PACE fisico independiente. Android18 completo identidad y certificados; el lanzador aislado no esta integrado con ClickOnce.

## Orden de limpieza

1. Fragmentacion independiente y documentacion vigente compacta.
2. Separar negociacion PACE-PC de encaminamiento y lifecycle, con contratos y pruebas cifradas existentes.
3. Explicitar estados reset/cierre, luego corregir fallos conocidos en commits de comportamiento separados.
4. Separar diagnostico/experimentos de entradas operativas Windows y documentar ejecucion portable.
5. Revisar coordinacion UI sin mover criptografia o APDU a Compose/ViewModels.

No eliminar perfiles o comandos solo por parecer antiguos: pueden formar parte de consumidores distintos. Cambios de PIN, reset y autenticacion requieren evidencia y prueba funcional.
