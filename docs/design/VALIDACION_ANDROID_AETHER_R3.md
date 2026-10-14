# Aether Android79 · instalación y pruebas en teléfono

2026-10-14, America/Bogota. Continuación de [la entrega R2](RESULTADO_AETHER_R2.md). No cambia el código ni se recompila: se instala exactamente el APK79 ya verificado.

- `adb install -r` satisfactorio sobre Android78, sin desinstalar ni borrar datos de la aplicación principal.
- Versión leída del Package Manager: `pe.gob.reniec.dnie.evolution.debug`, versionCode79, versionName3.1.0.
- `AetherStampPresentationTest`: **2 pruebas pasan**, 4,01 s. Renderizado nativo de los cinco estilos, bitmap manuscrito y transparencia; selector «Todas las páginas» y vista previa Compose visible con la misma configuración.
- Arranque normal de MainActivity satisfactorio: arranque frío 1007 ms según `am start -W`. Proceso continúa activo, actividad en primer plano y sin fatal registrado en el buffer crash filtrado por ese PID durante la comprobación. Esto no es una medición exhaustiva de rendimiento.
- Paquete auxiliar de pruebas retirado al finalizar. La aplicación principal queda abierta.

APK: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.1.0-v79-aether-debug.apk`. SHA-256: `56304dc5a52a552a81b64154f7232e6d5998ecbe533a28378e3c3f7cfa8c3f27`.

Evidencia local: `D:/DNIeToolchain/Builds/aether-redesign/android-runtime-r3/`. Copia en Drive: `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-aether-20261014/android-runtime-r3/`, con salida instrumental y resumen JSON. No se exportan datos de bóveda, credenciales ni registros personales.

Quedan pendientes PDF/lote firmado con DNI real y TSA, revisión visual completa de todos los controles en uso y regresión física Suite/puente. Estas dos pruebas no prueban operaciones privadas del DNI ni suspensión de Windows. Los documentos R2 conservan el estado histórico anterior a disponer de ADB; esta revisión actualiza el estado instalado a Android79.
