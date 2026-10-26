/**
 * 响应体，同 Java flow-spring 的 ResponseBody：0008 的形状 `{ code, message, params, errors, data }` 写成 JSON 文字，没有的部分省掉。
 * data 是 typed JSON（`toWire`）：编号写成文字，根上带 `$types`，前端的请求层（vue-tsx-pro 的 `createHttpClient`）拆包时据此还原。
 * 时间在 Node 版里本来就是 ISO 文字（同 Java `Instant` 的写法），前端默认也按文字用，所以不另记 timestamptz。
 */
import type { ApiResult } from '@zerodep-dev/flow-model';
import type { CommandResult } from '@zerodep-dev/flow-core';
import { toWire } from '@zerodep-dev/wire';

/** 命令的结果在线上的形状：`{ instance, workItems, closed, events }` */
export const commandShape = (result: CommandResult) => ({
  instance: result.instance,
  workItems: result.created,
  closed: result.closed,
  events: result.events,
});

/** 整个响应体写成 JSON 文字；data 先过 toWire */
export function writeResult(result: ApiResult): string {
  const body: Record<string, unknown> = { code: result.code };
  if (result.message != null) body['message'] = result.message;
  if (result.params && Object.keys(result.params).length > 0) body['params'] = result.params;
  if (result.errors && result.errors.length > 0) body['errors'] = result.errors;
  if (result.data !== undefined && result.data !== null) body['data'] = toWire(result.data);
  return JSON.stringify(body);
}
