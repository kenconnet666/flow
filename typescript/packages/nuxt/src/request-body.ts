/**
 * 请求体（JSON 文字）读成命令，同 Java flow-spring 的 RequestBody：字段名同命令的参数（0006 第 3 节），
 * JSON 写错、该是对象的不是对象时报 `flow.validation-failed`。
 *
 * 前端按 typed JSON 发（vue-tsx-pro 的 `toWire`）：编号写成文字，小数是十进制文字。根上的 `$types` 用不上、不还原——
 * 表单里的小数本来就按文字存（模型包的值规则），编号这里按文字读成 bigint。
 */
import { FlowError, type Id } from '@zerodep-dev/flow-model';
import type {
  AddSignersCommand,
  AssignCommand,
  CcCommand,
  ClaimCommand,
  CommentCommand,
  CompleteCommand,
  FormPublication,
  HandOverCommand,
  InstanceCommand,
  JumpCommand,
  Picks,
  ReadCcCommand,
  ReleaseCommand,
  RemindCommand,
  RemoveSignersCommand,
  RetryJobCommand,
  SignalCommand,
  SkipJobCommand,
  StartCommand,
  WithdrawCommand,
  WithdrawStepCommand,
} from '@zerodep-dev/flow-core';

type Fields = Readonly<Record<string, unknown>>;

export class RequestBody {
  private constructor(private readonly fields: Fields) {}

  /** 读一个请求体；空的请求体当作空对象 */
  static parse(text: string | undefined): RequestBody {
    if (text == null || text.trim() === '') return new RequestBody({});
    let tree: unknown;
    try {
      tree = JSON.parse(text);
    } catch {
      throw invalid('body', 'malformed-json');
    }
    if (!isObject(tree)) throw invalid('body', 'expected-object');
    return new RequestBody(tree);
  }

  /** `{ key, form, variables, businessKey, title, priority, picks }` */
  start(): StartCommand {
    return {
      key: this.text('key') ?? '',
      form: this.object('form'),
      variables: this.object('variables'),
      businessKey: this.text('businessKey'),
      title: this.text('title'),
      priority: this.number('priority'),
      picks: this.picks(),
    };
  }

  /** `{ workItemId }` */
  claim(): ClaimCommand {
    return { workItemId: this.id('workItemId') };
  }

  /** `{ workItemId, outcome, comment, form, picks, returnTarget }` */
  complete(): CompleteCommand {
    return {
      workItemId: this.id('workItemId'),
      outcome: this.text('outcome'),
      comment: this.text('comment'),
      form: this.object('form'),
      picks: this.picks(),
      returnTarget: this.text('returnTarget'),
    };
  }

  /** `{ instanceId, reason }` */
  withdraw(): WithdrawCommand {
    return { instanceId: this.id('instanceId'), reason: this.text('reason') };
  }

  /** `{ workItemId, reason }` */
  withdrawStep(): WithdrawStepCommand {
    return { workItemId: this.id('workItemId'), reason: this.text('reason') };
  }

  /** 转办、委派、改派：`{ workItemId, userId, reason }` */
  handOver(): HandOverCommand {
    return { workItemId: this.id('workItemId'), userId: this.id('userId'), reason: this.text('reason') };
  }

  /** `{ workItemId, users, mode, reason }` */
  addSigners(): AddSignersCommand {
    return { workItemId: this.id('workItemId'), users: this.ids('users'), mode: this.text('mode') as AddSignersCommand['mode'], reason: this.text('reason') };
  }

  /** `{ workItemId, users }` */
  removeSigners(): RemoveSignersCommand {
    return { workItemId: this.id('workItemId'), users: this.ids('users') };
  }

  /** `{ workItemId, reason }` */
  release(): ReleaseCommand {
    return { workItemId: this.id('workItemId'), reason: this.text('reason') };
  }

  /** `{ instanceId, workItemId, message }` */
  remind(): RemindCommand {
    return { instanceId: this.id('instanceId'), workItemId: this.optionalId('workItemId'), message: this.text('message') };
  }

  /** `{ instanceId, text }` */
  comment(): CommentCommand {
    return { instanceId: this.id('instanceId'), text: this.text('text') ?? '' };
  }

  /** `{ workItemId, userId }` */
  assign(): AssignCommand {
    return { workItemId: this.id('workItemId'), userId: this.id('userId') };
  }

  /** `{ instanceId, users, message }` */
  cc(): CcCommand {
    return { instanceId: this.id('instanceId'), users: this.ids('users'), message: this.text('message') };
  }

  /** `{ instanceId }` */
  readCc(): ReadCcCommand {
    return { instanceId: this.id('instanceId') };
  }

  /** `{ instanceId 或 businessKey, event, outcome, data }` */
  signal(): SignalCommand {
    return {
      instanceId: this.optionalId('instanceId'),
      businessKey: this.text('businessKey'),
      event: this.text('event') ?? '',
      outcome: this.text('outcome'),
      data: this.object('data'),
    };
  }

  /** 终止、暂停、恢复：`{ instanceId, reason }` */
  instance(): InstanceCommand {
    return { instanceId: this.id('instanceId'), reason: this.text('reason') };
  }

  /** `{ instanceId, node, reason }` */
  jump(): JumpCommand {
    return { instanceId: this.id('instanceId'), node: this.text('node') ?? '', reason: this.text('reason') };
  }

  /** `{ jobId }` */
  retryJob(): RetryJobCommand {
    return { jobId: this.id('jobId') };
  }

  /** `{ jobId, outcome, reason }` */
  skipJob(): SkipJobCommand {
    return { jobId: this.id('jobId'), outcome: this.text('outcome'), reason: this.text('reason') };
  }

  /** `{ key }`：停用、启用流程或表单 */
  key(): string {
    const key = this.text('key');
    if (key == null || key.trim() === '') throw invalid('key', 'required');
    return key;
  }

  /** `{ key, name, category, manifest, clientSource, clientCompiled, compilerVersion, serverLanguage, serverSource, note }`；manifest 是对象 */
  formPublication(): FormPublication {
    return {
      key: this.text('key') ?? '',
      name: this.text('name') ?? '',
      category: this.text('category'),
      manifest: JSON.stringify(this.object('manifest')),
      clientSource: this.text('clientSource'),
      clientCompiled: this.text('clientCompiled'),
      compilerVersion: this.text('compilerVersion'),
      serverLanguage: this.text('serverLanguage'),
      serverSource: this.text('serverSource'),
      note: this.text('note'),
    };
  }

  private text(name: string): string | null {
    const value = this.fields[name];
    if (value == null || typeof value === 'string') return value ?? null;
    throw invalid(name, 'expected-text');
  }

  /** 必填的编号：没给时交给引擎按"找不到"报 */
  private id(name: string): Id {
    return this.optionalId(name) ?? 0n;
  }

  private optionalId(name: string): Id | null {
    const value = this.fields[name];
    return value == null ? null : idOf(name, value);
  }

  private ids(name: string): Id[] {
    const value = this.fields[name];
    if (value == null) return [];
    if (!Array.isArray(value)) throw invalid(name, 'expected-list');
    return value.map((item) => idOf(name, item));
  }

  /** 下一步选的处理人：`{ 节点: [用户编号…] }` */
  private picks(): Picks {
    const out: Record<string, Id[]> = {};
    for (const [node, users] of Object.entries(this.object('picks'))) {
      if (!Array.isArray(users)) throw invalid(`picks.${node}`, 'expected-list');
      out[node] = users.map((user) => idOf(`picks.${node}`, user));
    }
    return out;
  }

  private number(name: string): number {
    const value = this.fields[name];
    if (value == null) return 0;
    if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value);
    throw invalid(name, 'expected-number');
  }

  private object(name: string): Fields {
    const value = this.fields[name];
    if (value == null) return {};
    if (isObject(value)) return value;
    throw invalid(name, 'expected-object');
  }
}

/** 编号：前端按 typed JSON 发成文字（大整数不丢精度），写成整数也认 */
export function idOf(name: string, value: unknown): Id {
  if (typeof value === 'number' && Number.isSafeInteger(value)) return BigInt(value);
  if (typeof value === 'string' && /^\s*-?\d+\s*$/.test(value)) return BigInt(value.trim());
  throw invalid(name, 'expected-id');
}

/** 查询参数里的整数（页码、版本号） */
export function intOf(name: string, value: unknown, fallback: number): number {
  if (value == null || value === '') return fallback;
  const n = Number(value);
  if (Number.isInteger(n)) return n;
  throw invalid(name, 'expected-number');
}

export function invalid(field: string, code: string): FlowError {
  return new FlowError('flow.validation-failed', { message: `${field}: ${code}`, errors: [{ code, field }] });
}

function isObject(value: unknown): value is Fields {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
