# Puente 2.0.0-rc3: identidad estable, red local inversa y USB directo

## Problema observado

Tailscale funcionó en el teléfono. Los intentos LAN TLS y red USB de rc2 no llegaron a la aceptación TCP del host: solo registraron espera, sin negociación TLS ni apertura PC/SC. Esto localiza el fallo antes de TLS, pero no prueba por sí solo si fue firewall, ruta o interfaz. La interfaz RNDIS del teléfono estaba clasificada como red pública. No se cambió esa clasificación ni el firewall.

## Identidad de la PC y emparejamiento

`PcIdentity` conserva RSA 3072, certificado TLS, identificador y token de 32 bytes en una carpeta NTFS privada por usuario de esta PC. LAN y USB usan la misma identidad; iniciar un host o cambiar de transporte no renueva la huella. El certificado tiene diez años de vigencia, no una vigencia infinita: una rotación explícita obliga a volver a emparejar. No se modifica ningún certificado personal del DNI ni se exportan sus claves privadas.

El formato de QR conserva `DNIEBRIDGE1:` y admite JSON versión 2 con `persistent:true`. Los QR antiguos versión 1 siguen sujetos al límite de quince minutos. El QR permanente contiene una credencial: quien lo copie obtiene su token; ocultarlo o crear otra carpeta de sesión no lo revoca. Se genera y conserva en una carpeta privada y se debe compartir solo con el teléfono autorizado.

Android recuerda perfiles de transporte con AES-GCM y una clave no exportable de Android Keystore, dentro de `noBackupFilesDir`. El CAN/PIN no están en esos perfiles. Cambiar de método recupera su perfil previo. Autorizar sigue siendo una acción del usuario. «Olvidar PC» elimina todos los perfiles locales de emparejamiento; no revoca copias en otros teléfonos.

Para revocar todas las copias: detener el host, apartar la carpeta de identidad privada y crear una identidad nueva; todos los teléfonos deben escanear el QR nuevo. No borrar/reemplazar la identidad mientras un host viejo siga activo. Las carpetas de sesión contienen copias de la identidad anterior y deben permanecer privadas hasta retirarlas.

## Tres transportes

- **Tailscale:** conserva el canal TCP directo autorizado por el par de la tailnet y su sincronización automática. No necesita TLS adicional, JSON ni QR.
- **Wi-Fi local:** Windows conecta al teléfono en su IP Wi-Fi y puerto 39444. Android acepta únicamente la IP emparejada. Windows continúa siendo servidor TLS y Android cliente TLS: la dirección del TCP no cambia quién presenta el certificado. Android valida huella y vigencia; el host exige el token antes de abrir el backend PC/SC. Esta variante evita un puerto entrante LAN en Windows, sin alterar su firewall. Una Wi-Fi con aislamiento entre clientes puede seguir impidiendo el enlace.
- **USB directo:** Android Open Accessory (AOA), control USB 51/52/53 y endpoints bulk de accesorio. Windows usa una integración propia de la API C de UsbDk, sin incorporar el motor GPL de Virtual Smart Card ni libusb. UsbDk es una dependencia opcional Apache 2.0. Se selecciona explícitamente el teléfono; no se prueban dispositivos USB arbitrarios ni se usa la interfaz ADB como transporte. El cable debe admitir datos. No requiere depuración ni RNDIS.

El protocolo `DNIREADY1` de nueve bytes espera la preparación del teléfono antes de iniciar el plazo de negociación TLS. No constituye autorización: después vienen TLS, verificación de huella y token. En USB, una adaptación interna de loopback en Android transporta **bytes TLS cifrados** entre SSLSocket y el descriptor de accesorio; el enlace físico PC/teléfono es USB, sin IP, ADB ni compartir Internet. El código de PACE/firma se mantiene en su motor anterior.

## Duración y cancelación

No hay cierre de sesión autenticada por inactividad ni límite de treinta minutos. Tampoco hay límite de ejecución de treinta y cinco minutos en la tarea del host. El primer byte de un nuevo frame puede esperar indefinidamente; una vez iniciado, su recepción y reenvío sí tienen plazo. La negociación no autenticada sigue acotada a quince segundos en Windows y treinta en Android. Las operaciones NFC y PC/SC conservan sus límites individuales.

Cuando una sesión ya abierta termina, Android vuelve a esperar una nueva presentación del DNI y el host permanece listo. Cada sesión abre PACE de nuevo: no se repiten PIN, firmas ni APDU anteriores. Una pérdida NFC detectada por el transporte retira la tarjeta; no se falsifica presencia. La detección de una retirada estando totalmente inactivo depende de la siguiente operación NFC; los pulsos del backend no interrogan el chip. Salir/bloquear Android o pulsar Detener cancela. Las conexiones fallidas antes de abrir el DNI requieren autorizar de nuevo.

## División de módulos

- `PcIdentity`: credencial de host, ACL y certificado estable.
- `PersistentPcscHost`: coordinación de sesiones sucesivas.
- `TransportReadiness`: preparación del enlace antes de autenticar.
- `UsbAccessoryStream` / `UsbAccessoryDescriptors`: adaptación USB Windows y selección de endpoints sin ADB.
- `BridgeTransportAdapter`: socket inverso LAN / descriptor AOA, sin lógica PACE.
- `TrustedPcStorage`: almacenamiento Android de emparejamiento cifrado.
- `BridgeUsbPermission`: consentimiento de accesorio USB.
- `BridgeViewModel`: coordinación/cancelación; Compose presenta opciones y estado.

Los módulos de diagnóstico permanecen fuera de los binarios de producción. El soporte USB es una función operativa y su instalador verifica tanto SHA-256 fijo como firma Authenticode de Red Hat antes de ejecutarse.

## Validación y pendientes físicos

Las pruebas sintéticas Windows incluyen conservación exacta del PFX/token, QR persistente, espera más larga que el plazo de frame, rechazo de payload truncado, TLS sobre TCP iniciado por PC, preparación fragmentada y rechazo de descriptores que solo ofrecen ADB. Android prueba lectura/serialización/QR de perfiles USB y LAN persistentes, además de las pruebas del protocolo, PACE y aplicación existentes.

UsbDk 1.0.22 x64 se instaló desde su publicación oficial. MSI SHA-256: `91F6F695E1E13C656024E6D3B55620BF08D8835EF05EE0496935BA6BB62466A5`. Firma MSI y controlador: válida. MSI devolvió 0; al solicitar reinicio únicamente del dispositivo del teléfono, PnP devolvió **3010 (reinicio de Windows requerido)**. No se reinició la PC. UsbDk enumera los otros dispositivos, pero todavía no el teléfono. Por tanto, **USB con DNI real queda pendiente después de reiniciar/reconectar**; instalación correcta no equivale a transporte validado.

También queda pendiente prueba de Wi-Fi real, retirar/acercar varias veces y firma desde ReFirma/Firma Perú en ambos transportes. No se usó el DNI real para los experimentos sintéticos ni se cambiaron servicios de tarjetas, minidriver, Windows Hello o reglas de firewall.

## Cómo probar

1. Abrir el gestor nuevo de Windows y detener el host anterior antes de cambiar de método.
2. LAN: seleccionar la interfaz PC, introducir la IP Wi-Fi que muestra Android, iniciar el host y escanear su QR. Seleccionar Wi-Fi local y autorizar en el móvil; acercar el DNI.
3. USB: completar el reinicio pendiente, reconectar cable de datos, actualizar dispositivos y seleccionar el teléfono. Iniciar USB directo, importar su QR la primera vez, aceptar el permiso de accesorio y autorizar. No activar Compartir Internet ni depuración para este transporte.
4. Dejar el canal inactivo, retirar y volver a acercar el DNI; observar nueva sesión y firma posterior. No considerar una firma sintética como validación de una firma real.

Fuentes: [AOA](https://source.android.com/docs/core/interaction/accessories/aoa), [UsbAccessory Android](https://developer.android.com/develop/connectivity/usb/accessory), [API UsbDk](https://github.com/daynix/UsbDk/blob/master/UsbDkHelper/UsbDkHelper.h), [licencia UsbDk](https://github.com/daynix/UsbDk/blob/master/LICENSE).
