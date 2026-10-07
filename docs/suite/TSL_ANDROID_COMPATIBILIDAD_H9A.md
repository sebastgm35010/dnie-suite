# Corrección de TSL en Android · H9a

2026-10-07. El usuario probó APK60: revocación titular sin revocación, cadena UNKNOWN, detalle «TSL no autenticada o no disponible». No declarar H9 validado físicamente. Checkpoint previo checkpoint/pre-tsl-android-diagnostics-h9a sobre 7298016, ZIP en D:/DNIeToolchain/Checkpoints/pre-tsl-android-diagnostics-h9a.

## Hallazgo reproducido

La configuración JCA real de Android inserta Bouncy Castle 1.78.1 primero. Repetir prueba JVM con ese orden pasó; esa diferencia no reprodujo el fallo. Se probó luego el DOM de Android 14 (org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl) y la implementación predeterminada de DatatypeFactory, org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl, ausente del runtime Android14 obtenido para la prueba. La autenticación XML llegó hasta metadatos y falló con DatatypeConfigurationException al convertir fechas. Una carga aislada de javax.xml.datatype de ese runtime confirma la implementación predeterminada y el error de newInstance. Las pruebas anteriores bajo Java17 tenían el proveedor Xerces interno de JDK y no cubrieron esta diferencia.

Esto reproduce una incompatibilidad real compatible con el síntoma informado. No se obtuvo log de la excepción del teléfono: ADB no muestra dispositivo. Todavía no se afirma que sea la única causa posible de UNKNOWN del móvil; red/TLS, XML, CRL y almacenamiento ahora se distinguen.

## Corrección modular

- TrustTimestampParser usa SimpleDateFormat/GregorianCalendar propios por llamada, Locale.ROOT, calendario proléptico y parseo no tolerante. Perfil XML de fechas con zona obligatoria, fracciones hasta nueve dígitos a precisión de milisegundos y límites de desplazamiento +/-14h. Rechaza fechas imposibles, falta de zona, offsets inválidos y texto sobrante. No requiere DatatypeFactory ni agrega Xerces al APK.
- TrustValidationFailure identifica etapa fija y clase de excepción; no expone texto de excepción, URL, XML, nombres/series de certificados, documentos ni credenciales. Diferencia ancla, descarga, XML, firmante, digests, firma, fechas, servicios, pin, rollback, CRL y persistencia. LinkageError de compatibilidad en autenticación se convierte en fallo indeterminado; no se ocultan errores de memoria.
- Repositorio Android registra resultado/código seguro en diagnóstico persistente Debug. El panel muestra descripción de la etapa y un código técnico solo con BuildConfig.DEBUG; producción no muestra código técnico y conserva diagnóstico deshabilitado por instalación de sink nulo existente.
- No se relajan firmas, digests, fechas, estados de servicio, CRL, pins o rollback. PACE/SSC/APDU y generación CMS/PDF no cambian.

## Verificación y artefactos

233 pruebas pasan: cuatro nuevas cubren fechas/zona/fracciones, proveedor XML ausente, entradas inválidas y privacidad/preservación de etapas anidadas. Regresión de cadena/CRL/OCSP, Android, PDF y suite pasa. Debug compilado BUILD SUCCESSFUL 2m05s, D:/DNIeToolchain/Temp/trust-android-h9a.log. Release61 no se compiló en esta corrección; Release60 sigue preservado.

Prueba posterior con DOM Android14 + BC prioritario + proveedor DatatypeFactory deliberadamente ausente: TSL autenticada secuencia25 y cadena TRUSTED. Titular GOOD; revocación de CA UNKNOWN en esa ejecución, que se mantiene indeterminada sin convertir falta de evidencia en GOOD. Resultado en D:/DNIeToolchain/CertificateAudit/2026-10-07/h9-engine/android-dom-result-fixed.txt. Esta prueba carga clases Android sobre JVM; no equivale a emulador ART ni teléfono físico.

Runtime de prueba externo a Git/APK: D:/DNIeToolchain/AndroidRuntimeTests/android-all-14-robolectric-10818077.jar, 137.666.235 bytes, Maven org.robolectric:android-all. Se descargó únicamente a D:, sin instalar emulador/SDK adicional. AndroidDatatypeProbe.java y evidencia de introspección se conservan ahí para reproducir incompatibilidad. No se incorpora este runtime como dependencia del producto.

APK61 / 2.0.0-rc13-trust-android:
D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc13-trust-android-debug.apk
SHA256 2B2F1218F60E4BFDCD1BE39A8C7370CC6DE3F9E58F6A25A9354A937EA66A3892.

Solicitado envío único al mensajero Telegram vigente; confirmar entrega por separado. No instalado por ADB. Prueba pendiente: leer DNI -> Firma -> Comprobar confianza y revocación; si UNKNOWN, copiar código de Diagnóstico o exportar registro, sin borrar datos de emparejamiento/bóveda. Preflight automático, validación histórica y TSA siguen pendientes.

## Confirmación física posterior

El usuario confirma en APK61: TSL Perú autenticada secuencia25, ruta PKIX verificada y titular sin revocación en OCSP firmado/vigente. La revocación de toda la cadena sigue UNKNOWN; se investiga por separado en H9b. Esta confirmación sustituye el pendiente físico de confianza TSL, pero no completa validación de todas las CA.
