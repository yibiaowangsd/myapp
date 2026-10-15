#!/usr/bin/env python3
"""Check the exported Room SQL with SQLite, including FTS and uniqueness."""
import json,sqlite3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
schema=json.loads((root/'app/schemas/com.personal.wo.data.WoDatabase/1.json').read_text())['database']
sql=[]
for e in schema['entities']:
 sql.append(e['createSql'].replace('${TABLE_NAME}',e['tableName'])+';')
 for index in e.get('indices',[]):sql.append(index['createSql'].replace('${TABLE_NAME}',e['tableName'])+';')
target=root/'contracts/schema.sql';target.parent.mkdir(exist_ok=True);target.write_text('-- Generated from Room V1; the Room schema is authoritative.\nPRAGMA foreign_keys=ON;\n'+'\n'.join(sql)+'\n')
db=sqlite3.connect(':memory:');db.executescript(target.read_text())
assert len(schema['entities'])==17
db.execute('INSERT INTO observation VALUES (?,?,?,?,?,?,?,?)',('a','USAGE','key','RESUMED',1,1,1,'{}'))
try:db.execute('INSERT INTO observation VALUES (?,?,?,?,?,?,?,?)',('b','USAGE','key','RESUMED',1,1,1,'{}'));raise AssertionError('source key uniqueness missing')
except sqlite3.IntegrityError:pass
db.execute('INSERT INTO event_search(episodeId,tokens) VALUES (?,?)',('a','今天 使用 tls'))
assert db.execute('SELECT episodeId FROM event_search WHERE tokens MATCH ?',('今天 AND tls',)).fetchone()[0]=='a'
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS: 17 entities, generated DDL, source-key uniqueness, FTS4, integrity_check')
