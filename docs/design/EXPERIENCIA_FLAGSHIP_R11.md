# Experiencia Flagship R11 · 3.1.4 / APK 88

R11 simplifica la primera capa de la aplicación. Conserva los motores de DNIe, PACE, firma, TSA, bóveda y puente; cambia cómo se presentan las acciones y sus estados.

## Android

- `Mi DNIe` usa selectores compactos por tipo de certificado. La credencial muestra vigencia, titular, DNI y uso antes de sus detalles técnicos. La huella SHA-256 se agrupa y puede copiarse.
- La bóveda usa secciones directas (`Mis archivos`, `Usar DNI`, `Respaldo`, `Opciones`), estado de llave, CAN reutilizable, radar NFC durante operaciones y filtros visibles. Las fichas reconocen PDF, imagen, nota y otros archivos; carpeta y etiquetas se muestran sin abrir un modal.
- La conexión PC diferencia usos de aplicaciones PC/SC y de la Suite. La sesión presenta los tres extremos y el estado de cada enlace sin afirmar que el DNI está conectado si no lo está.
- El editor PDF reúne los controles principales y conserva espacio para el documento. Al mover o redimensionar el sello aplica la geometría compartida de ajuste y muestra sus dimensiones.
- PIN y CAN siguen usando campos accesibles; las cápsulas solo comunican cantidad de dígitos, sin revelar el secreto.

## Windows

- La Suite usa campos redondeados con foco visual; conservan sus nombres y tipos para accesibilidad y automatización.
- El diálogo PIN añade un contador de longitud sin exponer el PIN. El array devuelto sigue siendo transitorio y se limpia en las rutas de cancelación existentes.
- Las ventanas QR muestran los pasos mínimos, huella agrupada y copia explícita de huella. Copiar no crea una sesión ni autoriza una PC.

## Límites conservados

- Los indicadores visuales no son pruebas criptográficas ni sustituyen la confirmación del DNIe.
- Un estado de PC vinculada describe configuración. Solo una sesión activa puede describir un canal conectado.
- Ningún componente de presentación lee PIN, firma documentos ni inicia PACE por sí solo.
- El movimiento se limita a capas visuales; no promete una frecuencia constante de cuadros.

## Verificación esperada

Ejecutar la compilación Android y de Suite, las pruebas JVM existentes, el verificador de emparejamiento debug y pruebas instrumentadas en un teléfono desbloqueado. Las pruebas físicas de NFC, TSA, firma y suspensión siguen siendo independientes de esta revisión visual.
