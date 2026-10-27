/**
 * 实例的状态徽标：列表、详情、进度上共用。结束了、结果又认得（文案表的 results：approved、rejected……）时写结果，
 * 发起人一眼看出通过没通过，不是笼统的"已完成""已终止"。
 *
 *   <InstanceStatus status={instance.status} result={instance.result} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, type StatusTone } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowMessages } from '../../lang/messages';

/** InstanceStatus 的属性 */
export interface InstanceStatusProps {
  status: string;
  /** 结束的结果；没结束时为 null */
  result?: string | null;
}

const ENDED = new Set(['completed', 'terminated']);

/** 进行中是强调色，暂停是警示色，完成、通过是成功色，没通过是危险色，终止、撤回是中性 */
function toneOf(status: string, result: string | null): StatusTone {
  if (result === 'rejected') return 'danger';
  if (result === 'approved') return 'success';
  switch (status) {
    case 'active':
      return 'accent';
    case 'suspended':
      return 'warning';
    case 'completed':
      return 'success';
    default:
      return 'neutral';
  }
}

export const InstanceStatus = _component(({ status, result }: InstanceStatusProps) => {
  const messages = useFlowMessages();
  return () => {
    const known = ENDED.has(status) && result ? messages.results[result] : undefined;
    return <Badge tone={toneOf(status, known ? result! : null)}>{known ?? messages.status[status] ?? status}</Badge>;
  };
});
