"""Portable, offline SQLite architecture registry. Python standard library only."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DEFAULT_DB = ROOT / 'docs/project-registry/project.sqlite3'
GENERATED = {'docs/project-registry/project.sqlite3', 'docs/project-registry/snapshot.sql'}
EXTENSIONS = {'.kt', '.kts', '.java', '.cs', '.csproj', '.vcxproj', '.c', '.cpp', '.h',
              '.py', '.ps1', '.bat', '.cmd', '.md', '.sql', '.json', '.xml', '.toml', '.yml', '.yaml'}


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args]).decode('utf-8').strip()


def candidates(root):
    # Only tracked project metadata/source: never inspect external logs, credentials or APKs.
    paths = git(root, 'ls-files', '-z').split('\0')
    return sorted(p for p in paths if p and p not in GENERATED
                  and not any(part in {'build', '.gradle', '.idea', 'node_modules'} for part in Path(p).parts))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def layer(path):
    if '/src/test/' in path or '/src/androidTest/' in path:
        return 'test'
    if path.endswith('.md'):
        return 'documentation'
    if '/ui/' in path or path.startswith('dnie-signing-ui/'):
        return 'frontend'
    if '/data/' in path or path.startswith(('dnie-nfc/', 'dnie-passkeys-android/', 'dnie-bridge-android/')):
        return 'adapter'
    if '/domain/' in path or path.startswith('dnie-desktop-orchestration/'):
        return 'application'
    if path.startswith('tools/'):
        return 'windows-tool' if '/windows-' in path else 'tool'
    if path.startswith('dnie-'):
        return 'engine'
    return 'configuration'


def build(db_path, root=ROOT, catalog_path=None):
    catalog = json.loads((catalog_path or HERE / 'catalog.json').read_text(encoding='utf-8'))
    paths = candidates(root)
    missing = [p for p in paths if not (root / p).is_file()]
    if missing:
        raise ValueError('Tracked files absent: ' + ', '.join(missing))
    modules = sorted(set(re.findall(r'"\:([^"\s]+)"', (root / 'settings.gradle.kts').read_text(encoding='utf-8'))))
    windows = sorted({p.split('/')[1] for p in paths if p.startswith('tools/windows-')})
    db_path.parent.mkdir(parents=True, exist_ok=True)
    handle, temp = tempfile.mkstemp(prefix='registry-', suffix='.sqlite3', dir=db_path.parent)
    os.close(handle)
    con = sqlite3.connect(temp)
    try:
        con.executescript((HERE / 'schema.sql').read_text(encoding='utf-8'))
        con.executemany('INSERT INTO modules VALUES(?,?,?)',
                        [(m, 'gradle', f'{m}/build.gradle.kts') for m in modules] +
                        [(f'tools/{m}', 'windows', None) for m in windows])
        metadata = {'schema_version': '1', 'source_commit': git(root, 'rev-parse', 'HEAD'),
                    'branch_at_refresh': git(root, 'branch', '--show-current'),
                    'project': 'DnieSignerPeruEvolution',
                    'index_method': 'tracked files; lexical symbols/imports, not runtime call graph',
                    'catalog_review_date': catalog['review_date']}
        con.executemany('INSERT INTO metadata VALUES(?,?)', sorted(metadata.items()))
        for ref in git(root, 'for-each-ref', '--format=%(refname:short)|%(objectname)|%(subject)', 'refs/tags/checkpoint/').splitlines():
            name, commit, subject = ref.split('|', 2)
            con.execute('INSERT INTO checkpoints VALUES(?,?,?)', (name, commit, subject))
        for path in paths:
            file = root / path
            text = file.read_text(encoding='utf-8-sig', errors='replace') if file.suffix.lower() in EXTENSIONS else ''
            first = path.split('/')[0]
            module = first if first in modules else ('/'.join(path.split('/')[:2]) if path.startswith('tools/windows-') else None)
            con.execute('INSERT INTO files VALUES(?,?,?,?,?,?)',
                        (path, module, file.suffix.lower(), layer(path), digest(file), file.stat().st_size))
            if path.endswith('build.gradle.kts') and module in modules:
                for n, line in enumerate(text.splitlines(), 1):
                    for scope, target in re.findall(r'(\w+)\(project\("\:([^"\s]+)"\)\)', line):
                        con.execute('INSERT INTO module_dependencies VALUES(?,?,?,?)', (module, target, scope, n))
            if file.suffix in {'.kt', '.java', '.cs'}:
                pkg = re.search(r'^\s*(?:package|namespace)\s+([\w.]+)', text, re.M)
                pkg = pkg.group(1) if pkg else ''
                for n, line in enumerate(text.splitlines(), 1):
                    imp = re.match(r'\s*import\s+(?:static\s+)?([\w.*]+)', line)
                    if imp:
                        con.execute('INSERT OR IGNORE INTO imports VALUES(?,?,?)', (path, imp.group(1), n))
                    # Lexical locator only: nested types/functions are not compiler-resolved.
                    declaration = re.search(r'\b(class|interface|object|enum|fun)\s+([A-Za-z_][\w]*)', line)
                    if declaration:
                        kind, name = declaration.groups()
                        con.execute('INSERT INTO symbols(path,name,qualified_name,kind,line) VALUES(?,?,?,?,?)',
                                    (path, name, f'{pkg}.{name}' if pkg else name, kind, n))
            if file.suffix == '.md':
                title = next((line.lstrip('# ').strip() for line in text.splitlines() if line.startswith('# ')), file.stem)
                role = catalog['document_roles'].get(path, 'historical_or_reference')
                con.execute('INSERT INTO documents VALUES(?,?,?)', (path, title, role))
                for n, line in enumerate(text.splitlines(), 1):
                    if re.match(r'^#{1,6} ', line):
                        con.execute('INSERT INTO document_sections VALUES(?,?,?)', (path, n, line.lstrip('# ').strip()))
        for feature in catalog['features']:
            con.execute('INSERT INTO features VALUES(?,?,?,?,?)', tuple(feature[k] for k in ['id','name','status','note','evidence']))
            for role, patterns in feature['files'].items():
                for pattern in patterns:
                    # Select explicit paths or directories; no fuzzy guessed matches.
                    matched = [p for p in paths if p == pattern or (pattern.endswith('/') and p.startswith(pattern))]
                    if not matched:
                        raise ValueError(f"Empty feature mapping: {feature['id']} -> {pattern}")
                    con.executemany('INSERT OR IGNORE INTO feature_files VALUES(?,?,?)', [(feature['id'], p, role) for p in matched])
        for feature in catalog['features']:
            con.executemany('INSERT INTO feature_dependencies VALUES(?,?)', [(feature['id'], dep) for dep in feature.get('depends_on', [])])
        for item in catalog['pending']:
            con.execute('INSERT INTO pending VALUES(?,?,?,?,?)', tuple(item[k] for k in ['id','feature','state','description','evidence']))
        for item in catalog['facts']:
            con.execute('INSERT INTO facts VALUES(?,?,?,?)', tuple(item[k] for k in ['id','category','value','evidence']))
        if con.execute('PRAGMA foreign_key_check').fetchall():
            raise ValueError('Invalid foreign keys')
        con.commit()
        if con.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise ValueError('SQLite integrity failure')
        dump = '\n'.join(con.iterdump()) + '\n'
        con.close()
        os.replace(temp, db_path)
        db_path.with_name('snapshot.sql').write_text(dump, encoding='utf-8')
    finally:
        con.close()
        if Path(temp).exists():
            Path(temp).unlink()


@contextmanager
def connect(path):
    con = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)
    con.execute('PRAGMA query_only=ON')
    con.row_factory = sqlite3.Row
    try:
        yield con
    finally:
        con.close()


def check(con, root=ROOT):
    indexed = {row['path']: row['sha256'] for row in con.execute('SELECT path,sha256 FROM files')}
    current = set(candidates(root))
    changes = []
    for path, old in indexed.items():
        file = root / path
        if path not in current or not file.is_file():
            changes.append({'change': 'removed', 'path': path})
        elif digest(file) != old:
            changes.append({'change': 'modified', 'path': path})
    changes += [{'change': 'new', 'path': p} for p in sorted(current - indexed.keys())]
    return sorted(changes, key=lambda x: (x['path'], x['change']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', type=Path, default=DEFAULT_DB)
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ['refresh','check','status']:
        sub.add_parser(name)
    for name in ['feature','search','sql']:
        sub.add_parser(name).add_argument('value')
    args = parser.parse_args()
    if args.command == 'refresh':
        build(args.db)
        print(f'Registry updated: {args.db}')
        return 0
    with connect(args.db) as con:
        if args.command == 'check':
            changes = check(con)
            print(json.dumps({'integrity': con.execute('PRAGMA integrity_check').fetchone()[0],
                              'changes': changes}, ensure_ascii=False, indent=2))
            return 1 if changes else 0
        if args.command == 'status':
            result = {'metadata': dict(con.execute('SELECT key,value FROM metadata').fetchall()),
                      'counts': {t: con.execute(f'SELECT count(*) FROM {t}').fetchone()[0]
                                 for t in ['modules','files','symbols','documents','features','pending','checkpoints']},
                      'features': [dict(r) for r in con.execute('SELECT id,name,status,note FROM features ORDER BY id')],
                      'pending': [dict(r) for r in con.execute('SELECT * FROM v_open_pending')]}
        elif args.command == 'feature':
            result = {'feature': [dict(r) for r in con.execute('SELECT * FROM features WHERE id=?', (args.value,))],
                      'files': [dict(r) for r in con.execute('SELECT role,path,module,layer FROM v_feature_map WHERE id=? ORDER BY role,path', (args.value,))],
                      'depends_on': [r[0] for r in con.execute('SELECT target FROM feature_dependencies WHERE source=?', (args.value,))]}
        elif args.command == 'search':
            value = '%' + args.value + '%'
            result = {'files': [dict(r) for r in con.execute('SELECT path,module,layer FROM files WHERE path LIKE ? ORDER BY path', (value,))],
                      'symbols': [dict(r) for r in con.execute('SELECT path,name,kind,line FROM symbols WHERE name LIKE ? ORDER BY path,line', (value,))],
                      'documents': [dict(r) for r in con.execute('SELECT * FROM documents WHERE title LIKE ?', (value,))]}
        else:
            result = [dict(r) for r in con.execute(args.value)]
        print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, sqlite3.Error, OSError, subprocess.CalledProcessError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
