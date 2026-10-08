# Registro SQL del proyecto

La base `project.sqlite3` registra el repositorio y su mapa de funcionalidades. Es SQLite estándar, portable, sin servidor y consultable con Python o cualquier cliente SQLite. Las rutas de fuentes/documentos son relativas a la raíz: no dependen de G: ni de una PC concreta. No forma parte de Android, Windows ni del motor de firma.

## Consultar

Desde la raíz del repositorio:

```powershell
python tools/project-registry/registry.py status
python tools/project-registry/registry.py feature pdf_android
python tools/project-registry/registry.py feature suite
python tools/project-registry/registry.py search CmsSignatureBuilder
python tools/project-registry/registry.py check
python tools/project-registry/registry.py sql "SELECT * FROM v_open_pending"
python tools/project-registry/registry.py sql "SELECT * FROM v_feature_map WHERE id='tsa'"
python tools/project-registry/registry.py sql "SELECT * FROM v_module_closure WHERE source='app'"
python tools/project-registry/registry.py sql "SELECT * FROM documents WHERE role<>'historical_or_reference'"
python tools/project-registry/registry.py sql "SELECT * FROM v_file_dependencies WHERE target LIKE '%CmsSignatureBuilder.kt'"
```

Las consultas abren la base en modo solo lectura. `check` compara rutas y SHA256 con archivos rastreados; devuelve código 1 si cambió el inventario y 2 ante errores. No considera vigente un snapshot porque la ruta exista solamente.

## Contenido y procedencia

- `files`, `modules`, `module_dependencies`: inventario Git y declaraciones `project(...)` de Gradle, incluyendo scope y línea.
- `symbols`, `imports`, `v_file_dependencies`: localizadores léxicos de declaraciones/importaciones. No son un grafo de llamadas ni una resolución completa de Kotlin/C#/Java; overloads, extensiones, tipos anidados e imports wildcard requieren revisar la fuente. Las líneas son del snapshot.
- `documents`, `document_sections`: rutas, títulos, encabezados y rol. `historical_or_reference` no significa estado actual. No se duplica todo el texto de los informes.
- `features`, `feature_files`, `feature_dependencies`: mapa revisado de opciones, archivos y motores compartidos. Un archivo puede pertenecer a varias funciones. Las dependencias funcionales expresan colaboración, no implican que una opción sea obligatoria.
- `pending`, `facts`: pendientes priorizados, validaciones, artefactos e invariantes revisados, cada uno con documento de evidencia. No extraer automáticamente todas las frases «pendiente» de la historia: varias ya fueron superadas.
- `checkpoints`: etiquetas checkpoint/ existentes y sus commits; no se incorporan archivos de respaldo externos a la base.
- `metadata`: versión de esquema, commit observado y fecha de revisión del catálogo. El commit observado es procedencia, no necesariamente el último commit (el snapshot puede incluir cambios preparados antes del commit).

Estados `validated_real`/`validated_real_manual` indican evidencia real del alcance descrito; `implemented_real_use` y `implemented_real_scan` no acreditan cada caso límite. `implemented_partial` conserva limitaciones explícitas. El registro no inspecciona servicios vivos, PIDs, NFC ni el estado de revocación actual de certificados.

## Actualizar y mantener

1. Actualizar los documentos vigentes y `tools/project-registry/catalog.json` cuando cambie una función, un pendiente o una evidencia.
2. Añadir las fuentes/documentos nuevos a Git (`git add`), porque el inventario solo incluye archivos rastreados/preparados.
3. Ejecutar `python tools/project-registry/registry.py refresh` y después `check`.
4. Versionar juntos catálogo, esquema, SQLite y `snapshot.sql`. Este último permite revisar diferencias textuales y reconstruir la base sin el generador: `sqlite3 nueva.sqlite3 < snapshot.sql`.

La reconstrucción valida referencias y mapeos no vacíos antes de sustituir la base, y no elimina el snapshot anterior ante un error. No escribir modificaciones manuales en SQLite: el catálogo es la fuente de los datos revisados; el código es la fuente del inventario. El archivo SQL es una exportación, no una segunda fuente editable.

Al retomar trabajo, consultar `status`, ejecutar `check` y leer los documentos marcados como vigentes más AGENTS.md. Mantener los documentos narrativos: una tabla no reemplaza detalles de protocolo o evidencia.

Pruebas del registro: `python -m unittest discover -s tools/project-registry -p test_registry.py -v`. No requiere paquetes adicionales ni instalación en C:.

No indexa logs externos, APKs, almacenes de claves ni datos del DNI; solo metadatos y fuentes rastreadas. Los archivos binarios rastreados solo aportan ruta, tamaño y SHA256. La extracción léxica se limita a formatos declarados.
