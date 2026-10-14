# Preparación del monorepo privado · 2026-10-13

Destino creado y privacidad consultada con GitHub CLI: [sebastgm35010/dnie-suite](https://github.com/sebastgm35010/dnie-suite). Autenticación por navegador con la cuenta del propietario; no se solicitó un PAT en el chat.

## Código y recuperación

Se conserva el historial del fork, la rama `baseline/1.0.8` y las etiquetas de checkpoint. La rama de trabajo `refactor/modularizacion` pasa a llamarse `main` sin reescribir commits. El punto previo es `checkpoint/pre-github-private-monorepo`, sobre `029ea8d`. La base local clausurada continúa fuera de esta carpeta y su remoto se llama `base-local`.

Android, Suite JVM, Bridge Windows y driver UMDF ya estaban en la misma raíz de fuentes. Se añade README, una guía de compilación y `tools/build_suite.py` como entrada común por componentes; no se fusionan sus motores ni se incorporan programas de terceros o duplicados de las revisiones externas. El contexto narrativo, mapa SQL, pruebas y licencias de dependencias forman parte del repositorio.

## Validaciones y alcance

- Arquitectura y sintaxis Python comprobadas.
- Compilación real `--component all --offline`: Android debug, Suite CLI/GUI, gestor/servicio Bridge y adaptador de integración. Resultados en `D:/DNIeToolchain/GitHubPreparation/monorepo-build`, fuera de Git. El driver no se recompila ni instala con `all`.
- Configuración local de firma debug/release validada. En copia aislada sin keystore privado: validación debug ejecutada y grafo release configurado con `--dry-run`; no se afirma haber generado allí un release.
- Comprobación de patrones del historial: 1707 blobs de texto, sin coincidencias con cuatro familias de secretos. [Resultado](GITHUB_PREFLIGHT_HISTORIAL.json). Es una búsqueda limitada, no una auditoría exhaustiva ni una garantía de ausencia de información sensible.
- No se agregan APK, PFX, keystores, perfiles de emparejamiento, capturas APDU ni datos de ejecución. No se instala esta compilación ni se modifica el servicio operativo.

Las huellas de productos generados y resultados de los comandos están en [GITHUB_PREPARACION_RESULTADOS.json](GITHUB_PREPARACION_RESULTADOS.json). Los logs de compilación quedan en D:, no en el repositorio. No se atribuyen pruebas físicas nuevas de NFC/firma a esta preparación.

## Verificar la publicación

```powershell
gh repo view sebastgm35010/dnie-suite --json isPrivate,defaultBranchRef,url
git rev-parse HEAD
git ls-remote origin refs/heads/main
python tools/project-registry/registry.py check
```

La privacidad debe ser `true`, la rama principal `main`, y el hash remoto debe coincidir con el commit local publicado. El registro SQL comprueba el inventario de fuentes/documentos; no consulta el estado vivo del servicio ni la tarjeta.

La portabilidad del instalador Windows a equipos sin D: permanece pendiente. Tampoco se asigna una licencia global nueva al proyecto durante esta publicación privada.
