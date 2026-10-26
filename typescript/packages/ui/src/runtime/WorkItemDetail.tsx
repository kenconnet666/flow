/**
 * 待办详情（审批页面）：实例的概要、这个节点上的表单、意见与处理结果、流转记录。
 *
 *   <WorkItemDetail id={workItemId} people={peopleSource} onDone={() => router.push('/inbox')} />
 *
 * 能做什么由引擎说（待办详情的 claimable、completable）：在收件箱里等认领时多一个"认领"（直接处理也会顺带认领）；
 * 不是我的、已处理的只能看。处理时先校验表单（模型包的规则，同后端），引擎退回的按字段的问题标回表单与意见框，
 * 别的错误（别人已认领、已处理过）显示在顶上并重新加载。
 */
import type { Id } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Badge,
  Button,
  Card,
  Descriptions,
  Flex,
  HttpError,
  Skeleton,
  useErrorMessage,
  useFormat,
  useRequest,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { CommandResult, OutcomeView } from '../client/types';
import type { FlowFormHandle } from '../form/context';
import { FlowForm } from '../form/FlowForm';
import { useFlowMessages } from '../lang/messages';
import { ApprovalActions } from './ApprovalActions';
import { FlowTimeline } from './FlowTimeline';

/** WorkItemDetail 的属性 */
export interface WorkItemDetailProps {
  /** 待办的编号 */
  id: Id;
  /** 人员字段的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 处理完了（页面多半回到收件箱） */
  onDone?: (result: CommandResult) => void;
}

/** 待办详情 */
export const WorkItemDetail = _component(({ id, people, modules, onDone }: WorkItemDetailProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const format = useFormat();
  const view = useRequest((workItemId: Id, signal) => flow.workItem(workItemId, signal), { params: () => id });
  const detail = useRequest((instanceId: Id | null, signal) => (instanceId == null ? Promise.resolve(null) : flow.instance(instanceId, signal)), {
    params: () => view.data?.workItem.instanceId ?? null,
  });

  let form: FlowFormHandle | null = null;
  let comment = _state('');
  let commentError = _state<string | undefined>(undefined);
  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);

  /** 引擎退回的错误：按字段的标回表单与意见框，其余显示在顶上并重新加载（状态可能已经变了） */
  function report(error: unknown) {
    const details = error instanceof HttpError ? (error.errors ?? []) : [];
    if (error instanceof HttpError && error.code === 'flow.validation-failed' && details.length) {
      commentError = details.some((d) => d.field === 'comment') ? messages.commentRequired : undefined;
      form?.showErrors(details.filter((d) => d.field !== 'comment'));
      return;
    }
    failure = errorText(error);
    void view.refresh();
  }

  async function run(key: string, action: () => Promise<CommandResult>) {
    busy = key;
    failure = null;
    try {
      const result = await action();
      await view.refresh();
      onDone?.(result);
    } catch (error) {
      report(error);
    } finally {
      busy = null;
    }
  }

  const claim = () => run('claim', () => flow.claim(id));

  async function complete(outcome: OutcomeView) {
    commentError = undefined;
    if (form && !(await form.validate())) return;
    await run(outcome.key, () =>
      flow.complete({
        workItemId: id,
        outcome: outcome.key,
        ...(comment.trim() ? { comment: comment.trim() } : {}),
        ...(form ? { form: form.values() } : {}),
      }),
    );
  }

  return () => {
    const data = view.data;
    if (view.error && !data) return <Alert tone="danger">{errorText(view.error)}</Alert>;
    if (!data) return <Skeleton />;
    const { workItem, instance } = data;
    const open = workItem.state === 'open';
    return (
      <Flex direction="column" gap="lg">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {!open && <Alert tone="info">{messages.notOpen}</Alert>}
        {open && !data.completable && <Alert tone="info">{messages.viewOnly}</Alert>}
        <Card
          header={instance.title ?? instance.processKey}
          extra={<Badge tone={instance.status === 'active' ? 'accent' : 'neutral'}>{messages.status[instance.status] ?? instance.status}</Badge>}
        >
          <Descriptions
            columns="auto"
            items={[
              { key: 'node', label: messages.columnNode, value: workItem.nodeName ?? workItem.node },
              { key: 'startedBy', label: messages.startedBy, value: instance.startedByName ?? '' },
              { key: 'startedAt', label: messages.startedAt, value: format.date(instance.startedAt, { dateStyle: 'medium', timeStyle: 'short' }) },
            ]}
          />
        </Card>
        {data.form ? (
          <Card>
            <FlowForm
              view={data.form}
              mode={data.completable ? 'fill' : 'view'}
              {...(people ? { people } : {})}
              {...(modules ? { modules } : {})}
              onReady={(handle) => (form = handle)}
            />
          </Card>
        ) : null}
        {data.claimable && workItem.assignment === 'pool' && (
          <Flex>
            <Button variant="outline" loading={busy === 'claim'} disabled={busy != null && busy !== 'claim'} onClick={claim}>
              {messages.claim}
            </Button>
          </Flex>
        )}
        {data.completable && (
          <ApprovalActions
            outcomes={data.outcomes}
            _comment={comment}
            commentError={commentError}
            busy={busy}
            onChoose={complete}
          />
        )}
        {detail.data && (
          <Card header={messages.timeline}>
            <FlowTimeline events={detail.data.timeline} />
          </Card>
        )}
      </Flex>
    );
  };
});
