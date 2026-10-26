/**
 * 发起页：流程开始节点上的表单加"提交"。提交前按模型包的规则校验，引擎退回的按字段的问题标回表单。
 *
 *   <StartForm processKey="purchase" people={peopleSource} onStarted={(result) => router.push(…)} />
 */
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

/** StartForm 的属性 */
export interface StartFormProps {
  /** 流程标识 */
  processKey: string;
  /** 人员字段的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块（见 FlowForm） */
  modules?: Readonly<Record<string, unknown>>;
  /** 业务单据编号（从业务页面发起时带上） */
  businessKey?: string;
  /** 发起成功 */
  onStarted?: (result: CommandResult) => void;
}

/** 发起页 */
export const StartForm = _component(({ processKey, people, modules, businessKey, onStarted }: StartFormProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  const view = useRequest((key: string, signal) => flow.startView(key, signal), { params: () => processKey });
  let form: FlowFormHandle | null = null;
  let busy = _state(false);
  let failure = _state<string | null>(null);

  async function submit() {
    failure = null;
    if (form && !(await form.validate())) return;
    busy = true;
    try {
      const result = await flow.start({
        key: processKey,
        ...(form ? { form: form.values() } : {}),
        ...(businessKey ? { businessKey } : {}),
      });
      onStarted?.(result);
    } catch (error) {
      if (error instanceof HttpError && error.code === 'flow.validation-failed' && error.errors?.length) form?.showErrors(error.errors);
      else failure = errorText(error);
    } finally {
      busy = false;
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
          <Flex>
            <Button loading={busy} onClick={submit}>
              {messages.submit}
            </Button>
          </Flex>
        </Flex>
      </Card>
    );
  };
});
