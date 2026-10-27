/** 统计（管理端）：各流程的实例数、各环节的停留与超时、处理人的工作量 */
import { FlowStatistics } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';

export const StatsPage = _component(() => () => <FlowStatistics />);
