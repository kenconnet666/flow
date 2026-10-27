/**
 * 按字段清单自动排版的表单：表单还没有代码（只发布了清单）时容器用它。每个字段一个控件，按清单的先后从上往下排，
 * 控件按字段类型挑（default-control.tsx）；明细表用 form.List（每行一组列，能改时可以加行、删行）。
 * 想要别的排版、联动、远程数据时写代码表单（defineFlowForm）。
 */
import type { FormManifest } from '@zerodep-dev/flow-model';
import { type TsxComponent } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Flex } from '@zerodep-dev/vue-tsx-pro-ui';
import { defineFlowForm, type FlowFormComponentProps } from './define';
import { defaultControl } from './default-control';

/** 同一份清单只建一次组件（容器每次渲染都会要） */
const cache = new WeakMap<FormManifest, TsxComponent<FlowFormComponentProps>>();

/** 按清单自动排版的表单组件 */
export function autoFlowForm(manifest: FormManifest): TsxComponent<FlowFormComponentProps> {
  let component = cache.get(manifest);
  if (!component) {
    component = defineFlowForm(manifest, ({ form, context }) => () => (
      <form.Form>
        <Flex direction="column" gap="md">
          {Object.entries(manifest).map(([name, field]) =>
            field.type === 'list' ? (
              <form.List key={name} name={name} />
            ) : (
              <form.Field key={name} name={name}>
                {defaultControl(field, context.people, () => form.getValue(name as never))}
              </form.Field>
            ),
          )}
        </Flex>
      </form.Form>
    ));
    cache.set(manifest, component);
  }
  return component;
}
