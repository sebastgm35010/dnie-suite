# Alternativas permisivas para el puente Android–PC

Se revisaron repositorios originales y archivos de licencia. No se instaló ninguna dependencia ni se incorporó código ajeno al APK. La búsqueda no encontró un sustituto completo MIT de Remote Smart Card Reader junto con un driver virtual PC/SC Windows listo para distribución.

## Componentes MIT comprobados

| Proyecto | Licencia verificada | Aporte | Límite |
|---|---|---|---|
| [pcsc-rust](https://github.com/bluetech/pcsc-rust) | [MIT](https://github.com/bluetech/pcsc-rust/blob/master/LICENSE-MIT) | bindings PC/SC para Rust, Windows/Linux/macOS; cliente de pruebas y diagnóstico del host | usa un lector existente, no crea un lector virtual |
| [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) | [MIT](https://github.com/TooTallNate/Java-WebSocket/blob/master/LICENSE) | transporte WebSocket/WSS Java para el adaptador Android/JVM | no implementa NFC, PACE ni VPCD; pairing y autorización son nuestros |
| [nfc-pcsc](https://github.com/pokusew/nfc-pcsc) | [MIT](https://github.com/pokusew/nfc-pcsc/blob/master/LICENSE.md) | acceso NFC/APDU desde Node en PC sobre PC/SC | no funciona como motor Android ni driver virtual; revisar bindings nativos y dependencias si se selecciona |

Para nuestro motor Android no es necesario sustituir IsoDep ni PACE: ya contamos con adaptadores y protocolo propios. Para VPCD se propone una implementación propia del [protocolo documentado](https://frankmorgner.github.io/vsmartcard/virtualsmartcard/api.html), sin copiar VPCDWorker/NFCReader GPL.

## Alternativas permisivas que no son MIT

- [pcsc-sharp](https://github.com/danm-de/pcsc-sharp): su [COPYING.txt](https://github.com/danm-de/pcsc-sharp/blob/master/COPYING.txt) contiene términos BSD de dos cláusulas y una condición adicional sobre cambios de licencia. No etiquetarlo MIT ni asumir un SPDX estándar exacto sin revisión. Es wrapper .NET de PC/SC; su README aclara que no contiene drivers.
- [NFCGate](https://github.com/nfcgate/nfcgate): [Apache-2.0](https://github.com/nfcgate/nfcgate/blob/master/LICENSE), con relay y TLS. Sirve de referencia para transporte NFC; no sustituye un lector Windows PC/SC. Algunas funciones requieren Xposed; no necesitamos incorporar captura, clonación ni emulación HCE para nuestro puente.
- [Muestra SmartCard de Microsoft](https://github.com/microsoft/Windows-universal-samples/blob/main/Samples/SmartCard/README.md): administra tarjetas virtuales de Windows y transmite a tarjetas existentes. No presenta una API para convertir cualquier servidor APDU Android en lector PC/SC. No considerarla sustituto de Bix por el nombre "virtual smart card".

## Decisión propuesta

1. Mantener el motor NFC/PACE existente y desarrollar `dnie-bridge-core` y su adaptador VPCD propios.
2. Considerar Java-WebSocket MIT solo si elegimos WSS; TCP/TLS de plataforma puede evitar esa dependencia. Mantener transporte independiente del protocolo de tarjeta.
3. Usar pcsc-rust MIT para pruebas del lado PC si seleccionamos Rust; no añadir Rust solo para reemplazar el pequeño probe WinSCard actual.
4. Resolver aparte el lector virtual Windows. El Bix ya instalado puede seguir como componente externo de laboratorio; no permite afirmar que la solución distribuida es enteramente MIT. Falta una base permisiva de driver, implementación propia o alternativa comercial con términos adecuados, además de firma e instalación de producto.
5. Revisar licencias transitivas y dependencias actuales antes de declarar una licencia global del APK. MIT en el adaptador nuevo no cambia licencias de componentes anteriores.

El uso de un programa GPL independiente no transforma automáticamente otros programas en GPL. Incorporar su implementación a un programa combinado es otra situación; la [FAQ de GNU sobre agregación](https://www.gnu.org/licenses/gpl-faq.en.html#MereAggregation) distingue mecanismo y semántica de la comunicación. No se considera que un socket sea por sí solo una garantía. Para mantener libertad de licencia en el adaptador, no copiar código GPL, conservar procedencia de dependencias elegidas y documentar el componente externo usado en pruebas.
