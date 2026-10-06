# Resultado Android18: lectura RENIEC completa por el puente

Prueba del 6 de octubre de 2026 con Android 1.8.14-reniec-pki (47), traducción PACE en Android, selector Java por proceso y minidriver DNI temporalmente inhabilitado. CertPropSvc y SCardSvr se preservan.

## Evidencia cruzada

- Exportación APDU Android18: sesión `b1271a68-a537-4c5d-ac62-8f39c232ab2a`, 22:11:55.710–22:13:11.931 UTC (17:11:55–17:13:11 Bogotá), 76,221 segundos.
- 60 comandos y 60 respuestas; ningún evento transport_failed. Estados: 37 × 9000, 20 × 6A86 de sondeo, 1 × 6A82 de detección v2 y 2 × 6282 de fin de archivo. Los sondeos rechazados no impidieron completar el flujo.
- Diagnóstico18: PACE físico listo a las 22:11:55.707 UTC; solicitud PACE PC a las 22:12:02.026 y finalización a las 22:12:03.163. Lectura protegida de identidad seguida de ruta plana IAS → PKI → certificados, traducida al canal físico protegido.
- Java original RENIEC, con selección exclusiva del lector puente, completó en aproximadamente 12,235 segundos. selector-result.json registra officialSuccess=true, officialReportedFailure=false y exitCode=0.
- El resultado privado original contiene success:true, los cuatro campos de identidad no vacíos y los campos de certificados cerFir y cerAut. Se comprobó que no se trata de un éxito con lista vacía.
- Se reconstruyeron en memoria las respuestas READ BINARY de CE82 (firma, 2.403 bytes) y CE81 (autenticación, 2.346 bytes). Ambos DER se interpretaron correctamente con cryptography.x509: claves públicas RSA, algoritmo de firma SHA-256 con RSA y dentro de su intervalo de vigencia al analizar. Esto verifica estructura y vigencia, no cadena de confianza ni revocación.
- card_powered_off aparece a las 22:12:19.097. stop_requested a las 22:13:11.923 precede a SocketException a las 22:13:11.946: cierre manual, no evidencia de desconexión espontánea.
- Monitor Windows: session_completed y collectorsStopped=true. Captura y resultado privados conservados en D:, sin copiar datos personales al repositorio.

## Alcance demostrado

La combinación lector PC/SC virtual + Tailscale + traducción PACE Android + selector Java permite al motor original de lectura RENIEC obtener identidad y sus dos certificados reales. La ruta modular de certificados añadida en 1.8.14 resuelve el bloqueo 6985 observado en Android16.

No hubo nueva modificación de código ni compilación en este análisis. La versión ya tenía 185 pruebas aprobadas, 2 omitidas y APK compilado antes del ensayo real.

## Pendientes

1. Integrar la selección del lector en la ejecución real ClickOnce / Ciudadano Digital. Esta prueba usa el JAR original mediante nuestro lanzador; no demuestra todavía que la web complete su flujo.
2. Mantener el aislamiento del minidriver durante las pruebas controladas. Resolver su coexistencia y el reinicio de contexto SSC antes de restaurarlo para uso general. No detener CertPropSvc.
3. Verificar el flujo real con navegador, registrar el resultado del backend y distinguirlo de la lectura local ya conseguida. No asumir validación, firma ni reconocimiento de IDplug a partir de este éxito.
4. Revisar por separado la limpieza Windows que anteriormente registraba Win32 code 5 después del cierre; no atribuirle el cierre manual de este ensayo.

## Fuentes privadas

- D:/Winodws files/Descargas/dnie-apdu-pace-android18.jsonl.json
- D:/Winodws files/Descargas/dnie-diagnostico18.jsonl
- D:/DNIeToolchain/LiveCaptures/tailnet-reader-selector-7f2f892e2ade425aab4a96ce66d27c9c/

No se incluyen CAN, identidad, números de serie ni contenido de certificados en este documento.
