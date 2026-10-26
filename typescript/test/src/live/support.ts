/**
 * 实库测试的支撑（同 Java flow-it 的 LiveMysql、TestOrg、TestUser、TestExtensions、MovableClock）：
 *   - 连接变量同 Java：FLOW_MYSQL_URL（`jdbc:mysql://主机:端口/库`）、FLOW_MYSQL_USER、FLOW_MYSQL_PASSWORD；没设时跳过，设了 FLOW_REQUIRE_LIVE 时失败；
 *   - 每个场景前删掉 flow_ 开头的表，再执行建表脚本（与 Java 用同一份脚本，自增的编号从 1 开始，两个后端跑出来的编号相同）；
 *   - 场景里的扩展：解析器、守卫、动作、命令拦截器，同 spec/scenarios 的 README 那张表。
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { FlowError, type Id } from '@zerodep-dev/flow-model';
import type { Actor, Clock, CommandInterceptor, OrgDirectory, OutcomeGuard, PeopleResolver, ServiceAction } from '@zerodep-dev/flow-core';
import mysql from 'mysql2/promise';

/** 建表脚本（Java flow-core 的资源目录，两个后端共用） */
const SCRIPTS = join(import.meta.dirname, '..', '..', '..', '..', 'java', 'flow-core', 'src', 'main', 'resources', 'db', 'flow', 'mysql');

/** 实库的连接池；没配置时为 null（调用方跳过） */
export function livePool(): mysql.Pool | null {
  const url = process.env['FLOW_MYSQL_URL'];
  const user = process.env['FLOW_MYSQL_USER'];
  const password = process.env['FLOW_MYSQL_PASSWORD'];
  if (!url || !user || password === undefined) {
    if (process.env['FLOW_REQUIRE_LIVE']) throw new Error('FLOW_MYSQL_URL/_USER/_PASSWORD not set but FLOW_REQUIRE_LIVE is');
    return null;
  }
  const m = /^jdbc:mysql:\/\/([^:/]+)(?::(\d+))?\/([^?]+)/.exec(url);
  if (!m) throw new Error(`FLOW_MYSQL_URL must look like jdbc:mysql://host:port/database, got ${url}`);
  return mysql.createPool({ host: m[1]!, port: Number(m[2] ?? 3306), database: m[3]!, user, password, connectionLimit: 4, timezone: 'Z' });
}

/** 删掉 flow_ 的表，再按建表脚本建表 */
export async function recreateTables(pool: mysql.Pool): Promise<void> {
  const [tables] = (await pool.query("SELECT table_name AS name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'flow\\_%'")) as unknown as [{ name: string }[]];
  for (const { name } of tables) await pool.query(`DROP TABLE \`${name}\``);
  const files = readdirSync(SCRIPTS).filter((f) => /^V\d+__.*\.sql$/.test(f)).sort((a, b) => version(a) - version(b));
  for (const file of files) {
    const script = readFileSync(join(SCRIPTS, file), 'utf8').replace(/^\s*--.*$/gm, '');
    for (const statement of script.split(/;\s*(?:\r?\n|$)/)) if (statement.trim()) await pool.query(statement);
  }
}

const version = (file: string) => Number(/^V(\d+)__/.exec(file)![1]);

/** 测试里的操作人 */
export interface TestUser extends Actor {
  readonly name: string;
}

/** 测试里的组织架构：就是这几个人。1000 是流程管理员；有角色 9 的人是所有收件箱的负责人 */
export function testOrg(users: readonly TestUser[]): OrgDirectory {
  return {
    userName: (id) => users.find((u) => u.id === id)?.name ?? String(id),
    administrators: () => users.filter((u) => u.id === 1000n).map((u) => u.id),
    inboxManager: () => users.find((u) => u.roles.has(9n))?.id ?? null,
    members: (kind, id) => users.filter((u) => (kind === 'role' ? u.roles : kind === 'department' ? u.departments : new Set<Id>()).has(id)).map((u) => u.id),
  };
}

/** 能往前拨的固定时钟 */
export class MovableClock implements Clock {
  #at: number;

  constructor(start: string) {
    this.#at = Date.parse(start);
  }

  now(): Date {
    return new Date(this.#at);
  }

  /** 拨到这一刻（不能往回拨） */
  moveTo(at: string): void {
    const next = Date.parse(at);
    if (next < this.#at) throw new Error(`the clock cannot go back to ${at}`);
    this.#at = next;
  }
}

const invalid = (field: string, code: string) => new FlowError('flow.validation-failed', { message: `${field}: ${code}`, errors: [{ code, field }] });

/**
 * 场景里用的扩展（每个场景一份，失败的次数按场景计）：
 *   - 解析器 manager：谁发起都由总经理（1004）处理；守卫 budget：金额超过 100000 时不通过，原因"超出预算"；
 *   - 动作 classify：按 amount 分大小；archive：同一个去重键的前 failures 次失败；explode：总是失败；
 *   - 命令拦截器：意见是"拦下"的处理拦下；留言里的"敏感词"换成"***"；业务编号 rollback-after 的发起做完再拦。
 */
export function testExtensions() {
  const failures = new Map<string, number>();
  const resolvers: PeopleResolver[] = [{ name: 'manager', resolve: () => [1004n] }];
  const guards: OutcomeGuard[] = [{ name: 'budget', check: (c) => (Number(c.form['amount'] ?? 0) > 100000 ? '超出预算' : null) }];
  const actions: ServiceAction[] = [
    {
      name: 'classify',
      perform: (c) => {
        const big = Number(c.args['amount']) >= 10000;
        return { outcome: big ? 'big' : 'small', variables: { level: big ? 'big' : 'small' } };
      },
    },
    {
      name: 'archive',
      perform: (c) => {
        const allowed = Number(c.args['failures'] ?? 0);
        const failed = failures.get(c.idempotencyKey) ?? 0;
        if (failed < allowed) {
          failures.set(c.idempotencyKey, failed + 1);
          throw new Error('档案系统暂时不可用');
        }
        return { variables: { archived: true } };
      },
    },
    {
      name: 'explode',
      perform: () => {
        throw new Error('ERP 不可用');
      },
    },
  ];
  const interceptors: CommandInterceptor<TestUser>[] = [
    async (_actor, operation, command, proceed) => {
      const c = command as Record<string, unknown>;
      if (operation === 'complete' && c['comment'] === '拦下') throw invalid('comment', 'blocked');
      if (operation === 'comment' && typeof c['text'] === 'string') return proceed({ ...c, text: c['text'].replaceAll('敏感词', '***') });
      const result = await proceed(command);
      if (operation === 'start' && c['businessKey'] === 'rollback-after') throw invalid('businessKey', 'rolled-back');
      return result;
    },
  ];
  return { resolvers, guards, actions, interceptors };
}
