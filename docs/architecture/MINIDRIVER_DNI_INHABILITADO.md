# Minidriver DNI inhabilitado para control

Acción autorizada por el usuario: inhabilitar minidriver del DNI, conservando CertPropSvc y lector puente.

Resultado: paquete oem237.inf/peruminidriver.inf de IN Groupe1.8.0.459 exportado y retirado con pnputil /delete-driver /uninstall, código0. Cuatro asociaciones Calais de DNI Perú contacto/contactless,32/64bits, exportadas y retiradas. Verificación posterior: INF original no presente; cuatro claves ausentes; no módulos PeruMinidriver/idplug-peru-minidriver cargados al finalizar inventario administrativo.

CertPropSvc y SCardSvr Running/Automatic antes y después. Lector ROOT\SMARTCARDREADER\0001, DNIe Android NFC Private Smart Card Reader, iniciado con oem236.inf; confirmado por pnputil. Host100.104.106.125:39443 PID53000 sigue escuchando. No se modificaron servicios, políticas, firewall ni DLL manualmente, ni se solicitó reinicio.

Respaldo privado y restauración administrativa: D:\DNIeToolchain\MinidriverBackups\95aed260f9974207ab292516f607d8e8\RESTAURAR-ADMIN.ps1. Incluye paquete completo, cuatro exportaciones de registro y result.json. El restore reinstala el INF exportado e importa asociaciones. No ejecutado todavía.

Siguiente control: verificar en la inserción siguiente que no reaparezcan asociaciones/otro paquete mediante Plug and Play. No asumir que impedir la carga resuelve compatibilidad del middleware: el minidriver retirado es requerido por consumidores CSP/KSP del DNI. No bloquear reinstalación con políticas. Último monitor ya concluido; no nuevo monitor armado en esta acción.
