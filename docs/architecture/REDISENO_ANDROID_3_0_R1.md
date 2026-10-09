# Android 3.0 · adaptación del prototipo · R1

2026-10-08. Referencia del usuario: D:/stitch_suite_criptogr_fica_y_firma_digital. Se revisaron DESIGN.md y las cinco imágenes/HTML de Inicio, Certificados, Firma, Bóveda y Conexión PC. El original se conserva sin cambios. Checkpoint previo: checkpoint/pre-android-redesign-apk73.

## Alcance de esta etapa

Se introduce una pantalla Inicio en Compose, una navegación de cinco entradas (Inicio, Identidad, DNIe, Firma, Bóveda) y una elección de modos de PC. PDF individual y Lotes son destinos y ViewModels independientes, accesibles desde Inicio y el selector de firma. Desarrollo se accede desde Inicio exclusivamente en debug. Bóveda conserva su flujo concentrado y vuelve mediante su botón/Atrás; oculta la barra inferior durante sus operaciones.

Tema azul/turquesa inspirado en Aether, superficies claras y alternativa oscura. Color dinámico desactivado por defecto para conservar el lenguaje visual. Fuentes del sistema disponibles offline: no se descargan fuentes ni imágenes remotas al abrir la app. Se conserva soporte de tamaño de texto del sistema. Inicio usa LazyColumn y resume solo estado mínimo, evitando cargar bóveda, certificados o imágenes durante la composición.

HomeScreen recibe un HomeSummary y acciones; HomeRoute proyecta únicamente número de certificados cargados, disponibilidad de resultado ICAO (incluida lectura independiente) y lectura en curso, con distinctUntilChanged y colección consciente del ciclo de vida. NavGraph coordina los accesos. Ningún motor depende de estas pantallas ni se crean implementaciones nuevas de firma/PACE/cifrado. El selector PC abre BridgeActivity o RemoteSigningActivity mediante los callbacks existentes y sus guardas de exclusión NFC.

Inicio y selector PC no inician lectura por simple aparición de un tag: la acción Leer mi DNIe navega al lector existente y solicita CAN antes de la autorización habitual. El resumen describe datos leídos en esta sesión de la app; nunca afirma que el DNI continúe físicamente conectado.

## Ajustes del diseño a funciones reales

- No se muestran la identidad/fotografía de ejemplo, CAN, direcciones IP o contadores ficticios del prototipo.
- No se reintroducen passkeys, biometría, funciones de revocación remota o autentificación facial.
- No se afirma DNI 4.0, raíz FNMT, QSCD, equivalencia eIDAS, cifrado cuántico, PAdES-LTA ni RSA-4096 por defecto. Los certificados y su confianza se consultan en el módulo existente.
- Firma conserva PDF integrado, CMS separado para archivos, manuscrita, colocación/resize a pantalla completa y TSA RENIEC opcional verificada. No se anuncia un verificador mediante QR del sello que no existe.
- Bóveda conserva cifrado con CIF hard, descifrado con DNI/PIN y respaldo completo DNIEBKP2 por contraseña. La recomendación de respaldo explica renovación destructiva de claves, sin suponer que los certificados pasados permanezcan.
- PC distingue lector compatible con aplicaciones externas (controlador virtual requerido en Windows) de suite propia sin exposición PC/SC global. USB del puente es accesorio directo, no ADB; LAN y Tailscale se configuran en el módulo correspondiente. No se inventa un estado TLS o conexión activa en el selector.

## Continuación del rediseño

Esta etapa adapta Inicio, tema y navegación; las pantallas internas siguen utilizando la interfaz probada con el tema actualizado. Pendientes: composición por secciones de certificados/identidad, simplificación de opciones y disposición de firma, biblioteca de bóveda y pantallas de configuración Bridge/Suite. Mantener ICAO independiente y lectura unificada; no sustituir módulos por réplicas de los ejemplos HTML.

El diagnóstico R2 sigue vigente: el rediseño no demuestra por sí solo corrección de picos de medición/recomposición. Medir los cambios de pantalla, edición y biblioteca real durante cada etapa. Conservar confidencialidad de previews y flujo SAF→credenciales→autorización explícita.

## Validación y artefactos

APK73 / 3.0.0-alpha01-interface compilada e instalada por ADB, con las correcciones incluidas. Batería JVM app: 78 casos, 77 pasan, un enlace simbólico omitido por permisos Windows, cero fallos/errores. La batería Android final completa pasa: 28 pruebas en 35,398 s, incluyendo navegación nueva, lectura→CAN→cancelación, diferenciación PC, separación PDF/lotes, ICAO, bóveda/recuperación sintéticas y diagnósticos. El fallo inicial de scroll del test PC se corrige usando búsqueda dentro de LazyColumn: contenido no visible no está compuesto; la vuelta conserva posición de scroll.

Compilación Release Kotlin y manifest verificados, sin backend/provider diagnóstico debug. Check de capas e imports correcto. No se generó APK de distribución Release. Arranque normal final COLD por ADB: Status ok, MainActivity visible, TotalTime 1061 ms; no es un benchmark de fluidez. Captura visual final revisada con tema oscuro del teléfono; no se modifica su configuración de sistema. Se deja la app en Inicio.

Artefacto: D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.0.0-alpha01-interface-debug.apk. SHA256: 88D5C658B8B03C84F55820C7F2BE02F400A60DC446FC0489E47CE74741487384. Evidencia fuera de Git: D:/DNIeToolchain/Checkpoints/apk73-* y D:/DNIeToolchain/Diagnostics/apk73/. No se usa tarjeta real ni se envían intentos de PIN durante estas pruebas; las firmas con DNI físico siguen pendientes tras el rediseño.


Durante la prueba inicial se detectó NPE en Screen.items: inicializar primero el singleton Home ejecutaba el inicializador de su superclase mientras Home.INSTANCE aún era null. La lista eager guardaba ese null. Se corrigió con inicialización diferida (lazy) y se añadió NavigationDestinationsTest; el arranque frío corregido se verificó por ADB. El cierre era del grafo UI, no de los motores. La etiqueta Certificados se acortó a DNIe en la barra inferior tras revisión visual para evitar una letra aislada al final.
