/**
 * 打开着的待办由谁处理：落到人的写名字，代理人写成"李四（代 张三）"，在收件箱里的写"待认领"。收件箱、详情、管理员操作共用。
 */
import type { WorkItem } from '@zerodep-dev/flow-model';
import type { FlowMessages } from '../../lang/messages';

export function holderName(item: WorkItem, messages: FlowMessages): string {
  if (item.assigneeName == null) return messages.pool;
  return item.onBehalfOfName ? messages.onBehalfOf(item.assigneeName, item.onBehalfOfName) : item.assigneeName;
}
