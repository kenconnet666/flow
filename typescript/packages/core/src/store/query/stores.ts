/**
 * 引擎存储的默认实现：用 query 读写 `flow_*` 表（同 Java 的 store.query 包）。状态的变化都写成带条件的更新（改到 0 行就是别人先改了），
 * 收件箱分"落到我的"与"我能认领的"两段查再合并（0007 第 4 节）。
 *
 *   const stores = queryStores(flowDatabase(mysqlDriver(pool), () => 0n, systemClock));
 */
import type { Cc, FlowEvent, Id, WorkItem } from '@zerodep-dev/flow-model';
import { all, any, deleteFrom, from, insertInto, update, type Database, type Page, type Ref } from '@zerodep-dev/query-core';
import { RunState } from '../../run-state.js';
import type { Instant } from '../../time.js';
import {
  CANDIDATE,
  JOB_STATE,
  STATUS,
  type CcStore,
  type Candidate,
  type DefinitionPublication,
  type DefinitionStore,
  type EventStore,
  type FormDataStore,
  type FormPublicationRecord,
  type FormStore,
  type InstanceRecord,
  type InstanceSearch,
  type InstanceStore,
  type Job,
  type JobStore,
  type Member,
  type NewCc,
  type NewJob,
  type StepSnapshot,
  type StepStore,
  type StoredDefinition,
  type StoredForm,
  type Stores,
  type WorkItemStore,
} from '../types.js';
import { TENANT_FILTER } from './database.js';
import {
  CandidateRow,
  CcRow,
  DefinitionRow,
  EventRow,
  FormDataRow,
  FormRow,
  FormVersionRow,
  InstanceRow,
  JobRow,
  ProcessRow,
  StepRow,
  WorkItemRow,
} from './tables.js';

/** 全部存储都用 query 的默认实现 */
export function queryStores(db: Database): Stores {
  return {
    definitions: new QueryDefinitionStore(db),
    forms: new QueryFormStore(db),
    formData: new QueryFormDataStore(db),
    instances: new QueryInstanceStore(db),
    workItems: new QueryWorkItemStore(db),
    events: new QueryEventStore(db),
    jobs: new QueryJobStore(db),
    ccs: new QueryCcStore(db),
    steps: new QueryStepStore(db),
  };
}

/** 页里的每一行换个样子 */
const mapPage = <R, T>(page: Page<R>, map: (row: R) => T): Page<T> => ({ ...page, rows: page.rows.map(map) });

/** 空对象存成 NULL（摘要列、附加数据，同 Java） */
const nullIfEmpty = (value: Readonly<Record<string, unknown>>): Record<string, unknown> | null =>
  Object.keys(value).length === 0 ? null : { ...value };

// ───────── 流程定义 ─────────

class QueryDefinitionStore implements DefinitionStore {
  constructor(private readonly db: Database) {}

  /** 第一次发布时建上流程本身；之后最新版本号加一（只在最新版本还是读到的那个时改到：两个人同时发布，后到的失败） */
  publish(p: DefinitionPublication): Promise<StoredDefinition> {
    return this.db.transaction(async (db) => {
      const existing = await this.process(p.processKey);
      let version = 1;
      if (existing) {
        version = existing.latestVersion + 1;
        await db.execute(
          update(ProcessRow)
            .set((r) => ({ latestVersion: version, name: p.name, category: p.category, updatedBy: p.publishedBy, rev: r.rev.plus(1) }))
            .where((r) => {
              r.processKey.eq(p.processKey);
              r.latestVersion.eq(existing.latestVersion);
            })
            .expect(1),
        );
      } else {
        await db.execute(
          insertInto(ProcessRow).values({
            processKey: p.processKey,
            name: p.name,
            category: p.category,
            status: STATUS.ACTIVE,
            latestVersion: 1,
            rev: 0,
            createdAt: p.at,
            updatedBy: p.publishedBy,
          }),
        );
      }
      const [id] = await db.insertKeys<bigint>(
        insertInto(DefinitionRow).values({
          processKey: p.processKey,
          version,
          model: p.model,
          formKey: p.formKey,
          formVersion: p.formVersion,
          note: p.note,
          publishedBy: p.publishedBy,
          publishedByName: p.publishedByName,
          publishedAt: p.at,
        }),
      );
      return { id: BigInt(id!), processKey: p.processKey, version, model: p.model, formKey: p.formKey, formVersion: p.formVersion };
    });
  }

  process(processKey: string) {
    return this.db
      .optional(from(ProcessRow).where((r) => r.processKey.eq(processKey)).select(processColumns))
      .then((row) => row ?? null);
  }

  processes() {
    return this.db.list(from(ProcessRow).orderBy((r) => [r.category, r.name]).select(processColumns));
  }

  latest(processKey: string) {
    return this.db
      .first(from(DefinitionRow).where((r) => r.processKey.eq(processKey)).orderBy((r) => r.version.desc()).select(definitionColumns))
      .then((row) => row ?? null);
  }

  byId(definitionId: Id) {
    return this.db.optional(from(DefinitionRow).where((r) => r.id.eq(definitionId)).select(definitionColumns)).then((row) => row ?? null);
  }

  version(processKey: string, version: number) {
    return this.db
      .optional(
        from(DefinitionRow)
          .where((r) => {
            r.processKey.eq(processKey);
            r.version.eq(version);
          })
          .select(definitionColumns),
      )
      .then((row) => row ?? null);
  }

  versions(processKey: string) {
    return this.db.list(
      from(DefinitionRow)
        .where((r) => r.processKey.eq(processKey))
        .orderBy((r) => r.version.desc())
        .select((r) => ({
          version: r.version,
          note: r.note,
          publishedByName: r.publishedByName,
          publishedAt: r.publishedAt,
          formKey: r.formKey,
          formVersion: r.formVersion,
        })),
    );
  }

  async setStatus(processKey: string, status: string) {
    const changed = await this.db.execute(
      update(ProcessRow)
        .set((r) => ({ status, rev: r.rev.plus(1) }))
        .where((r) => r.processKey.eq(processKey)),
    );
    return changed === 1;
  }
}

const processColumns = (r: Ref<ProcessRow>) => ({
  processKey: r.processKey,
  name: r.name,
  category: r.category,
  status: r.status,
  latestVersion: r.latestVersion,
});

const definitionColumns = (r: Ref<DefinitionRow>) => ({
  id: r.id,
  processKey: r.processKey,
  version: r.version,
  model: r.model,
  formKey: r.formKey,
  formVersion: r.formVersion,
});

// ───────── 表单 ─────────

class QueryFormStore implements FormStore {
  constructor(private readonly db: Database) {}

  /** 同流程定义：第一次发布时建上表单本身，之后最新版本号加一 */
  publish(p: FormPublicationRecord): Promise<StoredForm> {
    return this.db.transaction(async (db) => {
      const existing = await this.form(p.formKey);
      let version = 1;
      if (existing) {
        version = existing.latestVersion + 1;
        await db.execute(
          update(FormRow)
            .set((r) => ({ latestVersion: version, name: p.name, category: p.category, updatedBy: p.publishedBy, rev: r.rev.plus(1) }))
            .where((r) => {
              r.formKey.eq(p.formKey);
              r.latestVersion.eq(existing.latestVersion);
            })
            .expect(1),
        );
      } else {
        await db.execute(
          insertInto(FormRow).values({
            formKey: p.formKey,
            name: p.name,
            category: p.category,
            status: STATUS.ACTIVE,
            latestVersion: 1,
            rev: 0,
            createdAt: p.at,
            updatedBy: p.publishedBy,
          }),
        );
      }
      const [id] = await db.insertKeys<bigint>(
        insertInto(FormVersionRow).values({
          formKey: p.formKey,
          version,
          clientSource: p.clientSource,
          clientCompiled: p.clientCompiled,
          compilerVersion: p.compilerVersion,
          manifest: p.manifest,
          serverLanguage: p.serverLanguage,
          serverSource: p.serverSource,
          note: p.note,
          publishedBy: p.publishedBy,
          publishedByName: p.publishedByName,
          publishedAt: p.at,
        }),
      );
      return { id: BigInt(id!), formKey: p.formKey, version, manifest: p.manifest };
    });
  }

  version(formKey: string, version: number) {
    return this.db
      .optional(
        from(FormVersionRow)
          .where((r) => {
            r.formKey.eq(formKey);
            r.version.eq(version);
          })
          .select(formColumns),
      )
      .then((row) => row ?? null);
  }

  latest(formKey: string) {
    return this.db
      .first(from(FormVersionRow).where((r) => r.formKey.eq(formKey)).orderBy((r) => r.version.desc()).select(formColumns))
      .then((row) => row ?? null);
  }

  byId(formVersionId: Id) {
    return this.db.optional(from(FormVersionRow).where((r) => r.id.eq(formVersionId)).select(formColumns)).then((row) => row ?? null);
  }

  client(formVersionId: Id) {
    return this.db
      .optional(
        from(FormVersionRow)
          .where((r) => r.id.eq(formVersionId))
          .select((r) => ({ clientSource: r.clientSource, clientCompiled: r.clientCompiled, compilerVersion: r.compilerVersion })),
      )
      .then((row) => row ?? null);
  }

  forms() {
    return this.db.list(from(FormRow).orderBy((r) => [r.category, r.name]).select(formInfoColumns));
  }

  form(formKey: string) {
    return this.db.optional(from(FormRow).where((r) => r.formKey.eq(formKey)).select(formInfoColumns)).then((row) => row ?? null);
  }

  versions(formKey: string) {
    return this.db.list(
      from(FormVersionRow)
        .where((r) => r.formKey.eq(formKey))
        .orderBy((r) => r.version.desc())
        .select((r) => ({ version: r.version, note: r.note, publishedByName: r.publishedByName, publishedAt: r.publishedAt })),
    );
  }

  source(formKey: string, version: number) {
    return this.db
      .optional(
        from(FormVersionRow)
          .where((r) => {
            r.formKey.eq(formKey);
            r.version.eq(version);
          })
          .select((r) => ({
            formKey: r.formKey,
            version: r.version,
            manifest: r.manifest,
            clientSource: r.clientSource,
            serverLanguage: r.serverLanguage,
            serverSource: r.serverSource,
          })),
      )
      .then((row) => row ?? null);
  }

  async setStatus(formKey: string, status: string) {
    const changed = await this.db.execute(
      update(FormRow)
        .set((r) => ({ status, rev: r.rev.plus(1) }))
        .where((r) => r.formKey.eq(formKey)),
    );
    return changed === 1;
  }
}

const formColumns = (r: Ref<FormVersionRow>) => ({ id: r.id, formKey: r.formKey, version: r.version, manifest: r.manifest });

const formInfoColumns = (r: Ref<FormRow>) => ({
  formKey: r.formKey,
  name: r.name,
  category: r.category,
  status: r.status,
  latestVersion: r.latestVersion,
});

class QueryFormDataStore implements FormDataStore {
  constructor(private readonly db: Database) {}

  read(instanceId: Id) {
    return this.db
      .optional(from(FormDataRow).where((r) => r.instanceId.eq(instanceId)).select((r) => ({ formVersionId: r.formVersionId, values: r.values })))
      .then((row) => row ?? null);
  }

  async insert(instanceId: Id, formVersionId: Id, values: Readonly<Record<string, unknown>>) {
    await this.db.execute(insertInto(FormDataRow).values({ instanceId, formVersionId, values: { ...values }, rev: 0 }));
  }

  async update(instanceId: Id, values: Readonly<Record<string, unknown>>) {
    await this.db.execute(
      update(FormDataRow)
        .set((r) => ({ values: { ...values }, rev: r.rev.plus(1) }))
        .where((r) => r.instanceId.eq(instanceId))
        .expect(1),
    );
  }
}

// ───────── 实例 ─────────

const instanceColumns = (r: Ref<InstanceRow>) => ({
  id: r.id,
  processKey: r.processKey,
  definitionId: r.definitionId,
  version: r.version,
  businessKey: r.businessKey,
  title: r.title,
  priority: r.priority,
  summary: r.summary,
  status: r.status,
  result: r.result,
  current: r.current,
  variables: r.variables,
  state: r.state,
  startedBy: r.startedBy,
  startedByName: r.startedByName,
  startedAt: r.startedAt,
  endedAt: r.endedAt,
  parentInstanceId: r.parentInstanceId,
  parentNode: r.parentNode,
  parentRound: r.parentRound,
  rootInstanceId: r.rootInstanceId,
  rev: r.rev,
});

/** 表的一行换成实例的记录：JSON 列是 NULL 时补成空的 */
function toInstance(row: InstanceRowValues): InstanceRecord {
  return {
    ...row,
    summary: row.summary ?? {},
    current: row.current ?? [],
    variables: row.variables ?? {},
    state: row.state ?? RunState.INITIAL,
    status: row.status as InstanceRecord['status'],
  };
}

/** 选出的实例列的值 */
interface InstanceRowValues {
  id: bigint;
  processKey: string;
  definitionId: bigint;
  version: number;
  businessKey: string | null;
  title: string | null;
  priority: number;
  summary: Record<string, unknown> | null;
  status: string;
  result: string | null;
  current: { node: string; name: string | null }[] | null;
  variables: Record<string, unknown> | null;
  state: RunState | null;
  startedBy: bigint | null;
  startedByName: string | null;
  startedAt: Instant;
  endedAt: Instant | null;
  parentInstanceId: bigint | null;
  parentNode: string | null;
  parentRound: number | null;
  rootInstanceId: bigint | null;
  rev: number;
}

class QueryInstanceStore implements InstanceStore {
  constructor(private readonly db: Database) {}

  async insert(instance: Omit<InstanceRecord, 'id'>): Promise<InstanceRecord> {
    const [id] = await this.db.insertKeys<bigint>(insertInto(InstanceRow).values(instanceRow(instance)));
    return { ...instance, id: BigInt(id!) };
  }

  /** 先把版本加一（这条 UPDATE 锁住实例行），再读回新的版本 */
  async lock(instanceId: Id) {
    const changed = await this.db.execute(
      update(InstanceRow)
        .set((r) => ({ rev: r.rev.plus(1) }))
        .where((r) => r.id.eq(instanceId)),
    );
    if (changed === 0) return null;
    return this.db.one(from(InstanceRow).where((r) => r.id.eq(instanceId)).select((r) => r.rev));
  }

  async find(instanceId: Id) {
    const row = await this.db.optional(from(InstanceRow).where((r) => r.id.eq(instanceId)).select(instanceColumns));
    return row ? toInstance(row) : null;
  }

  async findAll(instanceIds: readonly Id[]) {
    if (instanceIds.length === 0) return [];
    const rows = await this.db.list(from(InstanceRow).where((r) => r.id.in([...instanceIds])).select(instanceColumns));
    return rows.map(toInstance);
  }

  async search(search: InstanceSearch, page: number, size: number) {
    const rows = await this.db.page(
      from(InstanceRow)
        .where((r) => {
          if (search.status?.trim()) r.status.eq(search.status);
          if (search.processKey?.trim()) r.processKey.eq(search.processKey);
          if (search.keyword?.trim()) r.title.contains(search.keyword.trim());
          if (search.startedBy != null) r.startedBy.eq(search.startedBy);
        })
        .orderBy((r) => [r.startedAt.desc(), r.id.desc()])
        .select(instanceColumns),
      page,
      size,
    );
    return mapPage(rows, toInstance);
  }

  async startedBy(userId: Id, page: number, size: number) {
    const rows = await this.db.page(
      from(InstanceRow)
        .where((r) => r.startedBy.eq(userId))
        .orderBy((r) => [r.startedAt.desc(), r.id.desc()])
        .select(instanceColumns),
      page,
      size,
    );
    return mapPage(rows, toInstance);
  }

  async children(parentInstanceId: Id, parentNode: string, parentRound: number) {
    const rows = await this.db.list(
      from(InstanceRow)
        .where((r) => {
          r.parentInstanceId.eq(parentInstanceId);
          r.parentNode.eq(parentNode);
          r.parentRound.eq(parentRound);
        })
        .orderBy((r) => r.id)
        .select(instanceColumns),
    );
    return rows.map(toInstance);
  }

  async activeChildren(parentInstanceId: Id) {
    const rows = await this.db.list(
      from(InstanceRow)
        .where((r) => {
          r.parentInstanceId.eq(parentInstanceId);
          r.endedAt.isNull();
        })
        .orderBy((r) => r.id)
        .select(instanceColumns),
    );
    return rows.map(toInstance);
  }

  /** 按编号整行写回（调用前已锁住这一行；版本列照记录里的写） */
  async update(instance: InstanceRecord) {
    const { processKey: _k, definitionId: _d, version: _v, startedBy: _s, startedByName: _n, startedAt: _a, ...changed } = instanceRow(instance);
    await this.db.execute(
      update(InstanceRow)
        .set(changed)
        .where((r) => r.id.eq(instance.id))
        .expect(1),
    );
  }
}

/** 实例的记录写成表的一行 */
function instanceRow(i: Omit<InstanceRecord, 'id'>) {
  return {
    processKey: i.processKey,
    definitionId: i.definitionId,
    version: i.version,
    businessKey: i.businessKey,
    title: i.title,
    priority: i.priority,
    summary: nullIfEmpty(i.summary),
    status: i.status,
    result: i.result,
    current: i.current.map((c) => ({ node: c.node, name: c.name })),
    variables: { ...i.variables },
    state: i.state,
    startedBy: i.startedBy,
    startedByName: i.startedByName,
    startedAt: i.startedAt,
    endedAt: i.endedAt,
    parentInstanceId: i.parentInstanceId,
    parentNode: i.parentNode,
    parentRound: i.parentRound,
    rootInstanceId: i.rootInstanceId,
    rev: i.rev,
  };
}

// ───────── 待办 ─────────

/** 还没办完的待办：打开着的，和等委派、加签的人办完的 */
const PENDING = ['open', 'waiting'];

const workItemColumns = (r: Ref<WorkItemRow>) => ({
  id: r.id,
  instanceId: r.instanceId,
  processKey: r.processKey,
  priority: r.priority,
  node: r.node,
  nodeName: r.nodeName,
  round: r.round,
  signSeq: r.signSeq,
  weight: r.weight,
  state: r.state,
  assignment: r.assignment,
  arrival: r.arrival,
  inboxKind: r.inboxKind,
  inboxId: r.inboxId,
  assigneeId: r.assigneeId,
  assigneeName: r.assigneeName,
  fromUserId: r.fromUserId,
  fromUserName: r.fromUserName,
  parentItemId: r.parentItemId,
  addMode: r.addMode,
  createdAt: r.createdAt,
  claimedAt: r.claimedAt,
  dueAt: r.dueAt,
  outcome: r.outcome,
  comment: r.comment,
  completedBy: r.completedBy,
  completedByName: r.completedByName,
  completedAt: r.completedAt,
});

/** 选出的待办列就是模型包的 WorkItem（状态、落到谁这几列的取值范围更窄，按库里的值认） */
const toWorkItem = (row: object): WorkItem => row as WorkItem;

/** 新到的待办的顺序：到达时间从新到旧，同一时刻按编号从大到小 */
const newestFirst = (a: WorkItem, b: WorkItem) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : a.id < b.id ? 1 : a.id > b.id ? -1 : 0);

class QueryWorkItemStore implements WorkItemStore {
  constructor(private readonly db: Database) {}

  async insert(draft: Omit<WorkItem, 'id'>, candidates: readonly Candidate[]): Promise<WorkItem> {
    const [id] = await this.db.insertKeys<bigint>(insertInto(WorkItemRow).values({ ...draft, kind: 'task', remindCount: 0, needsDesignation: false, rev: 0 }));
    const saved: WorkItem = { ...draft, id: BigInt(id!) };
    if (candidates.length > 0) {
      await this.db.execute(insertInto(CandidateRow).values(...candidates.map((c) => ({ workItemId: saved.id, kind: c.kind, targetId: c.targetId }))));
    }
    return saved;
  }

  async find(workItemId: Id) {
    const row = await this.db.optional(from(WorkItemRow).where((r) => r.id.eq(workItemId)).select(workItemColumns));
    return row ? toWorkItem(row) : null;
  }

  candidates(workItemId: Id) {
    return this.db.list(
      from(CandidateRow)
        .where((c) => c.workItemId.eq(workItemId))
        .orderBy((c) => c.id)
        .select((c) => ({ kind: c.kind, targetId: c.targetId })),
    );
  }

  async claim(workItemId: Id, claim: { assigneeId: Id; assigneeName: string; claimedAt: Instant }) {
    const changed = await this.db.execute(
      update(WorkItemRow)
        .set((r) => ({ ...claim, assignment: 'claimed', rev: r.rev.plus(1) }))
        .where((r) => {
          r.id.eq(workItemId);
          r.state.eq('open');
          r.assigneeId.isNull();
        }),
    );
    return changed === 1;
  }

  async complete(workItemId: Id, completion: { outcome: string | null; comment: string | null; completedBy: Id | null; completedByName: string | null; completedAt: Instant }) {
    const changed =
      (await this.db.execute(
        update(WorkItemRow)
          .set((r) => ({ ...completion, state: 'done', rev: r.rev.plus(1) }))
          .where((r) => {
            r.id.eq(workItemId);
            r.state.eq('open');
          }),
      )) === 1;
    // 候选只在待办打开时存在
    if (changed) await this.db.execute(deleteFrom(CandidateRow).where((r) => r.workItemId.eq(workItemId)));
    return changed;
  }

  async cancelOpen(instanceId: Id, at: Instant) {
    const open = await this.db.list(
      from(WorkItemRow)
        .where((r) => {
          r.instanceId.eq(instanceId);
          r.state.in(PENDING);
        })
        .orderBy((r) => r.id)
        .select(workItemColumns),
    );
    return this.#cancel(open.map(toWorkItem), at);
  }

  async cancelOpenAt(instanceId: Id, nodes: readonly string[], at: Instant) {
    if (nodes.length === 0) return [];
    const open = await this.db.list(
      from(WorkItemRow)
        .where((r) => {
          r.instanceId.eq(instanceId);
          r.state.in(PENDING);
          r.node.in([...nodes]);
        })
        .orderBy((r) => r.id)
        .select(workItemColumns),
    );
    return this.#cancel(open.map(toWorkItem), at);
  }

  /** 把这些打开着的待办改成取消、删掉候选；返回它们取消之前的样子 */
  async #cancel(open: WorkItem[], at: Instant) {
    if (open.length === 0) return open;
    const ids = open.map((w) => w.id);
    await this.db.execute(
      update(WorkItemRow)
        // 取消也记下时间：列表上看得出什么时候结束的
        .set((r) => ({ state: 'canceled', completedAt: at, rev: r.rev.plus(1) }))
        .where((r) => {
          r.id.in(ids);
          r.state.in(PENDING);
        }),
    );
    await this.db.execute(deleteFrom(CandidateRow).where((r) => r.workItemId.in(ids)));
    return open;
  }

  async cancel(workItemId: Id, at: Instant) {
    const changed =
      (await this.db.execute(
        update(WorkItemRow)
          .set((r) => ({ state: 'canceled', completedAt: at, rev: r.rev.plus(1) }))
          .where((r) => {
            r.id.eq(workItemId);
            r.state.in(PENDING);
          }),
      )) === 1;
    if (changed) await this.db.execute(deleteFrom(CandidateRow).where((r) => r.workItemId.eq(workItemId)));
    return changed;
  }

  async changeState(workItemId: Id, fromState: string, to: string) {
    const changed = await this.db.execute(
      update(WorkItemRow)
        .set((r) => ({ state: to, rev: r.rev.plus(1) }))
        .where((r) => {
          r.id.eq(workItemId);
          r.state.eq(fromState);
        }),
    );
    return changed === 1;
  }

  async release(workItemId: Id, userId: Id) {
    const changed =
      (await this.db.execute(
        update(WorkItemRow)
          .set((r) => ({ assigneeId: null, assigneeName: null, claimedAt: null, assignment: 'pool', rev: r.rev.plus(1) }))
          .where((r) => {
            r.id.eq(workItemId);
            r.state.eq('open');
            r.assigneeId.eq(userId);
            r.assignment.eq('claimed');
          }),
      )) === 1;
    if (changed) await this.db.execute(insertInto(CandidateRow).values({ workItemId, kind: CANDIDATE.EXCLUDED, targetId: userId }));
    return changed;
  }

  async reassign(workItemId: Id, userId: Id, userName: string) {
    const changed =
      (await this.db.execute(
        update(WorkItemRow)
          .set((r) => ({ assigneeId: userId, assigneeName: userName, assignment: 'direct', rev: r.rev.plus(1) }))
          .where((r) => {
            r.id.eq(workItemId);
            r.state.eq('open');
          }),
      )) === 1;
    if (changed) await this.db.execute(deleteFrom(CandidateRow).where((r) => r.workItemId.eq(workItemId)));
    return changed;
  }

  async remind(workItemId: Id, at: Instant, notBefore: Instant) {
    const changed = await this.db.execute(
      update(WorkItemRow)
        .set((r) => ({ remindCount: r.remindCount.plus(1), lastRemindedAt: at }))
        .where((r) => {
          r.id.eq(workItemId);
          r.state.eq('open');
          any(() => {
            r.lastRemindedAt.isNull();
            r.lastRemindedAt.lt(notBefore);
          });
        }),
    );
    return changed === 1;
  }

  async reopen(workItemId: Id) {
    const changed = await this.db.execute(
      update(WorkItemRow)
        .set((r) => ({ state: 'open', outcome: null, comment: null, completedBy: null, completedByName: null, completedAt: null, rev: r.rev.plus(1) }))
        .where((r) => {
          r.id.eq(workItemId);
          r.state.eq('done');
        }),
    );
    return changed === 1;
  }

  async needDesignation(workItemId: Id, designatorId: Id | null) {
    const changed = await this.db.execute(
      update(WorkItemRow)
        .set((r) => ({ needsDesignation: true, designatorId, rev: r.rev.plus(1) }))
        .where((r) => {
          r.id.eq(workItemId);
          r.state.eq('open');
          r.assigneeId.isNull();
        }),
    );
    return changed === 1;
  }

  async awaitsDesignation(workItemId: Id, designatorId: Id) {
    return this.db.exists(
      from(WorkItemRow)
        .where((r) => {
          r.id.eq(workItemId);
          r.designatorId.eq(designatorId);
          r.needsDesignation.eq(true);
          r.state.eq('open');
        })
        .select((r) => r.id),
    );
  }

  async awaitingDesignation(designatorId: Id, page: number, size: number) {
    const rows = await this.db.page(
      from(WorkItemRow)
        .where((r) => {
          r.designatorId.eq(designatorId);
          r.needsDesignation.eq(true);
          r.state.eq('open');
        })
        .orderBy((r) => [r.createdAt.desc(), r.id.desc()])
        .select(workItemColumns),
      page,
      size,
    );
    return mapPage(rows, toWorkItem);
  }

  async designate(workItemId: Id, designatorId: Id, userId: Id, userName: string) {
    const changed =
      (await this.db.execute(
        update(WorkItemRow)
          .set((r) => ({ assigneeId: userId, assigneeName: userName, assignment: 'designated', needsDesignation: false, rev: r.rev.plus(1) }))
          .where((r) => {
            r.id.eq(workItemId);
            r.state.eq('open');
            r.needsDesignation.eq(true);
            r.designatorId.eq(designatorId);
          }),
      )) === 1;
    if (changed) await this.db.execute(deleteFrom(CandidateRow).where((r) => r.workItemId.eq(workItemId)));
    return changed;
  }

  async children(parentItemId: Id) {
    const rows = await this.db.list(
      from(WorkItemRow)
        .where((r) => r.parentItemId.eq(parentItemId))
        .orderBy((r) => r.id)
        .select(workItemColumns),
    );
    return rows.map(toWorkItem);
  }

  async ofInstance(instanceId: Id) {
    const rows = await this.db.list(
      from(WorkItemRow)
        .where((r) => r.instanceId.eq(instanceId))
        .orderBy((r) => [r.createdAt, r.id])
        .select(workItemColumns),
    );
    return rows.map(toWorkItem);
  }

  async lastCompletedBy(instanceId: Id, node: string) {
    const by = await this.db.first(
      from(WorkItemRow)
        .where((r) => {
          r.instanceId.eq(instanceId);
          r.node.eq(node);
          r.state.eq('done');
        })
        .orderBy((r) => [r.completedAt.desc(), r.id.desc()])
        .select((r) => r.completedBy),
    );
    return by ?? null;
  }

  async done(userId: Id, page: number, size: number) {
    const rows = await this.db.page(
      from(WorkItemRow)
        .where((r) => {
          r.completedBy.eq(userId);
          r.state.eq('done');
        })
        .orderBy((r) => [r.completedAt.desc(), r.id.desc()])
        .select(workItemColumns),
      page,
      size,
    );
    return mapPage(rows, toWorkItem);
  }

  /**
   * 收件箱分两段查，各取前"偏移 + 页长"条再合并：落到我的（处理人是我）；我能认领的（没有处理人，候选里有我、我的角色或部门，
   * 而且我没被排除）。第二段要求没有处理人，两段不会重叠，总数是两段之和。
   */
  async todo(member: Member, page: number, size: number): Promise<Page<WorkItem>> {
    if (page < 1 || size < 1) throw new RangeError('page and size start at 1');
    const head = page * size;
    const mine = () =>
      from(WorkItemRow).where((r) => {
        r.assigneeId.eq(member.userId);
        r.state.eq('open');
      });
    const claimable = () =>
      from(WorkItemRow).where((r) => {
        r.assigneeId.isNull();
        r.state.eq('open');
        r.candidates.any((c) =>
          any(() => {
            for (const [kind, ids] of member.memberships) {
              if (ids.size > 0) {
                all(() => {
                  c.kind.eq(kind);
                  c.targetId.in([...ids]);
                });
              }
            }
          }),
        );
        r.candidates.none((c) => {
          c.kind.eq(CANDIDATE.EXCLUDED);
          c.targetId.eq(member.userId);
        });
      });
    const total = (await this.db.count(mine().select((r) => r.id))) + (await this.db.count(claimable().select((r) => r.id)));
    const newest = (q: ReturnType<typeof mine>) => q.orderBy((r) => [r.createdAt.desc(), r.id.desc()]).limit(head).select(workItemColumns);
    const rows = [...(await this.db.list(newest(mine()))), ...(await this.db.list(newest(claimable())))].map(toWorkItem).sort(newestFirst);
    return { rows: rows.slice((page - 1) * size, page * size), total, page, size, totalPages: Math.ceil(total / size) };
  }
}

// ───────── 流转记录、作业、抄送、快照 ─────────

const eventColumns = (r: Ref<EventRow>) => ({
  id: r.id,
  instanceId: r.instanceId,
  workItemId: r.workItemId,
  type: r.type,
  node: r.node,
  nodeName: r.nodeName,
  edge: r.edge,
  outcome: r.outcome,
  instanceRev: r.instanceRev,
  actorId: r.actorId,
  actorName: r.actorName,
  at: r.at,
  comment: r.comment,
  data: r.data,
});

class QueryEventStore implements EventStore {
  constructor(private readonly db: Database) {}

  async append(events: readonly Omit<FlowEvent, 'id'>[]): Promise<FlowEvent[]> {
    if (events.length === 0) return [];
    const ids = await this.db.insertKeys<bigint>(insertInto(EventRow).values(...events.map((e) => ({ ...e, data: e.data ? nullIfEmpty(e.data) : null }))));
    return events.map((e, i) => ({ ...e, id: BigInt(ids[i]!), data: e.data ?? {} }));
  }

  async timeline(instanceId: Id) {
    const rows = await this.db.list(
      from(EventRow)
        .where((r) => r.instanceId.eq(instanceId))
        .orderBy((r) => [r.instanceRev, r.id])
        .select(eventColumns),
    );
    return rows.map((row): FlowEvent => ({ ...row, data: row.data ?? {} }));
  }
}

const jobColumns = (r: Ref<JobRow>) => ({
  id: r.id,
  kind: r.kind,
  instanceId: r.instanceId,
  workItemId: r.workItemId,
  dueAt: r.dueAt,
  payload: r.payload,
  state: r.state,
  attempts: r.attempts,
  maxAttempts: r.maxAttempts,
  lockedBy: r.lockedBy,
  lockedUntil: r.lockedUntil,
  lastError: r.lastError,
  createdAt: r.createdAt,
});

/** 选出的作业列：数据是 NULL 时补成空的 */
const toJob = (row: Omit<Job, 'payload'> & { payload: Record<string, unknown> | null }): Job => ({ ...row, payload: row.payload ?? {} });

/** 作业：领取走（state, due_at）的索引、跨租户；抢占是带条件的更新，改到 0 行就是别的进程先领了 */
class QueryJobStore implements JobStore {
  constructor(private readonly db: Database) {}

  async schedule(job: NewJob) {
    await this.db.execute(insertInto(JobRow).values({ ...job, payload: { ...job.payload }, state: JOB_STATE.PENDING, attempts: 0 }));
  }

  async due(now: Instant, limit: number) {
    const rows = await this.db.list(
      from(JobRow)
        .unfiltered(TENANT_FILTER)
        .where((r) => {
          r.state.eq(JOB_STATE.PENDING);
          r.dueAt.le(now);
          any(() => {
            r.lockedUntil.isNull();
            r.lockedUntil.lt(now);
          });
        })
        .orderBy((r) => [r.dueAt, r.id])
        .limit(limit)
        .select((r) => ({ ...jobColumns(r), tenantId: r.tenantId })),
    );
    return rows.map(toJob);
  }

  async lock(jobId: Id, owner: string, now: Instant, until: Instant) {
    const changed = await this.db.execute(
      update(JobRow)
        .unfiltered(TENANT_FILTER)
        .set(() => ({ lockedBy: owner, lockedUntil: until }))
        .where((r) => {
          r.id.eq(jobId);
          r.state.eq(JOB_STATE.PENDING);
          any(() => {
            r.lockedUntil.isNull();
            r.lockedUntil.lt(now);
          });
        }),
    );
    return changed === 1;
  }

  async done(jobId: Id) {
    await this.db.execute(deleteFrom(JobRow).unfiltered(TENANT_FILTER).where((r) => r.id.eq(jobId)));
  }

  async reschedule(jobId: Id, dueAt: Instant, payload: Readonly<Record<string, unknown>>) {
    await this.db.execute(
      update(JobRow)
        .unfiltered(TENANT_FILTER)
        .set(() => ({ dueAt, payload: { ...payload }, lockedBy: null, lockedUntil: null }))
        .where((r) => r.id.eq(jobId)),
    );
  }

  async retry(jobId: Id, dueAt: Instant, error: string) {
    await this.db.execute(
      update(JobRow)
        .unfiltered(TENANT_FILTER)
        .set((r) => ({ attempts: r.attempts.plus(1), dueAt, lastError: error, lockedBy: null, lockedUntil: null }))
        .where((r) => r.id.eq(jobId)),
    );
  }

  async fail(jobId: Id, error: string) {
    await this.db.execute(
      update(JobRow)
        .unfiltered(TENANT_FILTER)
        .set((r) => ({ state: JOB_STATE.FAILED, attempts: r.attempts.plus(1), lastError: error, lockedBy: null, lockedUntil: null }))
        .where((r) => r.id.eq(jobId)),
    );
  }

  async revive(jobId: Id, now: Instant) {
    const changed = await this.db.execute(
      update(JobRow)
        .set(() => ({ state: JOB_STATE.PENDING, attempts: 0, dueAt: now, lockedBy: null, lockedUntil: null }))
        .where((r) => {
          r.id.eq(jobId);
          r.state.eq(JOB_STATE.FAILED);
        }),
    );
    return changed === 1;
  }

  async find(jobId: Id) {
    const row = await this.db.optional(from(JobRow).where((r) => r.id.eq(jobId)).select(jobColumns));
    return row ? toJob(row) : null;
  }

  async failed(instanceId: Id | null, page: number, size: number) {
    const rows = await this.db.page(
      from(JobRow)
        .where((r) => {
          r.state.eq(JOB_STATE.FAILED);
          if (instanceId != null) r.instanceId.eq(instanceId);
        })
        .orderBy((r) => [r.dueAt.desc(), r.id.desc()])
        .select(jobColumns),
      page,
      size,
    );
    return mapPage(rows, toJob);
  }

  async cancelForInstance(instanceId: Id) {
    await this.db.execute(
      deleteFrom(JobRow).where((r) => {
        r.instanceId.eq(instanceId);
        r.state.eq(JOB_STATE.PENDING);
      }),
    );
  }
}

class QueryCcStore implements CcStore {
  constructor(private readonly db: Database) {}

  async insert(ccs: readonly NewCc[]) {
    if (ccs.length > 0) await this.db.execute(insertInto(CcRow).values(...ccs.map((c) => ({ ...c, readAt: null }))));
  }

  async received(recipientId: Id, page: number, size: number): Promise<Page<Cc>> {
    return this.db.page(
      from(CcRow)
        .where((r) => r.recipientId.eq(recipientId))
        .orderBy((r) => [r.readAt.desc().nullsFirst(), r.createdAt.desc(), r.id.desc()])
        .select((r) => ({
          id: r.id,
          instanceId: r.instanceId,
          node: r.node,
          nodeName: r.nodeName,
          recipientId: r.recipientId,
          recipientName: r.recipientName,
          fromUserId: r.fromUserId,
          fromUserName: r.fromUserName,
          message: r.message,
          createdAt: r.createdAt,
          readAt: r.readAt,
        })),
      page,
      size,
    );
  }

  markRead(instanceId: Id, recipientId: Id, at: Instant) {
    return this.db.execute(
      update(CcRow)
        .set(() => ({ readAt: at }))
        .where((r) => {
          r.instanceId.eq(instanceId);
          r.recipientId.eq(recipientId);
          r.readAt.isNull();
        }),
    );
  }
}

class QueryStepStore implements StepStore {
  constructor(private readonly db: Database) {}

  async save(snapshot: StepSnapshot) {
    await this.remove(snapshot.instanceId);
    await this.db.execute(insertInto(StepRow).values({ ...snapshot, variables: { ...snapshot.variables }, form: { ...snapshot.form } }));
  }

  async find(instanceId: Id) {
    const row = await this.db.optional(
      from(StepRow)
        .where((r) => r.instanceId.eq(instanceId))
        .select((r) => ({ instanceId: r.instanceId, workItemId: r.workItemId, rev: r.rev, state: r.state, variables: r.variables, form: r.form })),
    );
    return row ?? null;
  }

  async remove(instanceId: Id) {
    await this.db.execute(deleteFrom(StepRow).where((r) => r.instanceId.eq(instanceId)));
  }
}
