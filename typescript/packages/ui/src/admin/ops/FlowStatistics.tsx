/**
 * 统计（管理端，0002 §9）：各流程的实例数；选一个流程看各人工节点处理了多少、平均停留多久、超时的比例；处理人的工作量。
 * 节点与处理人按最近一段时间（默认 30 天）算。
 *
 *   <FlowStatistics />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Card, DataTable, Field, Flex, Select, useRequest, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../../client/flow-client';
import type { HandlerStats, NodeStats, ProcessStats } from '../../client/types';
import { useFlowMessages } from '../../lang/messages';

/** 最近几天 */
const RANGES = [7, 30, 90, 365] as const;

/** 统计 */
export const FlowStatistics = _component(() => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  let processKey = _state<string | null>(null);
  let days = _state<string>('30');
  const since = () => new Date(Date.now() - Number(days) * 86_400_000).toISOString();

  const processes = useRequest((_: undefined, signal) => flow.processes(signal));
  const processStats = useRequest((_: undefined, signal) => flow.processStats(signal));
  const nodeStats = useRequest(
    ([key, from]: readonly [string | null, string], signal) => (key ? flow.nodeStats(key, from, signal) : Promise.resolve([])),
    { params: () => [processKey, since()] as const },
  );
  const handlerStats = useRequest((from: string, signal) => flow.handlerStats(from, signal), { params: () => since() });

  const processName = (key: string) => processes.data?.find((p) => p.key === key)?.name ?? key;

  const processColumns = (): DataTableColumn<ProcessStats>[] => [
    { key: 'process', header: messages.columnProcess, value: (s) => processName(s.processKey) },
    { key: 'running', header: messages.statsRunning, width: 100, type: 'number', value: (s) => s.running },
    { key: 'completed', header: messages.statsCompleted, width: 100, type: 'number', value: (s) => s.completed },
    { key: 'stopped', header: messages.statsStopped, width: 120, type: 'number', value: (s) => s.stopped },
  ];
  const nodeColumns = (): DataTableColumn<NodeStats>[] => [
    { key: 'node', header: messages.columnNode, value: (s) => s.nodeName ?? s.node },
    { key: 'done', header: messages.statsDone, width: 100, type: 'number', value: (s) => s.done },
    { key: 'hours', header: messages.statsAverageHours, width: 140, type: 'number', value: (s) => Math.round(s.averageHours * 10) / 10 },
    { key: 'overdue', header: messages.statsOverdueRate, width: 120, value: (s) => `${Math.round(s.overdueRate * 100)}%` },
  ];
  const handlerColumns = (): DataTableColumn<HandlerStats>[] => [
    { key: 'user', header: messages.columnAssignee, value: (s) => s.userName ?? String(s.userId) },
    { key: 'done', header: messages.statsDone, width: 100, type: 'number', value: (s) => s.done },
    { key: 'open', header: messages.statsOpen, width: 100, type: 'number', value: (s) => s.open },
  ];

  return () => (
    <Flex direction="column" gap="lg">
      <Card header={messages.statsProcesses}>
        <DataTable rows={processStats.data ?? []} columns={processColumns()} getKey={(s) => s.processKey} />
      </Card>
      <Field label={messages.statsRange}>
        <Select items={RANGES.map((d) => ({ value: String(d), label: messages.lastDays(d) }))} _value={days} />
      </Field>
      <Card header={messages.statsNodes}>
        <Flex direction="column" gap="md">
          <Field label={messages.columnProcess}>
            <Select items={(processes.data ?? []).map((p) => ({ value: p.key, label: p.name }))} _value={processKey} placeholder={messages.statsPickProcess} />
          </Field>
          <DataTable rows={nodeStats.data ?? []} columns={nodeColumns()} getKey={(s) => s.node} />
        </Flex>
      </Card>
      <Card header={messages.statsHandlers}>
        <DataTable rows={handlerStats.data ?? []} columns={handlerColumns()} getKey={(s) => String(s.userId)} />
      </Card>
    </Flex>
  );
});
