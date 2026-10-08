import json
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import unittest

import registry


class RegistryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        contents = {
            'settings.gradle.kts': 'include(":app", ":core")',
            'app/build.gradle.kts': 'implementation(project(":core"))',
            'core/build.gradle.kts': '',
            'core/src/main/Engine.kt': 'package test\nclass Engine\nfun sign() = 1',
            'app/src/main/Ui.kt': 'package test.ui\nimport test.Engine\nclass Screen',
            'docs/state.md': '# State\n## Current\n',
        }
        for name, content in contents.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
        subprocess.run(['git', '-C', str(self.root), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-qm', 'fixture'], check=True)
        self.catalog = self.root / 'catalog.json'
        self.catalog.write_text(json.dumps({'review_date': '2026-10-07', 'document_roles': {'docs/state.md':'current_state'},
            'features': [{'id':'sign', 'name':'Sign', 'status':'synthetic', 'note':'fixture', 'evidence':'docs/state.md',
                          'files': {'engine':['core/src/main/']}}], 'pending':[], 'facts':[]}), encoding='utf-8')
        self.db = self.root / 'output/project.sqlite3'
        registry.build(self.db, self.root, self.catalog)

    def test_maps_dependencies_and_readonly_queries(self):
        with registry.connect(self.db) as con:
            self.assertEqual(1, con.execute('SELECT count(*) FROM v_file_dependencies').fetchone()[0])
            self.assertEqual(('app','core'), tuple(con.execute('SELECT * FROM v_module_closure').fetchone()))
            self.assertEqual([], registry.check(con, self.root))
            with self.assertRaises(sqlite3.OperationalError):
                con.execute('DELETE FROM files')

    def test_detects_changed_deleted_and_new_sources(self):
        (self.root / 'core/src/main/Engine.kt').write_text('changed', encoding='utf-8')
        (self.root / 'app/src/main/Ui.kt').unlink()
        (self.root / 'new.md').write_text('# New', encoding='utf-8')
        subprocess.run(['git','-C',str(self.root),'add','new.md'], check=True)
        with registry.connect(self.db) as con:
            self.assertEqual({'modified','removed','new'}, {row['change'] for row in registry.check(con, self.root)})

    def test_invalid_mapping_keeps_previous_database(self):
        before = self.db.read_bytes()
        catalog = json.loads(self.catalog.read_text(encoding='utf-8'))
        catalog['features'][0]['files']['engine'] = ['missing.kt']
        self.catalog.write_text(json.dumps(catalog), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Empty feature mapping'):
            registry.build(self.db, self.root, self.catalog)
        self.assertEqual(before, self.db.read_bytes())

    def test_sql_export_restores_registry(self):
        with sqlite3.connect(':memory:') as con:
            con.executescript(self.db.with_name('snapshot.sql').read_text(encoding='utf-8'))
            self.assertEqual('ok', con.execute('PRAGMA integrity_check').fetchone()[0])
            self.assertEqual([], con.execute('PRAGMA foreign_key_check').fetchall())
            self.assertEqual(1, con.execute('SELECT count(*) FROM features').fetchone()[0])

    def test_paths_remain_relative_and_refresh_is_deterministic(self):
        before = self.db.with_name('snapshot.sql').read_bytes()
        registry.build(self.db, self.root, self.catalog)
        self.assertEqual(before, self.db.with_name('snapshot.sql').read_bytes())
        with registry.connect(self.db) as con:
            self.assertTrue(all(not Path(row[0]).is_absolute() for row in con.execute('SELECT path FROM files')))


if __name__ == '__main__':
    unittest.main()
