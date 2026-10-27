// 处理人一栏与到达记录里的休假代理（后端 OrgDirectory.standIn）：代理人写成"李四（代 张三）"，流转记录写谁代谁
import type { WorkItem } from '@zerodep-dev/flow-model';
import { describe, expect, it } from 'vitest';
import { enUS } from '../src/lang/en-US';
import { zhCN } from '../src/lang/zh-CN';
import { holderName } from '../src/runtime/holder';

const item = (assigneeName: string | null, onBehalfOfName: string | null = null) => ({ assigneeName, onBehalfOfName }) as WorkItem;

describe('holderName', () => {
  it('落到人的写名字，代理人带上代谁，在收件箱里的写待认领', () => {
    expect(holderName(item('张三'), zhCN)).toBe('张三');
    expect(holderName(item('李四', '张三'), zhCN)).toBe('李四（代 张三）');
    expect(holderName(item('Li', 'Zhang'), enUS)).toBe('Li (for Zhang)');
    expect(holderName(item(null), zhCN)).toBe('待认领');
  });
});

describe('到达记录', () => {
  it('换了代理人时写出谁代谁', () => {
    const data = { assigneeName: '李四', standIns: [{ userId: 1, userName: '张三', standInId: 2, standInName: '李四' }] };
    expect(zhCN.eventDetail('workItem.created', data)).toBe('处理人 李四；李四 代 张三');
    expect(zhCN.eventDetail('workItem.created', { assigneeName: '张三' })).toBe('处理人 张三');
    expect(zhCN.eventDetail('workItem.created', {})).toBeNull();
  });
});
