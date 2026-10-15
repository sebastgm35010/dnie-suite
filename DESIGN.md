# Aether · DNIe Evolución y DNIe Suite

## 1. Propósito y tono

Una interfaz sobria que permita leer el documento, firmar y proteger archivos con acciones comprensibles. La presentación describe estados reales del motor; nunca sustituye una validación criptográfica. Este documento establece nueve apartados propios del proyecto y complementa AGENTS.md.

## 2. Colores y superficies

Tokens compartidos por Compose, Swing y WinForms. No fijar claro u oscuro: conservar la elección del usuario y, en Android, la opción del sistema.

| Rol | Claro | Oscuro |
| --- | --- | --- |
| Canvas | #F8F9FF | #0B1326 |
| Superficie 1 | #FFFFFF | #131B2E |
| Superficie 2 | #EFF4FF | #1D2840 |
| Superficie 3 | #E5EEFF | #243554 |
| Borde fino | #DCE9FF | #2E3C56 |
| Primario | #004AC6 | #6B9EFF |
| Sobre primario | #FFFFFF | #00174B |
| Éxito | #006A63 | #67D9C8 |
| Fondo de éxito | #D0F7F0 | #123D39 |
| Texto | #0B1C30 | #EAF1FF |
| Texto secundario | #434655 | #A9B5CB |
| Contorno | #737686 | #8D94A6 |
| Advertencia | #D97706 | #FBBF24 |
| Error | #BA1A1A | #FFB4AB |

Escalera adicional Compose oscura: lowest #080E1C, low #131B2E, container #1D2840, high #243554, highest #2E4063. Tarjetas con borde de 1dp/px y elevación tonal; conservar colores semánticos. Blanco del documento PDF permanece blanco independientemente del tema de la aplicación. Advertencias incluyen texto/icono; el ámbar claro no se usa como único texto pequeño sobre blanco.

## 3. Tipografía

Sans del sistema en Android, Segoe UI en Windows. Categorías en formato oración y peso medio. Monoespaciada para huellas, hexadecimales, direcciones y otros valores técnicos. Números tabulares (`tnum`) en estilos de formularios, páginas, tamaños y contadores. No cambiar las fuentes de documentos firmados por preferencias del tema.

## 4. Geometría y componentes

Radios: 8 para inputs/controles compactos; 12 para botones y tarjetas; 16 para hero/paneles; 24 en esquinas superiores de hojas modales. Evitar sombras pesadas, bordes dobles y listas verticales dentro de otro desplazamiento vertical. Reutilizar AetherCard, FeatureSection y componentes Aether de escritorio.

## 5. Interacción y accesibilidad

Objetivos móviles de al menos 48dp y controles de escritorio de al menos 36px. Foco de teclado visible; etiquetas accesibles en acciones con iconos. Contraste de texto normal de al menos 4.5:1. No usar blanco sobre primario oscuro #6B9EFF: usar #00174B. Estado comunicado con texto además de color. Respetar tamaño de letra, teclado y desplazamiento.

## 6. Movimiento

Resorte compartido: rigidez 380 y amortiguación .82; presión .98 con rigidez 500. Frame values repetidos se leen en dibujo (`graphicsLayer`/Canvas), sin recomponer texto por cada frame. Radar de dos anillos solo durante espera/lectura; color esmeralda al detectar DNI y resorte de éxito al completar. No inventar porcentajes en PACE/TSA. Pestañas principales instantáneas; configuración puede usar desvanecimiento breve.

Observar la escala de animación de Android sin modificarla. Con movimiento reducido: no crear bucles infinitos, escala estable y transiciones inmediatas. La preferencia se adapta en la capa Android; el motor nunca depende de animaciones ni vibración.

R10: selectores segmentados compartidos, indicador consumido en dibujo y transiciones direccionales finitas. Elementos de firma con claves estables y animación de lista desactivada con movimiento reducido. Hover de escritorio finito (140 ms, temporizador coalescente de 16 ms), detenido al completar u ocultar/disponer el control. No llamar 60 FPS garantizados a ese temporizador: medir cuadros reales y declarar muestra, percentiles y limitaciones.

## 7. Navegación y primer inicio

Cinco destinos principales: Inicio, Mi DNIe, Firmar, Bóveda y PC. Guía de tres pasos solo por acción explícita: CAN opcional, colocación NFC y móvil/Windows. Tarjeta de bienvenida ocultable y persistente; no aparece si hay CAN recordado o datos leídos. Configuración permite reabrir la guía. Guardar CAN es una acción explícita y asíncrona; no pedir PIN ni iniciar NFC en la guía. Explicar que la antena varía por teléfono y que el resultado en pantalla autoriza retirar el DNI. No prometer descubrimiento en redes que lo bloquean, vibración disponible ni instalación cero del lector Windows.

## 8. Límites entre interfaz y motores

Compose/Swing/WinForms presentan estado y emiten acciones. ViewModels coordinan persistencia/adaptadores; motores mantienen formato, autorización, sesión y criptografía. No cambiar PIN, presupuesto de firmas, reintentos ni secuencias APDU para conseguir una animación. Guía de escritorio sin conexiones ni cambios de servicio. Errores requieren la autorización prevista por el flujo; jamás repetir automáticamente una firma.

R10: el PIN de Suite se introduce en un diálogo propio tras preflight y se confirma explícitamente. Cancelar/Escape/cerrar limpian el campo; el controlador recibe y borra el CharArray confirmado. Una tarjeta de PC vinculada describe identidad/configuración, no una conexión activa. Autocompletar CAN no inicia sesiones ni sustituye una edición del usuario.

## 9. Verificación y mantenimiento

Versionar entregas en gradle.properties y conservar la firma de actualización. Comprobar claro/oscuro, guía optativa, navegación, movimiento reducido, foco y estados NFC mediante fixtures aislados; las pruebas sintéticas no acreditan una firma física. Actualizar contexto, catálogo SQL y resultados verificables. Compilaciones/evidencias pesadas en D; código/documentación y evidencias de entrega en G. Referencias de API: [Compose Animation](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary), [Settings.Global](https://developer.android.com/reference/android/provider/Settings.Global).
