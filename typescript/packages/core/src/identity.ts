/**
 * 操作人与组织架构（0006 第 2、6 节）：接入方自己的用户类型实现 {@link Actor}，组织架构实现 {@link OrgDirectory}。
 * 编号都是接入方组织表的自增主键（bigint）。
 */
import type { Id } from '@zerodep-dev/flow-model';

/** 操作人：角色收件箱、部门收件箱按 roles、departments 匹配候选，所以换了角色的人马上看得到 */
export interface Actor {
  /** 用户编号 */
  readonly id: Id;
  /** 显示的名字，存进待办、流转记录的快照；不给时用编号 */
  readonly name?: string;
  /** 所在部门：要让上级部门的待办也能被认领，就把上级部门一起给出 */
  readonly departments: ReadonlySet<Id>;
  /** 拥有的角色 */
  readonly roles: ReadonlySet<Id>;
  /** 接入方扩展的候选种类（岗位、项目组……）里的归属：种类 → 编号 */
  readonly groups?: Readonly<Record<string, ReadonlySet<Id>>>;
}

/** 操作人的名字（没给时是编号） */
export const actorName = (actor: Actor): string => actor.name ?? String(actor.id);

/** 某一种扩展种类里的归属 */
export const actorGroups = (actor: Actor, kind: string): ReadonlySet<Id> => actor.groups?.[kind] ?? new Set();

/** 当前操作人从哪来（Nuxt 里从会话取） */
export type ActorProvider<U extends Actor> = () => U;

/**
 * 接入方的组织架构：引擎没有默认实现。处理人解析成人时存名字快照（userName）；会签的参与人写了角色、部门时展开成人（members）；
 * 处理人为空时转给管理员（administrators）；收件箱很久没人认领时转负责人指定（inboxManager）。
 */
export interface OrgDirectory {
  /** 用户的显示名字；查不到时返回编号本身 */
  userName(userId: Id): string;
  /** 一个角色、部门（或扩展的种类）下的人：会签、抄送写成角色、部门时展开；不给时会签按角色写会报错 */
  members?(kind: string, id: Id): readonly Id[];
  /** 流程管理员：处理人解析为空、定义写了 `emptyAssignee: "admin"` 时待办转给他们 */
  administrators?(): readonly Id[];
  /** 一个收件箱的负责人：无人认领超时、`unclaimed.action: "assignByManager"` 时由他指定承办人 */
  inboxManager?(kind: string, id: Id): Id | null;
}

/**
 * 每个操作前的权限判断（0006 第 6 节）：返回 false 时报 `flow.forbidden`。默认全部放行。
 *
 * @param operation 同 Java 的 PermissionPolicy：`start`、`complete`、`todo`、`instances.search`……
 * @param target    操作的对象：流程标识、待办编号或实例编号
 */
export type PermissionPolicy<U extends Actor> = (actor: U, operation: string, target: string | null) => boolean;

/** 全部放行 */
export const allowAll = (): boolean => true;
