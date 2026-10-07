## Actualización 2026-10-06 · 2.0.0-rc3

El usuario confirmó Tailscale y reportó fallo de LAN TLS y USB por RNDIS de rc2. Nueva iteración: identidad TLS estable por PC, perfiles Android cifrados por transporte, conexión LAN inversa PC→Android manteniendo servidor TLS en PC, y USB directo AOA con API UsbDk propia. Se retiraron los límites de inactividad/30 minutos y el selector UI; el host atiende sesiones nuevas y Android vuelve a esperar el DNI después de cerrar una sesión abierta. No se repiten operaciones y no se alteró el motor de firma/PACE.

Pruebas: 251 pasan en JVM/Android, 2 de integración opcional omitidas; Windows 123 comprobaciones sintéticas. APK debug/release y gestor Windows debug/release compilados; verificación de exclusión debug aprobada. APK debug rc3 (51) instalado por ADB. Tailscale actualizado en estado de espera, sin tarjeta activa durante el cambio.

**Pendiente físico importante:** UsbDk se instaló con MSI/firma válida, pero todavía no enumera el teléfono. PnP devolvió 3010 al reiniciar únicamente su dispositivo: requiere reiniciar Windows. No se reinició la PC. USB con DNI real y LAN real no están validados todavía; sí el TLS inverso/formatos/control de sesiones mediante simulación. No cambiar firewall ni servicios de tarjetas para resolverlo.

Detalle y procedimiento: `docs/architecture/TRANSPORTES_PERSISTENTES_2_0_RC3.md`. Artefactos en `D:/DNIeToolchain/Checkpoints/2.0.0-rc3`. Base rc2 preservada en `checkpoint/puente-2.0.0-rc2`.

# Contexto vigente del proyecto

Fork autorizado: modest-bose-evolution. Base modest-bose y Antigravity clausuradas; no modificarlas.

## Candidata actual: 2.0.0-rc2 (50)

Tres métodos implementados: Tailscale con sincronización, LAN TLS por QR y cable mediante red USB/tethering TLS, sin ADB. Gestor Windows con selección de método/interfaz, preferencias y tiempo de inactividad; Android con configuración y ajustes avanzados acotados. Consola, historial, métricas y grabador separados por fuentes Debug/Release; captura APDU opt-in inicialmente apagada. Release comprobado sin implementaciones ni actividad de diagnóstico y Windows sin módulos/entrada debug ni comandos de desarrollo.

250 pruebas Android/JVM aprobadas, dos omitidas; 110 host Windows y cuatro selector. APK Debug/Release y paquetes Windows compilados. QR LAN preparado y decodificado sin abrir un listener ni tocar la tarjeta. Pendiente validación física LAN y USB. Entrega privada D:/DNIeToolchain/Checkpoints/2.0.0-rc2/. No instalado por ADB; host anterior PID53000 preservado, runtime preparado actualizado con respaldo. Ver [transportes, separación y prueba](architecture/TRANSPORTES_Y_DEBUG_2_0_RC2.md). Los apartados rc1 siguientes describen la referencia anterior; la captura opt-in ya está resuelta para rc2, el consentimiento por operación sigue pendiente.

## Referencia funcional preservada

Commit10089b4, tag checkpoint/firma-windows-1.8.14. APK1.8.14-reniec-pki(47) preservado y SHA256 comprobado en D:/DNIeToolchain/Checkpoints/firma-windows-1.8.14-2026-10-06/. Fuentes y tests propios en Git; secretos, capturas personales y runtimes de terceros fuera de Git.

Android18: Java original RENIEC obtuvo identidad y certificados Fir/Aut mediante selector de lector por proceso. Android21: Firma Peru genero PDF con firma SHA256_RSA valida, cobertura completa e integridad verificada con pyHanko. 56/56 comandos respondidos, cierre manual. Ver [evidencia de firma](architecture/RESULTADO_ANDROID21_FIRMA_PERU_VALIDADA.md) y [checkpoint](architecture/CHECKPOINT_MOTOR_FIRMA_WINDOWS_1_8_14.md).

Lectura/exportacion IDplug adicional: biblioteca PKCS11 propia confirmada; slot1 correcto, slot3 TOKEN_NOT_RECOGNIZED (mapeo lector no concluyente). Exportados3CA+3personales CIF/FIR/AUT;5.cer coincide con firmante del PDFAndroid21. Sin C_Login/C_Sign en esta ventana. Ver [resultado IDplug](architecture/RESULTADO_IDPLUG_EXPORTACION_CERTIFICADOS.md).

## Trabajo de limpieza

Candidata2.0.0-rc1(49) compilada, no instalada: ADB sin dispositivos. PcPaceNegotiator, PcResponseFragments, PlainCertificateReadRoute y PlainPinStatusQuery separan responsabilidades. BridgeMode excluye combinaciones incompatibles; traduccion Android recomendada por defecto, comparacion en opciones avanzadas. Supervisor y panel Windows independientes de capturas.188pruebas Android/JVM aprobadas,2omitidas;98hostWindows y26lector instalado; selector/panel sinteticos correctos. Ver [entrega y validacion](architecture/CONSOLIDACION_2_0_RC1.md) y [plan2.0/3.0](architecture/PLAN_VERSIONES_2_Y_3.md).

Motor y dependencias: [mapa del motor](architecture/MOTOR_FUNCIONAL.md). Compose sigue separado de protocolo, criptografia y transporte. PACE fisico y PC conservan claves/SSC independientes; GET RESPONSE fisico y PC separados.

## Pendientes reales

1. Repetir Ciudadano Digital y Firma Peru sobre APK/panel2.0rc1; referencia instalada preservada. No declarar2.0final antes de esas pruebas fisicas.
2. Android20: cierre por SM obsoleto corregido con6985 y nueva autenticacion exitosa en pruebas cifradas. Consulta vaciaPIN corregida sin habilitar VERIFY con datos. Cambios requieren regresion fisica del candidato.
3. Seleccion equivocada superada en prueba real Android22; ahora supervisor/panel operativos preparados. Autenticacion siguiente es facial con ID Peru; no se ejecutara descarga/programa de cambio PIN.104/104APDU de referencia. Ver [resultado Android22](architecture/RESULTADO_ANDROID22_CIUDADANO_DIGITAL.md) y [causa confirmada](architecture/RESULTADO_SELECTOR_JAVA_HELLO_CONFIRMADO.md).
4. Convivencia de minidriver y clientes. Minidriver DNI aislado; preservar CertPropSvc y SCardSvr. Restore privado disponible bajo D:/DNIeToolchain/MinidriverBackups/.
5. ReFirma CMS real validado: firma SHA256_RSA correcta y digest coincide con JSON original local. Sesion activa auditada sin APDU: backend privado deniega usuario normal, PCSC expone lector; VPCD antiguo escucha todas interfaces. Falta endurecer autorizacion por operacion y captura opt-in, no atribuir permiso por app al selector. Ver [auditoria y firma ReFirma](architecture/AUDITORIA_SESION_ACTIVA_REFIRMA_2_0.md). Boveda/passkeys migraciones y QR no probados fisicamente donde los informes lo indican.

## Operacion y registros

Servidor Tailscale reutilizable en100.104.106.125:39443, host privado D:/DNIeBridgePrivileged/tailnet-b840ed3a1a234354bba19c8b5abaa5f4/. Perfil Traducir PACE y sincronizacion sin JSON. Confirmar estado del proceso/puerto antes de otra prueba.

ABRIR-PUENTE-PC.bat abre el panel nuevo. Host candidato preparado en D:/DNIeToolchain/PairingUi, con copia anterior preservada en D:/DNIeToolchain/Checkpoints/2.0.0-rc1/windows-host-before. El proceso actual conserva su version anterior hasta detener/iniciar desde el panel. Logs nuevos y APK privadosD, ninguna captura activa ni supervisor dejado en marcha tras los self-tests.

Ultima captura Android22 finalizada: Java real eligio puente,104/104APDU coinciden movil/host,ETW cerrado,observador stopped confirmado. Filtro temporal retirado. No hay nuevo APK instalado. Ver [preparacion del filtro](architecture/PRUEBA_SELECTOR_JAVA_REAL.md). Monitor limitado no detiene por si solo la sesion NFC. PDF firmado y evidencia privadosD. No abrir consumidores de tarjeta paralelos para probar un cliente aislado.

La cronologia anterior se conserva en [archivo historico](archive/CONTEXTO_HISTORICO_HASTA_1_8_14.md); menciones antiguas de monitores armados no son estado operativo actual.
