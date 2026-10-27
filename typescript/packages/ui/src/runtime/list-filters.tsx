/**
 * 我已办、我发起的、抄送我的共用的筛选：流程、日期区间（组件库的 DateRangePicker；按浏览器的时区算一天，
 * 交给后端是半开的一对时刻，见 dayRangeBounds）。
 *
 *   const filters = useListFilters();
 *   const query = useTableQuery((q, signal) => flow.started({ page: q.page, pageSize: q.pageSize, ...listFilterParams(q.filters) }, signal),
 *     { pageSize, filters: { ...BLANK_LIST_FILTERS } });
 *   <QueryTable … blankFilters={BLANK_LIST_FILTERS} filters={filters} />
 */
import type { CalendarDate } from '@internationalized/date';
import { dayRangeBounds, DateRangePicker, Field, Select, useRequest, type DateRange } from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';

/** 查询栏的条件 */
export interface ListFilters {
  processKey: string;
  /** 两端都含的日期区间；不限时为 null */
  range: DateRange<CalendarDate> | null;
}

export const BLANK_LIST_FILTERS: ListFilters = { processKey: '', range: null };

/** 条件 → 接口的参数：开始那天零点起、结束那天的第二天零点前 */
export function listFilterParams(filters: ListFilters): { processKey?: string; from?: string; to?: string } {
  const bounds = filters.range ? dayRangeBounds(filters.range) : null;
  return {
    ...(filters.processKey ? { processKey: filters.processKey } : {}),
    ...(bounds ? { from: bounds.from.toISOString(), to: bounds.to.toISOString() } : {}),
  };
}

/** 查询栏的两个输入：流程、日期区间（带"最近 7 天"这类快捷区间） */
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
    <Field key="range" label={messages.dateRange}>
      <DateRangePicker valueType="dateValue" _value={draft.range} presets={['today', 'last7Days', 'last30Days', 'thisMonth', 'lastMonth']} />
    </Field>,
  ];
}
