/**
 * 我已办、我发起的、抄送我的共用的筛选：流程、起止日期（按浏览器的时区算一天，交给后端是时刻）。
 *
 *   const filters = useListFilters();
 *   const query = useTableQuery((q, signal) => flow.started({ page: q.page, pageSize: q.pageSize, ...listFilterParams(q.filters) }, signal),
 *     { pageSize, filters: { ...BLANK_LIST_FILTERS } });
 *   <QueryTable … blankFilters={BLANK_LIST_FILTERS} filters={filters} />
 */
import { getLocalTimeZone, type CalendarDate } from '@internationalized/date';
import { DatePicker, Field, Select, useRequest } from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';

/** 查询栏的条件 */
export interface ListFilters {
  processKey: string;
  from: CalendarDate | null;
  to: CalendarDate | null;
}

export const BLANK_LIST_FILTERS: ListFilters = { processKey: '', from: null, to: null };

/** 条件 → 接口的参数：起日的零点起、止日的第二天零点前（按浏览器的时区） */
export function listFilterParams(filters: ListFilters): { processKey?: string; from?: string; to?: string } {
  const zone = getLocalTimeZone();
  return {
    ...(filters.processKey ? { processKey: filters.processKey } : {}),
    ...(filters.from ? { from: filters.from.toDate(zone).toISOString() } : {}),
    ...(filters.to ? { to: filters.to.add({ days: 1 }).toDate(zone).toISOString() } : {}),
  };
}

/** 查询栏的几个输入：流程、从哪天、到哪天 */
export function useListFilters(): (draft: ListFilters) => VNodeChild[] {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const processes = useRequest((_: undefined, signal) => flow.processes(signal));
  return (draft) => [
    <Field key="process" label={messages.columnProcess}>
      <Select
        items={(processes.data ?? []).map((p) => ({ value: p.key, label: p.name }))}
        _value={draft.processKey}
        placeholder={messages.allProcesses}
        clearable
      />
    </Field>,
    <Field key="from" label={messages.dateFrom}>
      <DatePicker valueType="dateValue" _value={draft.from} />
    </Field>,
    <Field key="to" label={messages.dateTo}>
      <DatePicker valueType="dateValue" _value={draft.to} />
    </Field>,
  ];
}
