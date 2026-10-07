# Puente 2.0.0-rc2: transportes y separación de desarrollo

Versión Android 50. El motor IAS/PKI/PACE y la traducción validada para firma desde Windows se conservan. La base clausurada y Antigravity no se modifican.

## Tres métodos

| Método | Protección | Emparejamiento | Requisito |
|---|---|---|---|
| Tailscale | Cifrado de la tailnet; sin TLS adicional | Sincronización automática, sin importar JSON | Tailscale en ambos extremos; host limitado al teléfono configurado |
| Wi-Fi local | TLS 1.2/1.3, certificado fijado por SHA-256 y token aleatorio | QR local; importación JSON opcional | Una IPv4 privada asignada a la PC y accesible desde el teléfono |
| Cable USB | El mismo TLS y emparejamiento | QR local | Activar Compartir Internet por USB en Android y elegir esa interfaz en Windows |

El método de cable usa la interfaz de red USB de Android, no ADB ni un protocolo USB accesorio. No requiere depuración USB. No implementa detección automática del cable ni selecciona una interfaz por su nombre: el usuario escoge la IP correcta. La LAN inicial admite IPv4 privada RFC1918; no se ofrece un enlace IPv6 ni una dirección comodín.

La página QR es un archivo privado con la imagen incorporada, sin servidor HTTP ni recursos externos. La credencial vence en diez minutos; una sesión autenticada conserva su límite de treinta minutos. El host abre el backend PC/SC después de autenticar al teléfono. Cambiar de método invalida el emparejamiento en memoria. No se reenvían operaciones automáticamente después de un corte.

No se modifican firewall, servicios de tarjetas ni Windows Hello. El firewall y el aislamiento de clientes de la red pueden impedir una conexión LAN; el gestor no elude esa política. La elevación existente solo inicia el backend temporal SYSTEM requerido por el lector propio.

## Arquitectura

- `dnie-bridge-core`: BridgeConnectionMethod y BridgeRuntimeSettings; políticas sin Android, UI ni almacenamiento.
- `dnie-bridge-android`: socket/TLS, sincronización y ciclo de vida de PACE. Ajustes de conexión e inactividad validados.
- `app/data/bridge`: preferencias públicas, importación, NFC y adaptador del grabador según variante. No guarda token, huella de emparejamiento ni CAN en las preferencias nuevas.
- `app/ui/bridge`: estado independiente, coordinación de operaciones y pantallas de conexión/configuración avanzada. Compose emite acciones.
- Windows: LocalNetworkPolicy valida una única IPv4 local; network-session coordina QR y host; BridgeNetworkController enumera interfaces y guarda preferencias; BridgeDesktop presenta controles.

Los ajustes Android permiten espera de conexión de 5–30 s, inactividad de 30–300 s y respuesta NFC de 5–30 s. Valores recomendados: 15/120/10 s. El host mantiene su límite independiente de inactividad (5–300 s, 120 por defecto). El intercambio no autenticado mantiene una espera acotada de treinta segundos. Los ajustes se aplican a una nueva sesión y no se editan mientras está autorizada/activa.

## Desarrollo excluido de producción

Android mueve consola APDU, sondeo, elementos visuales de consola, historial SQLite, métricas de frames y grabador APDU a `app/src/debug`. El manifiesto debug contiene la actividad de desarrollo; el manifiesto principal no. `src/release` ofrece adaptadores vacíos y no registra un destino de desarrollo. Además, el logger APDU compartido se configura sin historial ni replay al arrancar Release.

La pantalla Desarrollo reúne APDU, eventos y acceso al puente debug. Desde passkeys se abre esa misma pantalla. Solo el puente abierto desde Desarrollo presenta los perfiles de comparación PACE y la captura completa. La captura se activa explícitamente, está desactivada inicialmente y conserva el historial existente hasta borrarlo; puede incluir datos personales y certificados, con PIN oculto. Release no compila BridgeApduTrace ni DiagnosticStore y no puede exportar sus registros.

Windows compila `doctor`, `pcsc-devices`, `pcsc-relay` y los servidores históricos de diagnóstico solo con DEBUG. El paquete Release elimina el cargador de la pantalla debug, sus módulos, las pruebas del gestor y PDB. El paquete Debug contiene el catálogo de observadores/capturas/pruebas, acceso a metadatos PC/SC, navegador auditable y lanzador de módulos PowerShell con parámetros. Los módulos de otros motores se abren desde el catálogo; las capturas de varias capas que requieren parámetros se coordinan manualmente. Los módulos que requieren administrador siguen verificándolo; el lanzador no los eleva automáticamente.

El selector RENIEC por proceso sigue siendo una función operativa, separada del diagnóstico. Su presencia en Release no autoriza firmas por aplicación. El PC autorizado continúa siendo un extremo de confianza: los clientes locales PC/SC pueden solicitar operaciones y el puente no añade consentimiento por firma en esta iteración.

## Verificación y límites

- 250 pruebas Android/JVM aprobadas, dos omitidas: protocolo 86, núcleo del puente 14, adaptador Android 25+2 omitidas, app Debug 68, app Release 57.
- APK Debug y Release compilados; Release con R8, reducción de recursos y lint vital aprobado.
- 110 comprobaciones del host Windows: TLS de loopback, token/huella, encuadre, backend sintético y política de interfaces. Cuatro pruebas del selector aprobadas.
- Gestor instanciado sin tocar host ni tarjeta: tres métodos y dos interfaces disponibles. Sintaxis comprobada en scripts fuente y paquete Release.
- Emparejamiento TLS para la IP LAN real generado sin abrir un puerto ni conectar al DNI. QR decodificado independientemente: dirección, token y vencimiento coinciden; sin recursos remotos.
- `tools/verify_production_package.py` confirma exclusión en el JAR previo a R8, manifiesto no depurable sin actividad debug, paquete Windows sin módulos debug y rechazo de cuatro comandos de diagnóstico en su binario Release.

No se han validado físicamente LAN ni tethering USB con el móvil/DNI. No se reinstaló el lector ni se ejecutaron pruebas sintéticas contra el lector instalado durante la sesión. Tampoco se abrió una nueva sesión de tarjeta ni se instaló el APK por ADB. El host anterior PID53000 se mantuvo activo; se actualizó el runtime preparado para el siguiente inicio y se respaldó su copia anterior.

Los APK conservan la identidad evolución: Debug actualiza la app de pruebas, Release usa otro applicationId. La firma Release continúa usando la identidad privada de desarrollo de este fork; estos artefactos no constituyen un despliegue público certificado del controlador ni una distribución pública definitiva.

## Entrega y prueba física

Artefactos y hashes en `D:/DNIeToolchain/Checkpoints/2.0.0-rc2/`:

- `dnie-evolution-2.0.0-rc2-debug.apk` para continuar con el historial de la app actual.
- `dnie-evolution-2.0.0-rc2-release.apk` para verificar la variante sin desarrollo.
- `puente-windows-2.0.0-rc2-debug.zip` y `puente-windows-2.0.0-rc2-release.zip`.

Los paquetes incluyen `ABRIR-GESTOR.bat`, runtime del host y generador QR; requieren .NET 8 y el lector propio previamente instalado. El selector opcional requiere Python/Frida y el runtime local RENIEC; no se distribuyen binarios propietarios de RENIEC ni el SDK de Windows.

1. Terminar la sesión actual en el teléfono y detener el host actual desde su panel. Cerrar y volver a abrir el gestor para cargar la nueva interfaz.
2. LAN: elegir LAN TLS y la interfaz física de la PC; iniciar host. En Android elegir Wi-Fi local, escanear QR, comparar huella, autorizar y acercar DNI.
3. USB: activar Compartir Internet por USB, actualizar interfaces y elegir esa interfaz en el gestor. En Android elegir Cable USB y seguir el QR. No habilitar depuración USB.
4. Probar primero lectura con un solo consumidor y después una firma en ReFirma/Firma Perú. Comprobar cierre manual y retirada del lector al perder transporte.
5. Para registros completos usar Debug → Desarrollo → Puente debug → Capturar APDU antes de iniciar. En Release comprobar ausencia de estas opciones.
