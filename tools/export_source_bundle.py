"""Herramienta de exportación ligera del código fuente y documentación completa.

Empaqueta automáticamente el código fuente y toda la documentación (incluyendo
la documentación interna en docs/ y reglas en AGENTS.md), excluyendo cachés,
historial git y artefactos pesados de compilación (.git, build, .gradle, bin, obj, apks).

Hereda dinámicamente la versión desde gradle.properties y guarda el archivo en /zip.
"""
from pathlib import Path
import argparse
import os
import re
import sys
import zipfile

# Asegurar compatibilidad UTF-8 en consolas Windows
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parents[1]

EXCLUDE_DIRS = {
    '.git',
    '.gradle',
    '.kotlin',
    '.idea',
    'build',
    'bin',
    'obj',
    '.cxx',
    '.externalNativeBuild',
    'captures',
    'dist',
    'out',
    'node_modules',
    '__pycache__',
    'zip',
}

EXCLUDE_EXTS = {
    '.apk',
    '.aab',
    '.zip',
    '.7z',
    '.tar',
    '.gz',
    '.hprof',
    '.jks',
    '.keystore',
    '.pfx',
    '.p12',
}

EXCLUDE_FILES = {
    'local.properties',
}


def read_app_version(root=ROOT) -> str:
    props_path = root / 'gradle.properties'
    if not props_path.is_file():
        raise FileNotFoundError(f'No se encontró gradle.properties en {props_path}')
    text = props_path.read_text(encoding='utf-8')
    match = re.search(r'^dnie\.versionName\s*=\s*(.+)$', text, re.M)
    if not match:
        raise ValueError('No se encontró la propiedad dnie.versionName en gradle.properties')
    return match.group(1).strip()


def export_bundle(output_dir=None, custom_name=None, root=ROOT):
    version = read_app_version(root)
    out_dir = Path(output_dir) if output_dir else (root / 'zip')
    out_dir.mkdir(parents=True, exist_ok=True)

    zip_filename = custom_name or f'dnie-suite-v{version}-full-source.zip'
    if not zip_filename.endswith('.zip'):
        zip_filename += '.zip'
    target_zip = out_dir / zip_filename

    prefix = f'dnie-suite-v{version}/'
    count = 0
    uncompressed_bytes = 0

    print(f'Exportando paquete de fuentes DNIe Suite v{version}...')
    print(f'Destino: {target_zip}')

    with zipfile.ZipFile(target_zip, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for dirpath, dirnames, filenames in os.walk(root):
            # Filtrar directorios excluidos in-place
            dirnames[:] = [
                d for d in dirnames
                if d not in EXCLUDE_DIRS and not d.startswith('.git')
            ]

            for fname in filenames:
                ext = os.path.splitext(fname)[1].lower()
                if ext in EXCLUDE_EXTS or fname in EXCLUDE_FILES:
                    continue

                full_path = Path(dirpath) / fname
                # Evitar incluir el archivo zip de destino si coincide
                if full_path.resolve() == target_zip.resolve():
                    continue

                rel_path = full_path.relative_to(root)
                arcname = prefix + str(rel_path).replace('\\', '/')

                try:
                    size = full_path.stat().st_size
                    zf.write(full_path, arcname=arcname)
                    count += 1
                    uncompressed_bytes += size
                except Exception as e:
                    print(f'Aviso: no se pudo empaquetar {full_path}: {e}', file=sys.stderr)

    zip_size = target_zip.stat().st_size
    savings = (1 - (zip_size / uncompressed_bytes)) * 100 if uncompressed_bytes > 0 else 0

    print('\n[OK] Exportacion completada con exito:')
    print(f'  - Versión del producto: {version}')
    print(f'  - Archivos empaquetados: {count}')
    print(f'  - Tamaño descomprimido: {uncompressed_bytes / (1024 * 1024):.2f} MB')
    print(f'  - Tamaño comprimido (.zip): {zip_size / (1024 * 1024):.2f} MB')
    print(f'  - Tasa de compresión: {savings:.1f}% de reducción')
    print(f'  - Archivo final: {target_zip.resolve()}\n')
    return target_zip


def main():
    parser = argparse.ArgumentParser(
        description='Exporta automáticamente una versión ligera del código fuente y documentación completa.'
    )
    parser.add_argument(
        '--output-dir',
        default=None,
        help='Directorio de destino (por defecto: carpeta /zip en la raíz del proyecto).'
    )
    parser.add_argument(
        '--name',
        default=None,
        help='Nombre personalizado para el archivo .zip.'
    )
    args = parser.parse_args()
    export_bundle(output_dir=args.output_dir, custom_name=args.name)


if __name__ == '__main__':
    main()
