# Contexto vigente del proyecto

Fork autorizado: modest-bose-evolution. Base modest-bose y Antigravity clausuradas; no modificarlas.

## Referencia funcional preservada

Commit10089b4, tag checkpoint/firma-windows-1.8.14. APK1.8.14-reniec-pki(47) preservado y SHA256 comprobado en D:/DNIeToolchain/Checkpoints/firma-windows-1.8.14-2026-10-06/. Fuentes y tests propios en Git; secretos, capturas personales y runtimes de terceros fuera de Git.

Android18: Java original RENIEC obtuvo identidad y certificados Fir/Aut mediante selector de lector por proceso. Android21: Firma Peru genero PDF con firma SHA256_RSA valida, cobertura completa e integridad verificada con pyHanko. 56/56 comandos respondidos, cierre manual. Ver [evidencia de firma](architecture/RESULTADO_ANDROID21_FIRMA_PERU_VALIDADA.md) y [checkpoint](architecture/CHECKPOINT_MOTOR_FIRMA_WINDOWS_1_8_14.md).

Lectura/exportacion IDplug adicional: biblioteca PKCS11 propia confirmada; slot1 correcto, slot3 TOKEN_NOT_RECOGNIZED (mapeo lector no concluyente). Exportados3CA+3personales CIF/FIR/AUT;5.cer coincide con firmante del PDFAndroid21. Sin C_Login/C_Sign en esta ventana. Ver [resultado IDplug](architecture/RESULTADO_IDPLUG_EXPORTACION_CERTIFICADOS.md).

## Trabajo de limpieza

Primera extraccion estructural: PcResponseFragments separa fragmentacion PC/GET RESPONSE del endpoint PACE. APK de desarrollo1.8.15-core-cleanup(48), distinto de la referencia probada. No cambia consultas PIN, reset, autenticacion ni permite SM obsoleto. 185 pruebas aprobadas,2 omitidas; arquitectura aprobada y APK compilado, no instalado ni probado con DNI aun. Detalles en [limpieza del motor](architecture/LIMPIEZA_MOTOR_ETAPA_1.md).

Motor y dependencias: [mapa del motor](architecture/MOTOR_FUNCIONAL.md). Compose sigue separado de protocolo, criptografia y transporte. PACE fisico y PC conservan claves/SSC independientes; GET RESPONSE fisico y PC separados.

## Pendientes reales

1. Android20: SM del PC enviado tras reset sin nuevo PACE produce cierre. Implementar rechazo controlado con pruebas, sin reutilizar claves/SSC antiguos.
2. Consulta PIN plana despues de PACE PC puede recibir6985 local; no confundir con bloqueo real. Firma Peru con sesion nueva ya funciono.
3. Selector de lector real en ClickOnce/Ciudadano Digital pendiente; selector del lanzador Java aislado ya funciona.
4. Convivencia de minidriver y clientes. Minidriver DNI aislado; preservar CertPropSvc y SCardSvr. Restore privado disponible bajo D:/DNIeToolchain/MinidriverBackups/.
5. ReFirma pendiente en sesion independiente. Boveda/passkeys migraciones y QR no probados fisicamente donde los informes lo indican.

## Operacion y registros

Servidor Tailscale reutilizable en100.104.106.125:39443, host privado D:/DNIeBridgePrivileged/tailnet-b840ed3a1a234354bba19c8b5abaa5f4/. Perfil Traducir PACE y sincronizacion sin JSON. Confirmar estado del proceso/puerto antes de otra prueba.

Ultima captura Firma Peru finalizada; recolectores detenidos. No hay otra prueba armada por este trabajo de limpieza. Monitor limitado no detiene por si solo la sesion NFC. PDF firmado y evidencia privadosD. No abrir consumidores de tarjeta paralelos para probar un cliente aislado.

La cronologia anterior se conserva en [archivo historico](archive/CONTEXTO_HISTORICO_HASTA_1_8_14.md); menciones antiguas de monitores armados no son estado operativo actual.
