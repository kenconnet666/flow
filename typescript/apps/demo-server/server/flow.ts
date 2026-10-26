/**
 * 示例应用接上引擎（同 Java 的 examples/spring-boot）：应用自己只写了这几样——
 *   - 组织架构 {@link org}：用户、部门、角色，引擎没有默认的；
 *   - 当前操作人 {@link currentUser}：示例从请求头 X-User 取，**只为演示**；
 *   - 通知 {@link notifications}：监听流转记录，示例只打日志；
 *   - 自动节点的动作 {@link pushAction}：`demo.push`，能故意失败，演示异常处理。
 *
 * 连接从环境变量取，同 Java 示例：FLOW_MYSQL_URL（`jdbc:mysql://主机:端口/库`）、FLOW_MYSQL_USER、FLOW_MYSQL_PASSWORD。
 */
import { createFlowEngine, weekdays, type Actor, type FlowListener, type OrgDirectory, type ServiceAction } from '@zerodep-dev/flow-core';
import { FLOW_EVENT, FlowError, type FlowEvent, type Id } from '@zerodep-dev/flow-model';
import { mysqlDriver } from '@zerodep-dev/query-mysql';
import { getHeader, type H3Event } from 'h3';
import mysql from 'mysql2/promise';

/** 应用自己的用户类型：实现引擎的 Actor，扩展点里拿到的就是它 */
export interface ExampleUser extends Actor {
  readonly name: string;
}

const user = (id: bigint, name: string, departments: bigint[], roles: bigint[]): ExampleUser => ({
  id,
  name,
  departments: new Set(departments),
  roles: new Set(roles),
});

/**
 * 写死在内存里的几个人（虚构的门店采购场景），同 Java 示例的 ExampleOrgDirectory。真实应用在这里查自己的用户表、部门表、角色表。
 * 编号同 spec/scenarios 的约定：角色 1 财务、2 总经理；部门 10 门店、20 财务部、30 总经办。
 */
const USERS: readonly ExampleUser[] = [
  user(1001n, '林小满', [10n], []),
  user(1002n, '周启航', [20n], [1n]),
  user(1003n, '沈若水', [20n], [1n]),
  user(1004n, '总经理', [30n], [2n]),
  user(1005n, '许知远', [30n], []),
];

const org: OrgDirectory = {
  userName: (id) => USERS.find((u) => u.id === id)?.name ?? String(id),
  // 流程管理员：总经理（处理人为空、写了 emptyAssignee: admin 时转给他）
  administrators: () => [1004n],
  // 收件箱的负责人：示例里都是总经理（没人认领超时、写了 assignByManager 时由他指定）
  inboxManager: () => 1004n,
  // 有这个角色、在这个部门的人：会签的参与人、抄送节点的收件人写成角色、部门时展开
  members: (kind, id) =>
    USERS.filter((u) => (kind === 'role' ? u.roles : kind === 'department' ? u.departments : new Set<Id>()).has(id)).map((u) => u.id),
};

/**
 * 当前操作人：从请求头 X-User 取用户编号。**只为演示方便，不能用在生产**——
 * 真实应用从登录信息取（会话、令牌），登录与接口的保护由应用负责。
 */
export function currentUser(event: H3Event): ExampleUser {
  const header = getHeader(event, 'x-user');
  const found = header && /^\d+$/.test(header) ? USERS.find((u) => u.id === BigInt(header)) : undefined;
  if (!found) throw new FlowError('flow.forbidden', { params: { reason: 'not-signed-in' } });
  return found;
}

/** 通知：该通知谁就打一行日志。真实应用在这里发站内信、邮件……提交后才调（默认的 afterCommit），失败不影响流转 */
const notifications: FlowListener = {
  types: new Set([
    FLOW_EVENT.WORK_ITEM_CREATED,
    FLOW_EVENT.WORK_ITEM_REMINDED,
    FLOW_EVENT.WORK_ITEM_OVERDUE,
    FLOW_EVENT.INSTANCE_CC,
    FLOW_EVENT.INSTANCE_FINISHED,
    FLOW_EVENT.JOB_FAILED,
  ]),
  on({ event, instance }) {
    const title = instance.title;
    const data = (key: string, fallback: string) => String(event.data?.[key] ?? fallback);
    const texts: Record<string, (e: FlowEvent) => string> = {
      [FLOW_EVENT.WORK_ITEM_CREATED]: (e) => `新待办「${title}」${e.nodeName}，处理人 ${data('assigneeName', '收件箱')}`,
      [FLOW_EVENT.WORK_ITEM_REMINDED]: (e) => `催办「${title}」${e.nodeName}`,
      [FLOW_EVENT.WORK_ITEM_OVERDUE]: (e) => `超时「${title}」${e.nodeName}`,
      [FLOW_EVENT.JOB_FAILED]: (e) => `异常「${title}」${e.nodeName}：${data('error', '')}，通知管理员处理`,
      [FLOW_EVENT.INSTANCE_CC]: () => `抄送「${title}」给 ${data('userNames', '')}`,
    };
    const text = texts[event.type]?.(event) ?? `「${title}」已结束，结果 ${event.outcome}，通知发起人 ${instance.startedByName}`;
    console.info(`[通知] ${text}`);
  },
};

/**
 * 自动节点的动作 `demo.push`：假装把单据推给外部系统。参数 fail 为 true 时总是失败（演示重试用完后的"异常处理"），
 * 否则按参数 outcome 给结果（不写时走没写结果的出边）。
 */
const pushAction: ServiceAction = {
  name: 'demo.push',
  perform({ args }) {
    if (args['fail'] === true) throw new Error('外部系统不可用（示例故意失败）');
    return { outcome: args['outcome'] == null ? null : String(args['outcome']) };
  },
};

/** 连接池：连接串同 Java 的写法 */
function connect(): mysql.Pool {
  const url = process.env['FLOW_MYSQL_URL'] ?? '';
  const m = /^jdbc:mysql:\/\/([^:/]+)(?::(\d+))?\/([^?]+)/.exec(url);
  if (!m) throw new Error(`FLOW_MYSQL_URL must look like jdbc:mysql://host:port/database, got "${url}"`);
  return mysql.createPool({
    host: m[1]!,
    port: Number(m[2] ?? 3306),
    database: m[3]!,
    user: process.env['FLOW_MYSQL_USER'] ?? '',
    password: process.env['FLOW_MYSQL_PASSWORD'] ?? '',
    connectionLimit: 8,
    // 时间按 UTC 读写，同 Java 的 serverTimezone=UTC
    timezone: 'Z',
  });
}

export const driver = mysqlDriver(connect());

export const engine = createFlowEngine<ExampleUser>({
  driver,
  org,
  // "工作日"按北京时间的日期算
  calendar: weekdays('Asia/Shanghai'),
  listeners: [notifications],
  actions: [pushAction],
});
