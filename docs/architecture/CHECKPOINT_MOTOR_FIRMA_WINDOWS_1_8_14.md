# Checkpoint del motor funcional 1.8.14

Referencia antes de limpieza: APK1.8.14-reniec-pki (47). Firma movil previa, lectura Java original RENIEC (Android18) y firma real Firma Peru por puente PC/SC (Android21) verificadas. PDF firmado intacto, cobertura completa, SHA256_RSA valida.

APK conservado fuera de Git: D:/DNIeToolchain/Checkpoints/firma-windows-1.8.14-2026-10-06/. SHA256: 4d8aeacb942b271cd6f3327c95b5b7aa89e85d493bf62687a6c431c664790e68. Respaldo byte a byte comprobado. Exportaciones/PDF personales y runtime de terceros permanecen fuera del repositorio. Compilacion anterior exitosa;185 pruebas aprobadas y2 interop omitidas. Estas evidencias corresponden al checkpoint, no certifican futuras refactorizaciones.

El commit/tag de checkpoint conserva fuentes de Android y Windows, pruebas, scripts propios y documentacion actual. No modificar la base clausurada modest-bose ni Antigravity. No se versionan secretos, keystores, capturas ni binarios de terceros.

## Motor y limites que se preservan

- dnie-core: APDU, formatos y criptografia independiente de Android/UI.
- dnie-protocol: IAS/PKI, PACE fisico, secure messaging, motor de firma y endpoint de traduccion PC.
- dnie-nfc: adaptador IsoDep y ciclo de vida Android.
- dnie-bridge-core: framing y operaciones del lector.
- dnie-bridge-android: transporte/emparejamiento y conexion del protocolo con tarjeta fisica.
- tools/windows-bridge y windows-pcsc-driver: host y lector PC/SC propios.
- app: presentacion y coordinacion; no debe absorber logica del motor.

Invariantes: token PACE/MAC/SSC estrictos, GET RESPONSE fisico antes de desenvolver, sesiones PC y fisica independientes, retiro de claves tras reset/cierre. No limpiar alterando respuestas APDU ni fabricando exito.

## Primera limpieza y orden posterior

Primero separar la negociacion PACE del PC de la traduccion/fragmentacion de comandos, manteniendo el contrato externo y los tests de cifrado, perfil implicito, certificados y MAC. Una refactorizacion estructural no corrige silenciosamente fallos conocidos.

Despues aislar estados/ciclo de vida, separar herramientas de investigacion de las de ejecucion, actualizar puntos de entrada y ampliar controles de dependencias. UI continua fuera del motor. Cambios de comportamiento en commits separados con reproduccion de Android20 (SM obsoleto tras reset), consulta PIN y selector real ClickOnce.

Cada paso requiere pruebas de modulos afectados, control de arquitectura y APK compilado. Mantener el APK probado disponible para volver a referencia; la siguiente APK refactorizada necesita confirmacion fisica antes de reemplazar esta referencia.
