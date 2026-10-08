PRAGMA foreign_keys = ON;
CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE checkpoints(name TEXT PRIMARY KEY, commit_hash TEXT NOT NULL, subject TEXT NOT NULL);
CREATE TABLE modules(id TEXT PRIMARY KEY, kind TEXT NOT NULL, build_path TEXT);
CREATE TABLE files(path TEXT PRIMARY KEY, module TEXT REFERENCES modules(id), kind TEXT NOT NULL,
 layer TEXT NOT NULL, sha256 TEXT NOT NULL, bytes INTEGER NOT NULL);
CREATE TABLE module_dependencies(source TEXT REFERENCES modules(id), target TEXT REFERENCES modules(id),
 scope TEXT NOT NULL, line INTEGER NOT NULL, PRIMARY KEY(source,target,scope,line));
CREATE TABLE symbols(id INTEGER PRIMARY KEY, path TEXT NOT NULL REFERENCES files(path),
 name TEXT NOT NULL, qualified_name TEXT NOT NULL, kind TEXT NOT NULL, line INTEGER NOT NULL);
CREATE INDEX symbols_qualified ON symbols(qualified_name);
CREATE TABLE imports(path TEXT REFERENCES files(path), target TEXT NOT NULL, line INTEGER NOT NULL,
 PRIMARY KEY(path,target,line));
CREATE TABLE documents(path TEXT PRIMARY KEY REFERENCES files(path), title TEXT NOT NULL, role TEXT NOT NULL);
CREATE TABLE document_sections(path TEXT REFERENCES documents(path), line INTEGER, heading TEXT,
 PRIMARY KEY(path,line));
CREATE TABLE features(id TEXT PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL,
 evidence TEXT NOT NULL REFERENCES documents(path));
CREATE TABLE feature_files(feature TEXT REFERENCES features(id), path TEXT REFERENCES files(path),
 role TEXT NOT NULL, PRIMARY KEY(feature,path,role));
CREATE TABLE feature_dependencies(source TEXT REFERENCES features(id), target TEXT REFERENCES features(id),
 PRIMARY KEY(source,target));
CREATE TABLE pending(id TEXT PRIMARY KEY, feature TEXT REFERENCES features(id), state TEXT NOT NULL,
 description TEXT NOT NULL, evidence TEXT REFERENCES documents(path));
CREATE TABLE facts(id TEXT PRIMARY KEY, category TEXT NOT NULL, value TEXT NOT NULL,
 evidence TEXT REFERENCES documents(path));
CREATE VIEW v_feature_map AS SELECT x.id,x.name,x.status,f.role,f.path,a.module,a.layer,x.evidence
 FROM features x JOIN feature_files f ON f.feature=x.id JOIN files a ON a.path=f.path;
-- These are syntactic import resolutions, not a runtime call graph.
CREATE VIEW v_file_dependencies AS SELECT DISTINCT i.path AS source,s.path AS target,i.target AS imported_symbol
 FROM imports i JOIN symbols s ON s.qualified_name=i.target WHERE i.path<>s.path;
CREATE VIEW v_module_closure AS WITH RECURSIVE links(source,target) AS (
 SELECT source,target FROM module_dependencies WHERE scope NOT LIKE 'test%'
 UNION SELECT l.source,d.target FROM links l JOIN module_dependencies d ON d.source=l.target
 WHERE d.scope NOT LIKE 'test%') SELECT * FROM links;
CREATE VIEW v_open_pending AS SELECT * FROM pending WHERE state<>'closed';
