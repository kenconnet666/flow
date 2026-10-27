/**
 * 表单的后端代码（0004 的 1.5，表单编辑器的"后端代码"页签）：一个 Java 类实现 FormHandler，跟表单版本一起发布。
 * "试编译"交给后端编译，问题带行列列在下面，点一条跳到代码里那个位置；发布时后端再编译一次，编不过不能发布。
 *
 *   <FormServerCode _value={serverSource} problems={serverProblems} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Button, Flex, Link, Small, useErrorMessage } from '@zerodep-dev/vue-tsx-pro-ui';
import { CodeEditor, type CodeEditorHandle } from '@zerodep-dev/vue-tsx-pro-ui/code-editor';
import { useFlowClient } from '../../client/flow-client';
import type { CompileProblem } from '../../client/types';
import { useFlowMessages } from '../../lang/messages';

/** 新写后端代码时的骨架 */
export const SERVER_SKELETON = `import org.dpdns.zerodep.flow.core.spi.*;

public class MyForm implements FormHandler {
    @Override
    public FieldErrors validate(FormValues values, FormContext ctx) {
        return FieldErrors.none();
    }
}
`;

/** FormServerCode 的属性 */
export interface FormServerCodeProps {
  /** 后端源码：父级写 _value={source} 双向绑定；空的表示没有后端代码 */
  _value?: string;
  /** 发布时后端报出的编译问题（不给时只显示试编译的） */
  problems?: readonly CompileProblem[] | null;
}

/** 后端代码的编辑与试编译 */
export const FormServerCode = _component(({ _value: source = _bindable(''), problems }: FormServerCodeProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let checked = _state<readonly CompileProblem[] | null>(null);
  let failure = _state<string | null>(null);
  let busy = _state(false);
  let editor: CodeEditorHandle | undefined;

  async function compile() {
    busy = true;
    failure = null;
    try {
      checked = await flow.compileFormCode(source);
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = false;
    }
  }

  return () => {
    const shown = checked ?? problems ?? null;
    return (
      <Flex direction="column" gap="sm">
        <Small tone="muted">{messages.serverCodeHint}</Small>
        <CodeEditor _value={source} aria-label={messages.formPanelServer} style={{ height: '28rem' }} onReady={(handle) => (editor = handle)} />
        <Flex gap="sm" align="center">
          {!source.trim() && (
            <Button variant="outline" onClick={() => (source = SERVER_SKELETON)}>
              {messages.serverCodeStart}
            </Button>
          )}
          <Button variant="outline" loading={busy} disabled={!source.trim()} onClick={compile}>
            {messages.compile}
          </Button>
        </Flex>
        {failure && <Alert tone="danger">{failure}</Alert>}
        {shown &&
          (shown.length === 0 ? (
            <Alert tone="success">{messages.compileOk}</Alert>
          ) : (
            <Alert tone="danger">
              <Flex direction="column" gap="xs">
                {shown.map((p, i) => (
                  <Link key={i} href="#" onClick={(event: MouseEvent) => (event.preventDefault(), editor?.goTo(p.line, p.column))}>
                    ✕ {messages.compileProblem(p.line, p.column, p.message)}
                  </Link>
                ))}
              </Flex>
            </Alert>
          ))}
      </Flex>
    );
  };
});
