/**
 * 待办详情（审批页面）：实例的概要、这个节点上的表单、意见与处理结果、协作（转办、委派、加签、退回收件箱）、流转记录。
 *
 *   <WorkItemDetail id={workItemId} people={peopleSource} onDone={() => router.push('/inbox')} />
 *
 * 能做什么由引擎说（待办详情的 claimable、completable）：在收件箱里等认领时多一个"认领"（直接处理也会顺带认领）；
 * 协作按钮由待办详情的 actions 定；不是我的、已处理的只能看。处理时先校验表单（模型包的规则，同后端），引擎退回的按字段的问题标回表单与意见框，
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
  Popconfirm,
  Skeleton,
  Small,
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
import { CollaborationActions, type Collaboration } from './CollaborationActions';
import { FlowDiagram } from './FlowDiagram';
import { FlowTimeline } from './FlowTimeline';
import { statusTone } from './status';
import { watch } from 'vue';

/** WorkItemDetail 的属性 */
export interface WorkItemDetailProps {
  /** 待办的编号 */
  id: Id;
  /** 人员字段、转办委派加签挑人的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 处理、协作完了（待办结束了——result.closed 里有它——页面多半回到收件箱） */
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
  let saved = _state<string | null>(null);

  // 落到人身上、还没读过的待办：打开了就标已读（是不是我的由后端判，别人打开不算）
  watch(
    () => view.data?.workItem,
    (item) => {
      if (item && item.state === 'open' && item.assigneeId != null && item.readAt == null) void flow.readWorkItem(item.id).catch(() => {});
    },
  );

  // 打开时接着暂存过的意见写
  watch(
    () => view.data?.draft,
    (draft) => {
      if (draft?.comment && !comment) comment = draft.comment;
    },
  );

  /** 引擎退回的错误：按字段的标回表单与意见框，其余显示在顶上并重新加载（状态可能已经变了） */
  function report(error: unknown) {
    const details = error instanceof HttpError ? (error.errors ?? []) : [];
    if (error instanceof HttpError && error.code === 'flow.validation-failed' && details.length) {
      commentError = details.some((d) => d.field === 'comment') ? messages.commentRequired : undefined;
      form?.showErrors(details.filter((d) => d.field !== 'comment'));
      return;
    }
    // 守卫不通过：显示守卫给的原因（"超出预算"），比通用的"条件不满足"有用
    const reasons = details.map((d) => d.params?.['reason']).filter((r): r is string => typeof r === 'string');
    failure = error instanceof HttpError && error.code === 'flow.guard-rejected' && reasons.length ? reasons.join('；') : errorText(error);
    void view.refresh();
  }

  /** 发一个命令：成功时刷新并告诉父级，返回 true */
  async function run(key: string, action: () => Promise<CommandResult>): Promise<boolean> {
    busy = key;
    failure = null;
    try {
      const result = await action();
      await Promise.all([view.refresh(), detail.refresh()]);
      onDone?.(result);
      return true;
    } catch (error) {
      report(error);
      return false;
    } finally {
      busy = null;
    }
  }

  /** 协作 → 引擎的命令 */
  function collaborate(c: Collaboration): Promise<boolean> {
    const reason = c.reason ? { reason: c.reason } : {};
    switch (c.kind) {
      case 'transfer':
        return run(c.kind, () => flow.transfer({ workItemId: id, userId: c.userId, ...reason }));
      case 'delegate':
        return run(c.kind, () => flow.delegate({ workItemId: id, userId: c.userId, ...reason }));
      case 'addSigners':
        return run(c.kind, () => flow.addSigners({ workItemId: id, users: c.users, mode: c.mode, ...reason }));
      case 'release':
        return run(c.kind, () => flow.release({ workItemId: id, ...reason }));
      case 'assign':
        return run(c.kind, () => flow.assign(id, c.userId));
      case 'requestReassign':
        return run(c.kind, () => flow.requestReassign(id, c.reason));
    }
  }

  const claim = () => run('claim', () => flow.claim(id));

  /** 暂存：不校验必填，存下表单与意见；留在这一页，不算办完 */
  async function saveDraft() {
    busy = 'saveDraft';
    failure = null;
    try {
      await flow.saveDraft({ workItemId: id, ...(form ? { form: form.values() } : {}), ...(comment.trim() ? { comment: comment.trim() } : {}) });
      saved = messages.draftSaved(format.date(new Date(), { timeStyle: 'short' }));
    } catch (error) {
      report(error);
    } finally {
      busy = null;
    }
  }

  /** 处理：先校验表单；返回成功没有（选人框按它决定关不关） */
  async function complete(outcome: OutcomeView, picks: Record<string, Id[]>, target?: string): Promise<boolean> {
    commentError = undefined;
    if (form && !(await form.validate())) return false;
    return run(outcome.key, () =>
      flow.complete({
        workItemId: id,
        outcome: outcome.key,
        ...(comment.trim() ? { comment: comment.trim() } : {}),
        ...(form ? { form: form.values() } : {}),
        ...(Object.keys(picks).length ? { picks } : {}),
        ...(target ? { returnTarget: target } : {}),
      }),
    );
  }

  return () => {
    const data = view.data;
    if (view.error && !data) return <Alert tone="danger">{errorText(view.error)}</Alert>;
    if (!data) return <Skeleton />;
    const { workItem, instance } = data;
    const open = workItem.state === 'open';
    const savedText =
      saved ?? (data.draft ? messages.draftSaved(format.date(data.draft.savedAt.toDate(), { dateStyle: 'medium', timeStyle: 'short' })) : null);
    return (
      <Flex direction="column" gap="lg">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {workItem.state === 'waiting' && <Alert tone="info">{messages.waiting}</Alert>}
        {!open && workItem.state !== 'waiting' && <Alert tone="info">{messages.notOpen}</Alert>}
        {open && data.actions.assign && <Alert tone="warning">{messages.awaitingDesignation}</Alert>}
        {open && !data.completable && !data.actions.assign && <Alert tone="info">{messages.viewOnly}</Alert>}
        <Card
          header={instance.title ?? instance.processKey}
          extra={<Badge tone={statusTone(instance.status)}>{messages.status[instance.status] ?? instance.status}</Badge>}
        >
          <Descriptions
            columns="auto"
            items={[
              { key: 'node', label: messages.columnNode, value: workItem.nodeName ?? workItem.node },
              { key: 'startedBy', label: messages.startedBy, value: instance.startedByName ?? '' },
              { key: 'startedAt', label: messages.startedAt, value: format.date(instance.startedAt, { dateStyle: 'medium', timeStyle: 'short' }) },
              ...(workItem.dueAt
                ? [{ key: 'dueAt', label: messages.columnDue, value: format.date(workItem.dueAt, { dateStyle: 'medium', timeStyle: 'short' }) }]
                : []),
            ]}
          />
        </Card>
        {data.form ? (
          <Card>
            <FlowForm
              view={data.form}
              instanceId={instance.id}
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
          <Flex gap="sm" align="center">
            <Button variant="outline" loading={busy === 'saveDraft'} disabled={busy != null && busy !== 'saveDraft'} onClick={saveDraft}>
              {messages.saveDraft}
            </Button>
            {savedText && <Small tone="muted">{savedText}</Small>}
          </Flex>
        )}
        {data.completable && (
          <ApprovalActions
            outcomes={data.outcomes}
            _comment={comment}
            commentError={commentError}
            busy={busy}
            {...(people ? { people } : {})}
            onChoose={complete}
          />
        )}
        <CollaborationActions actions={data.actions} {...(people ? { people } : {})} busy={busy} onRun={collaborate} />
        {data.actions.withdrawStep && (
          <Flex>
            <Popconfirm
              title={messages.withdrawStepConfirm}
              tone="warning"
              confirmText={messages.confirm}
              cancelText={messages.cancel}
              onConfirm={() => run('withdrawStep', () => flow.withdrawStep(id))}
              trigger={(props) => (
                <Button {...props} variant="outline" loading={busy === 'withdrawStep'} disabled={busy != null && busy !== 'withdrawStep'}>
                  {messages.withdrawStep}
                </Button>
              )}
            />
          </Flex>
        )}
        {detail.data && (
          <Card header={messages.diagram}>
            <FlowDiagram diagram={detail.data.diagram} height="16rem" />
          </Card>
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
