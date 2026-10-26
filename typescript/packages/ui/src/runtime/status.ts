/**
 * 实例状态的色调：列表、详情上的状态徽标共用。
 */
import type { StatusTone } from '@zerodep-dev/vue-tsx-pro-ui';

/** 进行中是强调色，暂停是警示色，完成是成功色，终止、撤回是中性 */
export function statusTone(status: string): StatusTone {
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
