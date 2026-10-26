/**
 * 业务错误码与响应的形状（docs/decisions/0008）：HTTP 一律 200，成功与失败都看响应体里的 `code`。
 * 码的清单在 spec/error-codes.json，Java 的 `FlowErrorCode` 与这里一致；query 的码（`query.`）、前端请求的码（`http.`）由那两个包给。
 *
 *   throw new FlowError('flow.work-item-not-open', { params: { workItem: id } });
 *   apiOk(data);                    // → { code: 'ok', data }
 *   apiFail(error);                 // → { code: 'flow.work-item-not-open', message, params }
 */

/** flow 的业务错误码 */
export const FLOW_ERROR_CODES = [
  'flow.not-found',
  'flow.forbidden',
  'flow.not-candidate',
  'flow.already-claimed',
  'flow.work-item-not-open',
  'flow.instance-not-active',
  'flow.concurrent-modification',
  'flow.guard-rejected',
  'flow.validation-failed',
  'flow.definition-invalid',
  'flow.extension-failed',
  'flow.internal-error',
] as const;

/** flow 的业务错误码 */
export type FlowErrorCode = (typeof FLOW_ERROR_CODES)[number];

/** 成功的码 */
export const OK = 'ok';

/**
 * 错误明细的一项：表单校验是哪个字段、什么问题，发布校验是哪个节点或连线、什么问题。与 Java 的 `ErrorDetail` 同形，没有的部分省掉
 */
export interface ErrorDetail {
  /** 问题的代号：表单的值问题（`required`、`max`……）或 0005 第 7 节的问题代号 */
  readonly code: string;
  readonly field?: string;
  readonly node?: string;
  readonly edge?: string;
  readonly lane?: string;
  /** 文案里要填的参数 */
  readonly params?: Readonly<Record<string, string>>;
}

/** 响应体（0008 的第 1 节）；失败时 `code` 是任何包的业务错误码 */
export interface ApiResult<T = unknown> {
  readonly code: string;
  readonly message?: string;
  readonly params?: Readonly<Record<string, string>>;
  /** 按字段、按问题的明细：表单校验、发布校验 */
  readonly errors?: readonly ErrorDetail[];
  readonly data?: T;
}

/** 带业务错误码的错误；扩展抛的原始错误放在 cause */
export class FlowError extends Error {
  readonly code: FlowErrorCode;
  readonly params: Readonly<Record<string, string>>;
  readonly errors: readonly ErrorDetail[];

  constructor(
    code: FlowErrorCode,
    options: { message?: string; params?: Record<string, string>; errors?: readonly ErrorDetail[]; cause?: unknown } = {},
  ) {
    super(options.message ?? code, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = 'FlowError';
    this.code = code;
    this.params = options.params ?? {};
    this.errors = options.errors ?? [];
  }
}

/** 成功的响应 */
export const apiOk = <T>(data: T): ApiResult<T> => ({ code: OK, data });

/**
 * 失败的响应：带码的错误（有 `code` 字段的，FlowError 或 query 的异常）原样用它的码，其余是 `flow.internal-error`
 */
export function apiFail(error: unknown): ApiResult<never> {
  if (error instanceof FlowError) {
    return { code: error.code, message: error.message, params: error.params, ...(error.errors.length ? { errors: error.errors } : {}) };
  }
  const message = error instanceof Error ? error.message : String(error);
  const code = businessCodeOf(error);
  return code ? { code, message } : { code: 'flow.internal-error', message };
}

/** 错误上带的业务错误码（`包.码` 的写法）；没有时为 undefined。Node 的 `ENOENT` 这类系统码不算 */
function businessCodeOf(error: unknown): string | undefined {
  const code = typeof error === 'object' && error !== null ? (error as { code?: unknown }).code : undefined;
  return typeof code === 'string' && code.includes('.') ? code : undefined;
}
