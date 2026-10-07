# Distribución del puente con menos instalaciones

Evaluación documental y del repositorio: 2026-10-06. El usuario confirma que el flujo USB funciona y firma. No se recibió un nuevo PDF/log para validación independiente; la estabilidad de reconexión y la matriz de compatibilidad continúan pendientes.

## Resultado

El controlador de lector personalizado no es necesario para ejecutar PACE ni firmar con el DNI. Es el adaptador que publica el puente como lector en el PC/SC local de Windows, para que consumidores existentes lo usen sin integrar nuestro protocolo.

Hay dos cuestiones independientes:

1. Transporte PC–Android: Tailscale, LAN TLS o USB AOA. La implementación USB actual necesita UsbDk; LAN/Tailscale no.
2. Interfaz ofrecida al consumidor PC: lector PC/SC, módulo PKCS#11, proveedor criptográfico Windows o API propia. Cambiar el transporte no sustituye esta interfaz.

Dentro del escritorio local de Windows no se encontró una API pública soportada que permita publicar un lector PC/SC funcional arbitrario desde una aplicación portable, sin dispositivo/controlador instalado. SCardIntroduceReader administra el nombre de un lector existente; no implementa su backend. SwDeviceCreate requiere administrador y tampoco aporta por sí mismo la función de lector.

Fuentes: [entorno de lectores](https://learn.microsoft.com/en-us/windows-hardware/drivers/smartcard/smart-card-driver-environment), [SCardIntroduceReader](https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardintroducereaderw), [SwDeviceCreate](https://learn.microsoft.com/en-us/windows/win32/api/swdevice/nf-swdevice-swdevicecreate).

## Opciones

| Alternativa | Qué elimina | Qué exige / compatibilidad | Viabilidad |
| --- | --- | --- | --- |
| Firmador Windows propio por LAN/Tailscale | Lector virtual, acceso privado al controlador, UsbDk y servicio SYSTEM para esa edición | App del usuario y protocolo autenticado Android. Reimplementar el frontend/motor de firma PC o reutilizar bibliotecas adecuadas. No publica un lector para RENIEC. | Alta para un producto propio; requiere desarrollo, no implementado. |
| Módulo PKCS#11 remoto | Controlador PC/SC propio y, por LAN/Tailscale, UsbDk | Consumidor que permita cargar nuestra DLL y configurar el proveedor. ReFirma sería candidato a comprobar, no compatibilidad ya demostrada. DLL de arquitectura correcta y contratos PKCS#11 completos. | Alta como prototipo acotado. |
| Proveedor Windows KSP/CSP remoto | Puede evitar un lector y su driver kernel para consumidores que solo usan APIs criptográficas | Instalación/registro del proveedor, asociación del certificado con la clave remota, compatibilidad CNG/CryptoAPI según consumidor. No sustituye operaciones PC/SC que busquen una tarjeta. Permisos/registro pendientes de comprobación específica. | Media; no prometer edición portable universal. |
| Lector UMDF propio | Reduce código propio en kernel | Sigue siendo un controlador con paquete/registro PnP. No equivale a una app sin instalación; evaluar por estabilidad y mantenimiento, no como eliminación de drivers. | Media; rediseño sin quitar el requisito de instalación. |
| USB virtual UDE o USB/IP | Podría sustituir nuestro driver PC/SC por una pila de USB virtual y CCID | UDE/USB-IP necesita controlador de host virtual. Habría que emular además CCID y alimentar sus operaciones desde NFC/PACE. | Viable técnicamente, poco adecuada para el objetivo de cero drivers. |
| Hardware puente USB CCID | Controlador Windows propio; usaría el controlador de clase incluido en Windows | Dispositivo físico con firmware CCID y enlace al teléfono. Conserva el lector ante consumidores PC/SC. No asegura que todo middleware DNI deje de requerir sus componentes de tarjeta. | Viable como producto de hardware adicional. |
| Redirección de tarjeta por RDP | Puede aprovechar la infraestructura de redirección incluida en Windows en el extremo remoto | Cliente RDP Android que implemente el backend NFC/PACE y canal de tarjeta. Apps dentro de sesión remota, políticas/configuración RDP; no publica el lector en el escritorio local habitual. | Investigación secundaria, no sustituto directo del despliegue actual. |

USB AOA presenta endpoints bulk de accesorio, no una interfaz CCID. Una APK normal no dispone de una API pública para convertir cualquier teléfono en dispositivo USB CCID y controlar sus descriptores; requeriría intervención en la pila USB del sistema/OEM. Es una inferencia de las APIs y el formato AOA documentados, no una prueba de todas las ROM. WinUSB automático también depende de descriptores del dispositivo que anuncien compatibilidad WINUSB: usar WinUSB por sí solo no publica un lector PC/SC.

Fuentes: [UDE](https://learn.microsoft.com/en-us/windows-hardware/drivers/usbcon/usb-emulated-device--ude--architecture), [clases USB incluidas en Windows, CCID](https://learn.microsoft.com/en-us/windows-hardware/drivers/usbcon/supported-usb-classes), [WinUSB automático](https://learn.microsoft.com/en-us/windows-hardware/drivers/usbcon/automatic-installation-of-winusb), [AOA](https://source.android.com/docs/core/interaction/accessories/aoa), [MANAGE_USB privilegiado en AOSP](https://android.googlesource.com/platform/frameworks/base/+/af3eb61/tests/privapp-permissions/product/AndroidManifest.xml), [redirección RDP](https://learn.microsoft.com/en-us/windows/security/identity-protection/smart-cards/smart-card-and-remote-desktop-services).

## Proyectos consultados

- [usbip-win2](https://github.com/vadimgrn/usbip-win2): licencia [BSD-2-Clause](https://github.com/vadimgrn/usbip-win2/blob/master/LICENSE.txt). Incluye cliente UDE y filtro kernel; evidencia de que virtualizar USB no elimina instalar controladores. No se descargó ni instaló.
- [pkcs11-proxy-ng](https://github.com/mingulov/pkcs11-proxy-ng): [MIT](https://github.com/mingulov/pkcs11-proxy-ng/blob/main/LICENSE-MIT) / Apache-2.0. Referencia de arquitectura cliente/daemon/proveedor remoto. Documenta pruebas principalmente Linux; no se ha verificado que compile o sirva en Windows/Android para nuestro DNI. Revisión de dependencias e integración pendiente; no adoptado.
- [OpenSC](https://github.com/OpenSC/OpenSC): referencia para distinguir PKCS#11 y minidriver Windows. No asumir soporte nativo de nuestro protocolo por usar su biblioteca ni incorporarlo sin evaluar licencia/dependencias.

## Propuesta de implementación futura

Conservar el lector actual como edición de compatibilidad para RENIEC, IDplug, Firma Perú/ReFirma y demás consumidores PC/SC. Consolidar su firma/distribución de producción en un instalador único; estudiar distribución por Windows Update conforme a [las recomendaciones de Microsoft](https://learn.microsoft.com/en-us/windows-hardware/drivers/driversecurity/driver-security-checklist). No reemplazar aún el driver funcional por una pila USB más amplia.

Crear en paralelo una interfaz de operaciones del motor Android: enumerar certificados y claves públicas, autorizar una operación y firmar usando la clave real del DNI. Identificadores opacos de claves/sesión; nunca exportar privadas. La coordinación debe mantener exclusión mutua entre consumidores, cancelación y ausencia de reenvío automático de operaciones privadas. No reutilizar una sesión PACE desde UI.

Primera prueba de viabilidad portable: cliente Windows no elevado en una máquina limpia, por LAN TLS, capaz de consultar certificados y verificar una firma de desafío. Sin instalar nuestro lector ni UsbDk. Después, implementar un módulo PKCS#11 mínimo conforme a los mecanismos reales del DNI y probarlo en un consumidor que permita seleccionar el proveedor. Las reglas de PIN, longitudes de firma, flags, slots, sesiones, concurrencia y errores deben probarse; no basta simular C_Sign devolviendo bytes.

Si ese consumidor funciona, añadir otra edición o modo claramente identificado: portable para nuestras apps/proveedores configurables y compatibilidad PC/SC con instalación. Ambos comparten motor DNI y transporte; el controlador queda como plugin de publicación, no como dependencia del motor.

No se modificaron drivers, servicios, firewall ni la sesión del usuario durante esta evaluación. No se implementaron aún estos proveedores.
