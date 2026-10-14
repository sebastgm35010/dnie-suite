# Pantallas, flujos guiados y experiencia NFC R8

Base `a6770b3`, APK83 candidata / APK82 última instalada. Checkpoint previo `checkpoint/pre-flows-r8`; bundle de respaldo en `D:/DNIeToolchain/Checkpoints/pre-flows-r8/repository.bundle`.

2026-10-14 · Pantallas y NFC R8 / APK84 (3.1.0) compilada debug/release, sin instalar ni usar ADB; APK82 sigue siendo la última instalada. Hoja continua credenciales→NFC→progreso→resultado; CAN compacto y PIN transitorio. Firma con preview superior, tres pestañas y barra fija, resultados parciales explícitos. Cinco destinos comunes; Mi DNIe agrupa visualmente Certificados/Identidad, credencial ICAO y detalles plegables. Suite Windows con plantillas 2×2, indicador de actividad y apertura del PDF real. 251 JVM pasan/1 omitida y Manager44 pasan; auxiliar Android compilado, sin ejecutar. Suite preparada y acceso actualizado; Bridge/servicio/driver y motores criptográficos sin cambios. Pruebas Android/NFC pendientes.

## Alcance contrastado

La especificación recibida se conserva en [ESPECIFICACION_R8_RECIBIDA.md](ESPECIFICACION_R8_RECIBIDA.md). Se implementan sus cuatro flujos sobre los estados reales; sus nombres/lineas de ejemplo no son contratos de ejecución.

- La barra auxiliar R7 ya compartía `Screen.items`; el defecto era que esa lista incluía Identidad y omitía PC. Ahora ambas usan Inicio, Mi DNIe, Firmar, Bóveda, PC. Identidad y Lotes conservan rutas y ViewModels; `Screen.primaryFor` selecciona su destino padre y `destinations` admite intents de retorno históricos.
- No se fija PIN de seis dígitos ni se inventan tres intentos: se conserva el rango 4–8 del motor. El mensaje humano avisa del consumo de intentos. CAN guardado y CAN de sesión se distinguen; la vista compacta no guarda por sí sola un CAN que el usuario decidió no recordar.
- El porcentaje del lote Android deriva de eventos reales. Apertura PACE, firma individual y TSA son indeterminados. Windows aún recibe solo el resultado final del controlador: muestra actividad indeterminada y conteo final, sin inventar avances por documento.
- El siglo de la fecha MRZ se interpreta con contexto: nacimientos anteriores/no futuros dentro de 120 años y vencimientos en una ventana de 100 años alrededor de la consulta. Si quedan dos siglos plausibles se conserva año corto y se indica la ambigüedad; fechas inválidas permanecen sin reinterpretar. Una MRZ de dos dígitos no prueba por sí sola el siglo.
- Los controles MRZ requieren lista no vacía y todos correctos; no se da un check si DG1 tiene hash incoherente. Firma EF.SOD íntegra exige bloque disponible y resultado CMS válido. No equivale a hashes correctos de todos los grupos, confianza CSCA/emisor ni autenticidad del chip. Hashes incorrectos se avisan; la rúbrica inconsistente no puede seleccionarse.

## Android: archivos y responsabilidades

- `ui/components/DnieOperationSheet.kt`: hoja única animada, estados credenciales/espera/contacto/completado/error, progreso y cancelación. Pulso gráfico leído en drawing, sin recomponer toda la pantalla a cada cuadro. Error solo permite nueva autorización explícita, nunca reenvío automático.
- `CredentialsDialog.kt`: compatibilidad de llamadas existentes y `CredentialsForm` compartido. CAN compacto/cambiar, foco en PIN; PIN no saveable y borrado al confirmar, cancelar, pausar y salir. `NfcTapPromptSheet.kt` conserva entrada compatible con fases, delegando a la misma hoja.
- `SignerScreen.kt`: un solo LazyColumn vertical para documentos/preview/configuración; no lista vertical anidada. `SignerControls.kt`: tres pestañas y plantillas horizontales. `SignerResultActions.kt`: barra fija, resultados/exportación/compartición a rutas reales. `SigningPresentation.kt`: presentación de resultados completos/parciales y progreso; no realiza criptografía.
- El modal sigue visible en `AwaitingCard` y `Signing` hasta resultado. La firma parcial queda como incidencia y conserva archivos ya firmados; el botón de reintento exige volver a CAN/PIN. La barra permite guardar/compartir y sigue ofreciendo los pendientes. Opciones de documentos ya firmados y copiar solo a pendientes conservan sus guardas en el ViewModel.
- `SignerViewModel` conserva la orquestación: solo añade señal explícita de progreso indeterminado y elección de rúbrica que puede esperar al primer PDF. Seleccionar imagen no confirma propiedad; se mantiene confirmación y validación existentes. Elegir Firmar otro abre el selector: cancelar SAF conserva el resultado anterior.
- `HomeScreen/HomeRoute`: estado de sesión, CAN recordado y cuatro tareas en cuadrícula, resumen y aviso de respaldo. Inventario/SOD solos no se anuncian como identidad personal disponible. Bóveda conserva sus flujos autorizados y formatos.
- `NavGraph/Screen/ActivityNavigation`: cinco destinos uniformes; selector Certificados/Identidad usa rutas independientes. Cambiar pantalla conserva revocación de propietario NFC por Activity. Bridge y Suite siguen actividades con destino PC activo.
- `IdentityScreen/DigitalIdCredentialCard/IdentityPresentation`: foto/datos/rúbrica por bloques, fechas legibles cuando se pueden resolver, detalles técnicos plegables. Se reutiliza decodificador limitado fuera del hilo UI. `IdentityImageExportContract` lleva nombre y MIME juntos en SAF, evitando depender de una recomposición para aplicar el tipo. Se exportan originales (JPEG/JP2/PNG), sin fingir otro formato.
- `CertificatesViewModel.ReadingCard.engaged` distingue espera del contacto real; `CertificatesScreen` usa la misma hoja continua y muestra resultado del escaneo combinado. El lector de ICAO y la secuencia PKI permanecen separados.

## Windows

`SuiteWindow` presenta cuatro plantillas seleccionables en cuadrícula, comparadas mediante `SuiteTemplateSelection` por apariencia completa: página, motivo/lugar y solicitud TSA no cambian la selección, pero un diseño personalizado no se anuncia como plantilla idéntica. El pie indica actividad y permite abrir la carpeta o el PDF seleccionado devuelto por el motor. Apertura se delega fuera del EDT; se mantienen doble clic y menú contextual. Los estados del documento usan texto sin glifos que puedan faltar en la fuente.

Se prepara distribución nueva y acceso directo de usuario. No se reinstala Bridge, no se reemplaza servicio, controlador, identidad ni certificados; no se termina una sesión de usuario para entregar la interfaz.

## Verificación y límites

- 252 casos JUnit en 64 suites: 251 pasan, 0 fallos/errores, 1 omitido por privilegios de enlace de directorio Windows (`RetiredCredentialDataTest`). Base R7 conservada; 10 casos nuevos para parciales/duplicados, progreso, fechas/controles, plantillas, indicador y apertura del resultado fuera del EDT.
- Manager Release: 44 comprobaciones, 0 llamadas al servicio y 0 operaciones de tarjeta. Fuentes nativas intactas.
- Compilación offline de APK84 debug/release y auxiliar instrumentado; mismo identificador y firmante que APK83. Producción comprobada sin consola/diagnóstico/exportador debug/cotejador retirado.
- Cuatro casos instrumentados R8 nuevos compilados: continuidad de hoja hasta fin, CAN compacto/PIN, reautorización explícita y barra fija sobre PDF sintético. Los tests de navegación anteriores se adaptan a los destinos actuales. **No ejecutados: no se usó ADB, no se instaló ninguna APK ni auxiliar.**
- La captura Windows usa documentos/resultados sintéticos de interfaz, sin DNI. Las pruebas PDF/CMS existentes pasan, pero esto no acredita una firma física nueva ni validación externa de esta interfaz.
- PACE, Secure Messaging, CMS/ESSCertIDv2, TSA, TSL/OCSP/CRL, lectores, protocolos de red, bóveda, Bridge y controlador no tienen cambios de fuente. La comprobación Git contra el checkpoint se registra en resultados JSON.

## Entrega y evidencia

```text
D:/DNIeToolchain/AndroidPackages/
  dnie-evolution-3.1.0-v83-personalization-r7-debug.apk
  dnie-evolution-3.1.0-v84-flows-r8-debug.apk
D:/DNIeToolchain/Checkpoints/pre-flows-r8/
  repository.bundle
  dnie-evolution-3.1.0-v82-network-r6-debug.apk
D:/DNIeToolchain/Builds/flows-r8/
  modules/app/outputs/apk/{debug,release,androidTest}/
  signing-ui/install/dnie-signing-ui/
  signing-desktop/install/dnie-signing-desktop/
  build-package-final.log
  jvm-results.json
  manager-tests.log
G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-flujos-r8-20261014/
  INFORME_LOCAL_R8.md
  resultados.json
  GIT_CHECKPOINTS.json
  SHA256SUMS.json
  apk/dnie-evolution-3.1.0-v84-flows-r8-debug.apk
  evidencias/
    jvm-results.json
    junit-results.zip
    manager-tests.log
    suite-flujos-r8.png
    build-package-final.log
```

## Pendiente cuando el usuario habilite ADB

Instalación/arranque, cuatro casos instrumentados nuevos y navegación previa. Verificar tamaños/teclado/tema en el teléfono real, hoja visible durante PIN→PACE→TSA→resultado, cancelación y lote parcial conservado; firma PAdES-T con validador externo, selección/confirmación DG7 y guardado MIME, háptica percibida y rendimiento al escribir/desplazar. Regresión física Bridge/Suite/USB, cambio de red y suspensión del driver siguen pendientes. No solicitar estos pasos ahora.
