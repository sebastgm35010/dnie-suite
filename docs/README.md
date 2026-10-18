# Índice de Documentación Canónica

Este directorio contiene la documentación técnica, arquitectónica y operativa oficial de **DNIe Suite**.

---

## 1. Estado y Operaciones
- [**Contexto Actual**](CONTEXTO_ACTUAL.md): Registro cronológico y estado operativo vigente del proyecto (`v3.1.5` / `APK 89`).
- [**Pendientes Actuales**](PENDIENTES_ACTUALES.md): Tareas pendientes, criterios de cierre y pruebas físicas en hardware real.
- [**Compilación y Clon de Repositorio**](operations/GITHUB_Y_COMPILACION_MONOREPO.md): Flujo de trabajo Git, preparación de entorno, JDK, Android SDK y comandos de compilación.
- [**Retomar tras Reinicio**](operations/RETOMAR_TRAS_REINICIO_RC4.md): Procedimiento de verificación de servicios y entorno de desarrollo.

---

## 2. Arquitectura y Capas del Sistema
- [**Arquitectura Actual 2.0**](architecture/ARQUITECTURA_ACTUAL_2_0.md): Mapa de capas del sistema (JVM Core, Protocolo IAS/PKI/PACE, Transporte Android, PAdES, UI Compose y Suite Windows).
- [**Corrección de Bugs y Bóveda R12**](architecture/CORRECCION_BUGS_Y_BOVEDA_R12.md): Informe de entrega de la versión 3.1.5 (APK 89) con la resolución de 17 defectos de estabilidad, concurrencia, SAF y bóveda.
- [**Contexto de la Suite de Firma**](suite/CONTEXTO_SUITE_FIRMA.md): Arquitectura de firma remota, integración con puente PC/SC y validación CAdES/PAdES.

---

## 3. Diseño y Experiencia de Usuario
- [**Sistema de Diseño General**](../DESIGN.md): Paleta de colores Claro/Oscuro, tipografía tabular y sistema de espaciado.
- [**Experiencia Flagship R11 y Fluid UI**](design/EXPERIENCIA_FLAGSHIP_R11.md): Especificación de movimiento orgánico, micro-escalas táctiles, curvas cúbicas y soporte de movimiento reducido.

---

## 4. Registro y Gobernanza Técnica
- [**Registro SQL del Proyecto**](project-registry/README.md): Especificación de la base de metadatos `project.sqlite3`, inventario de módulos y catálogo de funcionalidades.
- [**Reglas y Guías Operativas**](../AGENTS.md): Invariantes criptográficas, directrices de desarrollo y límites de seguridad.
