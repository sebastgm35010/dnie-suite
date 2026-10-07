# ReFirma CMS y exposicion de una sesion activa

6 de octubre de 2026. Auditoria desde un proceso Windows de usuario normal, sin elevacion. No se enviaron APDU, PIN, firmas nuevas, cambios ni reset al DNI. Se mantuvo activa la conexion. No se desactivo ni reconfiguro ningun lector/servicio.

## Firma aportada

ReFirma genero un CMS SignedData separado, un firmante, SHA256_RSA y dos certificados incorporados. Se verifico la firma sobre atributos firmados con la clave publica del certificado del firmante. El atributo messageDigest coincide con SHA256 del JSON original local dnie-apdu-pace-android22.jsonl.json. El atributo contentType tambien coincide. Resultado criptografico correcto, sin comprobacion OCSP/CRL, cadena de confianza ni validacion legal/temporal.

El archivo recibido tenia un nombre distinto del original, pero la comparacion por digest encontro el contenido correcto; no se infirio integridad por extension ni solo por existencia del p7s. Archivos, certificados y detalle privado permanecen fuera de Git.

## Lo observado desde otro proceso normal

| Superficie | Resultado |
| --- | --- |
| Enumeracion PCSC | Puente index0, presente, ATR20bytes; virtualPCD ausente; Hello presente |
| Handle PCSC del puente | SCardConnectW DIRECT devuelve0; protocolo no negociado; SCardDisconnect LEAVE_CARD devuelve0 |
| APDU durante auditoria | Ninguna; no se llamo Transmit/Control/Reconnect |
| Interfaz privada del backend | Enumerada, pero CreateFile lectura/escritura devuelve error5, acceso denegado |
| Certificados CurrentUser/My con emisor RENIEC/PERU | Dos; ninguno con referencia privada HasPrivateKey en ese almacen |
| Procesos Java actuales | Dos, argumentos legibles por el mismo usuario; ninguno en runtime Ciudadano Digital |
| Sesion de red | Una establecida entre laptop100.104.106.125:39443 y telefono100.68.70.85 |
| Exposicion de red del puente propio | Escucha solamente la IPv4 Tailscale |
| Lector virtual antiguo | WUDFHost1880 escucha0.0.0.0:35963; su lector no tiene tarjeta presente |

SCardConnect DIRECT permite abrir el lector sin negociacion de protocolo; no demuestra que este proceso pudiera firmar ni leer un archivo del DNI. No se utilizo DIRECT para transmitir. Presencia permanecio verdadera tras cerrar el handle de prueba y red siguio conectada.

La exposicion de0.0.0.0 indica escucha en interfaces IPv4, no prueba acceso desde Internet ni una regla efectiva de firewall. El puerto35963 pertenece al camino virtual antiguo, no a la sesion Tailscale del puente propio. No se detuvo durante esta auditoria de observacion.

## Fronteras de seguridad

La ACL del backend permite SYSTEM y el SID del servicio de tarjetas; el driver exige ademas que operaciones privadas usen el handle propietario. El proceso normal quedo bloqueado efectivamente en esta prueba. El aislamiento del backend impide tomar el transporte privado directamente, pero no es una autorizacion por aplicacion del acceso estandar PCSC.

El servicio Windows permite acceso PCSC a clientes locales segun sus modos de comparticion. El filtro RENIEC corrige seleccion; no restringe consumidores del lector. Los mensajes del ABI privado llevan ID/opcode/capacidad/payload, no identidad fiable del programa Windows que origino la APDU. Por eso actualmente el telefono autoriza una sesion con la PC, no una lista de aplicaciones ni cada operacion de firma.

El host Tailscale comprueba direccion local/peer concreto antes de sincronizar, genera token aleatorio por conexion y lo elimina al cerrar. El token se entrega por esa misma conexion: acredita continuidad de sesion, no identidad de una aplicacion Android frente a otra del mismo dispositivo autorizado. El cifrado de este perfil lo proporciona Tailscale; no existe TLS adicional del puente. La confianza incluye el dispositivo del telefono y la laptop, no solo la app.

No se intento firmar desde un programa ajeno sin PIN ni aprovechar un estado autenticado de la tarjeta. No afirmar que esto este bloqueado por el puente: falta consentimiento por operacion y politica por cliente. Comparticion/exclusividad controla concurrencia, no identidad ni intencion del programa. La politica efectiva de PIN/estado autenticado tambien depende del DNI y middleware.

Certificados y firmas son publicos, pero contienen identidad personal. Los handles PKCS11 de clave privada que muestra un middleware no equivalen a exportar material secreto del DNI. No se inspeccionaron claves privadas ni memoria de procesos.

## Datos de diagnostico y distribucion

El APK candidato es debug (debuggable=true, comprobado en su manifest); no es una entrega endurecida de produccion. Un entorno de desarrollo autorizado tiene capacidades que una app release no expone. BridgeApduTrace se aplica actualmente a todas las sesiones del adaptador y guarda respuestas completas en almacenamiento privado Android; los comandos PIN/cambio PIN se redactan por INS. Al exportarse, los datos quedan accesibles segun permisos del destino. El comentario opt-in no coincide con su activacion actual permanente.

El middleware oficial de Ciudadano Digital ya inspeccionado registra argumentos de Java que pueden incluir CAN. La auditora actual no imprimio ni registro argumentos ni contenidos de logs oficiales. La posibilidad de leer argumentos de procesos del mismo usuario se confirmo con los dos Java activos; no se afirma presencia de CAN en ellos.

Proceso host activo sigue siendo53000, el que precede a la preparacion del host candidato; no se afirma que sus cambios de cleanup nuevos hayan sido desplegados en esta sesion. El usuario reporta prueba en nueva version Android. ReFirma se valida ahora en una sesion real; Ciudadano Digital y host candidato conservan sus puertas de regresion pendientes.

## Prioridades para cerrar seguridad 2.0

1. Retirar/detener el antiguo camino VPCD cuando se confirme que no se necesita; no tocar servicios requeridos ni firewall para hacerlo.
2. Hacer captura APDU una opcion explicita, desactivada por defecto en uso normal; modo metadata para diagnostico habitual y exportacion consciente.
3. Separar uso interoperable confiando en la PC de un modo con aprobacion en el telefono para firma/descifrado/cambio PIN, con pruebas de timeout y compatibilidad. No filtrar a ciegas durante esta sesion.
4. Mantener seleccion de lector y permiso de operacion como componentes diferentes. Estudiar atribucion fiable por cliente antes de prometer allowlist de aplicaciones.
5. Compilar/validar release e instalacion distribuible; no presentar debug/test-signing como endurecimiento final. El redisenio3.0 consume estos contratos, no los reemplaza.

Referencias primarias: [SCardConnectW](https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardconnectw) y [SCardListReadersW](https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardlistreadersw). Evidencia privada en D:/DNIeToolchain/LiveCaptures/security-live-*, indicada por Temp/current-security-audit.txt. No se generaron nuevas operaciones de firma para probar seguridad.
