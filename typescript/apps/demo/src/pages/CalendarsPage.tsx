/** 工作日历（管理端）：节假日与调休，时限写成工作日时按它算 */
import { FlowCalendars } from '@zerodep-dev/flow-ui/admin';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';

export const CalendarsPage = _component(() => () => <FlowCalendars />);
