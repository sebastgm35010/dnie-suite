# Mapa de documentación

Los registros de entregas anteriores permanecen en su ubicación original para conservar sus enlaces. Este índice distingue decisiones vigentes, historia y trabajo pendiente.

## Entrada al proyecto

- [README del monorepo](../README.md) y [GitHub/compilación](operations/GITHUB_Y_COMPILACION_MONOREPO.md): Android, Suite, Bridge y driver en un único repositorio privado, con productos independientes.

- [Retomar después del reinicio rc4](operations/RETOMAR_TRAS_REINICIO_RC4.md): estado instalado, persistencia, pruebas y siguiente secuencia.
- [Arquitectura actual 2.0](architecture/ARQUITECTURA_ACTUAL_2_0.md): módulos Android, gestor/servicio Windows y límites de confianza.
- [Pendientes vigentes](PENDIENTES_ACTUALES.md): prioridad, evidencia pendiente y criterios de cierre. Los listados 1.7.x siguientes son históricos.

- [Contexto vigente](CONTEXTO_ACTUAL.md): referencia fisica y candidata2.0; empezar por aqui.
- [Consolidacion 2.0.0-rc1](architecture/CONSOLIDACION_2_0_RC1.md): cambios, pruebas y artefactos.
- [Plan 2.0 y redisenio 3.0](architecture/PLAN_VERSIONES_2_Y_3.md).
- [Motor y responsabilidades](architecture/MOTOR_FUNCIONAL.md).
- [Cambios y validación del QR 1.8.1](architecture/EMPAREJAMIENTO_QR_1_8_1.md).
- [Revisión del diagnóstico 6](architecture/DIAGNOSTICO_PUENTE_QR_6.md): prueba real, evidencia de autenticación/NFC y límites del registro.
- [Sesión PC/SC 1.8.2](architecture/SESION_PCSC_1_8_2.md): backend integrado, pulsos, reset y prueba instalada por TLS.
- [Diagnóstico 7 y prueba cruzada](architecture/DIAGNOSTICO_7_Y_PRUEBA_CRUZADA.md): reset real, elección de Windows Hello en Java y evidencia conjunta Edge/PCSC/motor Kotlin.
- [Reglas del fork](../AGENTS.md): límites de seguridad y base clausurada.
- [Arquitectura y módulos](../ARQUITECTURA_FORK.md): dependencias y responsabilidades.
- [Pendientes actuales](../PENDIENTES_Y_DIAGNOSTICO_1_7_1.md): pruebas aplazadas, errores observados y propuestas.
- [Mantenimiento 1.7.2](../MANTENIMIENTO_1_7_2.md): checkpoint, cambios y validación de esta entrega.

## Funciones existentes

- [Identidad ICAO](../IMPLEMENTACION_ICAO.md).
- [Firma PDF y lotes](../IMPLEMENTACION_FIRMA_MODERNA.md).
- [Motor inicial de bóveda](../IMPLEMENTACION_BOVEDA_INICIAL.md), [herramientas](../IMPLEMENTACION_BOVEDA_HERRAMIENTAS.md), [vista previa y CAN](../AJUSTE_VISTA_PREVIA_Y_CAN.md).
- Passkeys retiradas por decisión del usuario: [retiro vigente](architecture/RETIRO_PASSKEYS_R3.md). Documentos históricos: [proveedor](../IMPLEMENTACION_PASSKEYS.md), [autorización](../PASSKEYS_AUTORIZACION_UNICA.md), [respaldo/migración](../CHECKPOINT_Y_PASSKEYS_1_7.md).
- [Diagnóstico persistente](../DIAGNOSTICO_PERSISTENTE_APP.md), [QR y simulaciones](../DIAGNOSTICO_QR_Y_SIMULACION_DNI_1_7_1.md).

## Investigación siguiente

- [Emparejamiento móvil por QR](../tools/windows-pairing/README.md): launcher local, formato, vencimiento y conexión ADB sin cambios de firewall.
- [Puente Android–PC 2.0](architecture/PLAN_PUENTE_ANDROID_PC_2_0.md): investigación pública, alternativas, licencias, requisitos de seguridad y etapas. Es un plan; no representa compatibilidad ya implementada.
- [Inspección del middleware oficial](architecture/INSPECCION_HOST_RENIEC.md) y [captura Edge/host](architecture/CAPTURA_EDGE_Y_MIDDLEWARE.md): instalación intentada, interfaces declaradas y preparación para el flujo manual.
- [Inspección estática de cddnie](architecture/INSPECCION_ESTATICA_CDDNIE.md): criterio de identificación v2/v3, uso de PC/SC y continuación en Java.
- [Prueba con DNI real por VPCD](architecture/RESULTADO_DNI_REAL_VPCD.md): SELECT IAS v3 aceptado, cierre de captura y límites de la prueba.
- [Integración VPCD/PACE 2.0](architecture/INTEGRACION_VPCD_PACE_2_0.md): fuente y licencia de Smart Card Reader, ruta Java y perfiles de adaptación.
- [Alternativas permisivas del puente](architecture/ALTERNATIVAS_PERMISIVAS_PUENTE.md): componentes MIT comprobados y limitación del driver Windows.
- [Motor inicial Android del puente](architecture/PUENTE_ANDROID_MOTOR_INICIAL.md) y [host Windows TLS](architecture/PUENTE_WINDOWS_PROTOCOLO.md): módulos incorporados, pruebas y limitaciones previas al despliegue.
- [Puente 1.8.0: pantalla y sesión](architecture/PUENTE_PREVIEW_1_8.md): implementación inicial, prueba interoperable y límites pendientes para el DNI real.
- [Canal local sin cambios globales Windows](architecture/PUENTE_LOCAL_SIN_CAMBIOS_WINDOWS.md): TLS, endpoint privado por usuario, transporte temporal y límite de compatibilidad PC/SC.
- [Lector PC/SC propio: implementación inicial](architecture/LECTOR_PCSC_IMPLEMENTACION_INICIAL.md): controlador UMDF2 original, backend probado y requisitos pendientes para compilar, instalar y conectar aplicaciones reales.

## Verificaciones

`python tools/check_architecture.py` comprueba imports principales y ciclos de dependencias Gradle. No sustituye las pruebas del motor ni una operación NFC física. El comando de regresión y compilación está en la documentación de arquitectura.

Los checkpoints privados residen en `build/checkpoints`, excluidos de Git. No contienen datos personales de la app ni su clave de firma; esa clave se conserva por separado en la ubicación privada existente.
