/**
 * 发起页：流程开始节点上的表单加"提交"。提交前按模型包的规则校验，引擎退回的按字段的问题标回表单。
 * 第一步要发起人选处理人（StartView.picks）时，提交先弹 PickDialog 选人。
 * "存草稿"把填了一半的存成发起前的草稿（不查必填）；给了 draftId 时接着那份草稿填，提交就从它发起。
 *
 *   <StartForm processKey="purchase" draftId={route.query.draft} people={peopleSource} onStarted={(result) => router.push(…)} />
 */
import type { Id } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Flex,
  HttpError,
  Skeleton,
  Text,
  useErrorMessage,
  useRequest,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../client/flow-client';
import type { CommandResult } from '../client/types';
import type { FlowFormHandle } from '../form/context';
import { FlowForm } from '../form/FlowForm';
import { useFlowMessages } from '../lang/messages';
import { PickDialog } from './PickDialog';

/** StartForm 的属性 */
export interface StartFormProps {
  /** 流程标识 */
  processKey: string;
  /** 人员字段、选第一步处理人的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 业务单据编号（从业务页面发起时带上） */
  businessKey?: string;
  /** 接着这份发起前的草稿填 */
  draftId?: Id | null;
  /** 发起成功 */
  onStarted?: (result: CommandResult) => void;
}

/** 发起页 */
export const StartForm = _component(({ processKey, people, modules, businessKey, draftId = null, onStarted }: StartFormProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const view = useRequest(([key, draft]: readonly [string, Id | null], signal) => flow.startView(key, signal, draft), {
    params: () => [processKey, draftId] as const,
  });
  let form: FlowFormHandle | null = null;
  let busy = _state<'submit' | 'draft' | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  /** 这次存成的草稿：之后再存是改它，提交是从它发起 */
  let draft = _state<Id | null>(draftId);

  async function saveDraft() {
    busy = 'draft';
    failure = null;
    try {
      const saved = await flow.saveStartDraft({
        ...(draft != null ? { draftId: draft } : { key: processKey }),
        ...(form ? { form: form.values() } : {}),
        ...(businessKey ? { businessKey } : {}),
      });
      draft = saved.id;
      notice = messages.startDraftSaved;
    } catch (error) {
      if (error instanceof HttpError && error.code === 'flow.validation-failed' && error.errors?.length) form?.showErrors(error.errors);
      else failure = errorText(error);
    } finally {
      busy = null;
    }
  }
  let pickOpen = _state(false);

  /** 提交：先校验表单；要选第一步的处理人时先弹选人框 */
  async function submit() {
    failure = null;
    notice = null;
    if (form && !(await form.validate())) return;
    if (view.data?.picks.length) pickOpen = true;
    else await send({});
  }

  /** 发起；返回成功没有（选人框按它决定关不关） */
  async function send(picks: Record<string, Id[]>): Promise<boolean> {
    busy = 'submit';
    try {
      const result = await flow.start({
        key: processKey,
        ...(draft != null ? { draftId: draft } : {}),
        ...(form ? { form: form.values() } : {}),
        ...(businessKey ? { businessKey } : {}),
        ...(Object.keys(picks).length ? { picks } : {}),
      });
      onStarted?.(result);
      return true;
    } catch (error) {
      if (error instanceof HttpError && error.code === 'flow.validation-failed' && error.errors?.length) form?.showErrors(error.errors);
      else failure = errorText(error);
      return false;
    } finally {
      busy = null;
    }
  }

  return () => {
    const data = view.data;
    if (view.error && !data) return <Alert tone="danger">{errorText(view.error)}</Alert>;
    if (!data) return <Skeleton />;
    return (
      <Card header={data.process.name}>
        <Flex direction="column" gap="lg">
          {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
          {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
          {data.form ? (
            <FlowForm
              view={data.form}
              {...(people ? { people } : {})}
              {...(modules ? { modules } : {})}
              onReady={(handle) => (form = handle)}
            />
          ) : (
            <Text tone="muted">{messages.noForm}</Text>
          )}
          <Flex gap="sm">
            <Button loading={busy === 'submit'} disabled={busy != null} onClick={submit}>
              {messages.submit}
            </Button>
            <Button variant="outline" loading={busy === 'draft'} disabled={busy != null} onClick={saveDraft}>
              {messages.saveDraft}
            </Button>
          </Flex>
          {data.picks.length > 0 && <PickDialog _open={pickOpen} picks={data.picks} {...(people ? { people } : {})} onConfirm={send} />}
        </Flex>
      </Card>
    );
  };
});
