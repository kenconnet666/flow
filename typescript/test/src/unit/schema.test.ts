import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { flowMigrations, MYSQL_SCRIPTS } from '@zerodep-dev/flow-core';

/** Java flow-core 的建表脚本：两个后端共用的那一份 */
const JAVA_SCRIPTS = join(import.meta.dirname, '..', '..', '..', '..', 'java', 'flow-core', 'src', 'main', 'resources', 'db', 'flow', 'mysql');

describe('建表脚本（Node 版的 migrateFlowSchema）', () => {
  it('与 Java 的同一份：改了 Java 的脚本后要跑 packages/core/scripts/sync-sql.mjs', () => {
    const java = readdirSync(JAVA_SCRIPTS)
      .filter((file) => file.endsWith('.sql'))
      .map((file) => ({ file, sql: readFileSync(join(JAVA_SCRIPTS, file), 'utf8').replaceAll('\r\n', '\n') }));
    const byFile = (a: { file: string }, b: { file: string }) => a.file.localeCompare(b.file);
    assert.deepEqual([...MYSQL_SCRIPTS].sort(byFile), java.sort(byFile));
  });

  it('按版本号排成迁移，版本号是文件名里的数字', () => {
    const versions = flowMigrations().map((m) => Number(m.version));
    assert.deepEqual(versions, [...versions].sort((a, b) => a - b));
    assert.equal(versions[0], 1);
  });
});
