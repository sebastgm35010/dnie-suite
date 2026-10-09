# Controlador: energía, cancelación y publicación · R1

2026-10-09. Checkpoint previo `checkpoint/pre-driver-power-r1`. Especialista `/root/driver_power_audit` revisó driver/INF y escribió pruebas; root revisó cambios, corregió observabilidad del host y coordinó firma/despliegue mediante tmux administrativo comprobado. No se usó DNI/PIN ni se suspendió/reinició la PC durante esta iteración.

## Correcciones

Driver2.0.0.3: EvtIoStop retira la solicitud pendiente antes de abandonar D0, respeta UnmarkCancelable y el callback que ya posee cancelación; completa sin reencolar. Cancelación invalida backend porque una APDU puede haberse ejecutado físicamente. Respuestas posteriores de una sesión anterior no completan solicitudes nuevas. Reset/transmit/firma no se repiten automáticamente al reanudar.

Colas manuales de notificación no power-managed, purge síncrono fuera del lock y arranque después de vaciarlas; IOCTL fuera de D0 devuelve DEVICE_NOT_READY. D0Exit retira ATR y dueño de backend. INF aísla el host UMDF mediante ProcessSharingDisabled; no demuestra que Bix causara el fallo. ABI, ACL, reflector de Windows, PACE, minidriver y políticas de integridad no modificados.

Host/control conserva CardPresent como estado privado e incorpora ReaderPublished nullable. ReaderPublication solo usa WinSCard establecer contexto/listar/liberar; no conecta tarjeta ni envía APDU/reset. Error de consulta => indeterminado, no ausencia. GUI diferencia DNI conectado/lector publicado, lector no publicado y publicación sin verificar. Adición al contrato JSON compatible con clientes anteriores. Servicio no incorpora coordinación de eventos de energía en esta iteración: driver cancela/retira y backend debe terminar su sesión, sin replay.

## Paquetes y despliegue

Rollback firmado original: D:/DNIeToolchain/Checkpoints/driver-power-r1/rollback-package, manifest SHA256 al lado. No sobrescrito. Compilación corregida: D:/DNIeToolchain/ReaderBuild-power-r1/DniBridgeReader. Paquete de pruebas firmado: D:/DNIeToolchain/ReaderTestPackage-power-r1. Certificado local de desarrollo existente, sin nueva CA/trust ni exportación de privada. InfVerif y catálogo/firma verificados mediante herramientas WDK de procedencia validada.

Instalado oem237.inf /2.0.0.3 en ROOT\SMARTCARDREADER\0001 sin reinicio; PnP0 y lector publicado en WinSCard comprobados. Gestor/servicio Release actualizado desde D:/DNIeToolchain/Builds/driver-power-r1-native. Ejecutable activo D:/DNIeBridgeApp/versions/bfd1bf9a727e4a5ba1c74d0e89614e3e/DNIeBridge.exe. Consulta autenticada final: Stopped, CardPresent=false, ReaderPublished=true. Nueva sesión física aún no probada. Android/APK76 no se cambió.

## Pruebas y límites

- C++ de producción incluido por power-tests/PowerLifecycleTests.cpp mediante seam WDF exclusivo: 6797 comprobaciones pasan, 200 carreras reales de hilos cancel/stop y 200 ciclos simulados. Prueba vacíoPoll sin clientes, antes y después de cancelación, propietario ajeno, completado único, reset/poweroff, respuesta tardía, timeout, cierre y notificaciones. No reproduce scheduler WDF, PnP ni suspensión física. Logs D:/DNIeToolchain/driver-power-tests.log y driver-power-build.log; WDK26100 build/INF sin errores ni advertencias.
- Referencia controlador anterior: 26 comprobaciones PCSC nativas +13 TLSrelay pasan como SYSTEM.
- Controlador instalado2.0.0.3: 25 comprobaciones PCSC nativas +13 TLSrelay pasan, incluidas 35s idle con3pulsos, reset/PowerOff reales por driver y retirada al cerrar conexión sintética.
- Primera prueba nativa nueva falló en supuesto de harness: Poll vacío después de Attach. Windows puede encolar reset automático; ese Poll lo consumía sin responder. Se retiró la assertion y worker vuelve a ser único consumidor. VacíoPoll se verifica en C++ sin clientes ajenos. Primer resultado fallido conservado, segunda ejecución pasa; no hubo cambio del driver entre ambas.
- Control Debug/Release: 45 comprobaciones pasan cada uno, incluyendo Attach privado con lector ausente, publicación disponible/indeterminada y separación de otros lectores. Paquete nativo construido sin errores. Consulta real del servicio nuevo acredita ReaderPublished=true.
- Límites de arquitectura e imports pasan; diff --check pasa. Resultados externos D:/DNIeToolchain/Checkpoints/driver-power-r1/ (baseline-*, updated-r2-*, control-*, deployment*, final-pnp.json y final-pcsc.json). No se incluyen logs personales, certificados privados o binarios en Git/SQL.

## Pendiente físico

Establecer sesión nueva, verificar DNI publicado/firma real; después acordar prueba de suspensión/reanudación con estado y solicitud pendiente capturados. Al reanudar, la sesión de tarjeta anterior debe estar retirada y la nueva requiere autenticación; jamás repetir automáticamente la firma interrumpida. Inspeccionar WinSCard/PnP/UMDF antes y después, sin cambiar CertPropSvc/Hello/minidriver/firewall. No dar por cerrada causa original: los defectos de código están corregidos y comprobados en sus alcances, pero falta demostrar la relación con las dos incidencias mediante trazas/dump y reproducción física.

Referencias Microsoft en tools/windows-pcsc-driver/README.md. Informe original y seguimiento: docs/operations/LECTOR_AUSENTE_PCSC_2026_10_07.md.
