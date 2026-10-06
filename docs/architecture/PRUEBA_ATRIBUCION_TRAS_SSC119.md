# Próxima prueba: atribución PC/SC tras SSC reiniciado

La sesión anterior confirmó SSC=1 frente al esperado119 con las claves actuales. Objetivo nuevo: localizar el ciclo de vida del consumidor Windows que produce ese SELECT, sin modificar MAC/SSC ni el firmware o reinstalar controladores.

## Preparación

Monitor administrativo armado: `D:\DNIeToolchain\LiveCaptures\tailnet-pcsc-lifecycle-900d4734d802495d9ce98dab8c1cf244`. Sigue el host existente `tailnet-b840ed3a1a234354bba19c8b5abaa5f4`. Espera hasta dos horas desde armed.json; captura un único intento y cierra al retornar el host a espera, con límite300 segundos. Reutilizar Android1.8.12; no requiere nuevo APK ni QR/JSON.

Al aceptar la conexión lanza metadatos de procesos/módulos y captura administrativa ETW PC/SC a un directorio GUID independiente, enlazado desde attribution-directory.txt. Proveedores Smartcard y WPP, WinSCard, SCardSvr, CertProp y minidriver; ETL circulares con límite32+64MiB. El cierre escribe stop.txt, detiene las sesiones ETW y espera al observador de procesos. Se registra attributionReady para distinguir arranque correcto de fallo. Los eventos anteriores al arranque de ETW pueden no quedar incluidos. No garantizar decodificación WPP ni atribución por llamada si faltan TMF/símbolos.

Windows listener confirmado100.104.106.125:39443 PID53000. IDplug se intentó abrir, pero no se confirmó un proceso persistente: no declarar que está activo. El minidriver/CertProp puede emitir comandos sin esa UI, como en pruebas anteriores. Edge no está disponible para captura.

## Ejecución

1. Android1.8.12: Tailscale activo, Conectar por Tailscale · sin JSON, acercar DNI y mantener pantalla encendida.
2. Dejar terminar el intento y exportar APDU y diagnóstico general.
3. Cruzar sesión/contexto SSC con solicitudes Windows y ventana ETW; comprobar readiness y códigos de parada antes de concluir cobertura. No aceptar el SSC alternativo automáticamente.

## Verificación y retención

Monitor probado con una secuencia sintética de apertura, transmisión y cierre; el dry-run no inicia ETW. Parser PowerShell validado. No se cambió código Android ni driver y no se recompiló APK.

Resúmenes históricos privados: `D:\DNIeToolchain\HistoricalResults`. Se verificaron inventarios y SHA256 de12 capturas antiguas antes de intentar limpieza. La revisión automática rechazó la eliminación general sin dar motivo específico. Se realizó una operación más acotada, sin recursión: eliminación explícita de los tres archivos más pesados del intento be2a. Última prueba SSC íntegra. Los demás originales antiguos permanecen; compaction-status.json registra cantidades reales. Los análisis narrativos del repositorio se conservan.
