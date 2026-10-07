# Lector instalado pero ausente de PC/SC — 2026-10-07

Sesión Tailscale activa; consulta autenticada del agente: Running, CardPresent=true, sin error. Eso describe la interfaz privada del backend, no acredita enumeración en WinSCard.

Comprobaciones reales: PnP reconoce `ROOT\SMARTCARDREADER\0001`, estado OK/código 0; interfaz estándar habilitada. Sin embargo `SCardListReaders` enumera solo el lector legado y Windows Hello. La consulta PnP de tarjetas muestra nodos históricos no presentes. El servicio SCardSvr está Running y su SID coincide con la ACL del INF.

No se reinstaló el minidriver ni se modificaron Hello, CertPropSvc, SCardSvr, firewall o drivers. Se solicitó `/restart-device` solo para nuestro lector desde la terminal administrativa previamente autorizada. PnP devolvió **3010: requiere reiniciar Windows**. Después de esa solicitud el lector sigue ausente de WinSCard, aunque el backend sigue reportando tarjeta. No se reinició el equipo automáticamente; la causa última de la falta de enumeración aún no está demostrada.

Pendiente: reinicio manual, comprobar primero enumeración PC/SC con `tools/host-diagnostics/pcsc-presence.ps1`, después abrir una nueva sesión NFC y validar presencia/lectura/firma. Si no vuelve el lector, capturar arranque/reconocimiento de su interfaz por SCardSvr antes de reinstalar. La compatibilidad abierta sigue en el código; H1a de suite no sustituyó este puente ni se desplegó en Windows.

Deuda de observabilidad: diferenciar lector privado accesible, lector enumerado por PC/SC y tarjeta presente para clientes. Evitar presentar CardPresent del backend como garantía de compatibilidad. Evidencia operativa local: `D:/DNIeToolchain/Diagnostics/reader-enumeration/restart-result.txt`.

Actualización 2026-10-07: el usuario confirma que volvió a funcionar después del reinicio. Recuperación confirmada por el usuario; no demuestra causa raíz ni que sea un incidente aislado. Mantener pendiente captura de enumeración al arranque y correlación PnP/UMDF/SCardSvr si vuelve a ocurrir. No reinstalar componentes preventivamente ni atribuirlo al canal privado H1a sin evidencia.

Consulta posterior de solo lectura: último arranque Windows 2026-10-07 09:35:10 America/Bogota; `SCardListReaders` vuelve a enumerar `DNIe Bridge Android NFC Private Reader 0`, junto al lector legado y Windows Hello. En esa instantánea el lector propio no tenía tarjeta presente. Esto acredita recuperación de enumeración, no una firma nueva.
