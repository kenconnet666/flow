/**
 * 代码表单的写法（0004 第 1 节）：
 *
 *   export const fields = defineFields({ title: { label: '标题', type: 'text', required: true }, … });
 *   export default defineFlowForm(fields, ({ form, context }) => () => (
 *     <form.Form>
 *       <form.Field name="title"><TextField /></form.Field>
 *       <form.Field name="amount"><NumberField /></form.Field>
 *       <form.Field name="buyer"><PeoplePicker source={context.people!} /></form.Field>
 *     </form.Form>
 *   ));
 *
 * - 标签、必填标记、说明从清单来，form.Field 不用再写；节点权限与显示条件合算出的状态（隐藏、只读、必填）自动套上。
 * - decimal 字段在表单里是 Decimal（NumberField 按 Decimal 读写，不经浮点），交给引擎时按小数位写回文字。
 * - 校验用模型包的规则（必填、类型、长度、范围……），与两个后端是同一套；跨字段的规则写在 options.validate 里。
 * - 表单由容器（FlowForm）渲染：容器给 context，表单把"校验、取值"交回容器，提交按钮在审批面板上。
 */
import {
  fieldStates,
  mergeSubmission,
  type FieldState,
  type FormManifest,
} from '@zerodep-dev/flow-model';
import { _component, type TsxComponent } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Field,
  Form,
  useForm,
  type FieldProps,
  type FormController,
  type FormErrors,
  type FormProps,
  type PeopleEntry,
} from '@zerodep-dev/vue-tsx-pro-ui';
import Decimal from 'decimal.js';
import { cloneVNode, computed, isVNode, type VNode, type VNodeChild } from 'vue';
import { useFlowMessages } from '../lang/messages';
import type { FlowFormContext, FlowFormScope } from './context';
import { formPath, toFormState, type FormState } from './values';

/** 一个字段在表单里的值的类型 */
type ValueOf<F> = F extends { readonly type: 'text' | 'date' | 'datetime' }
  ? string | null
  : F extends { readonly type: 'number' }
    ? number | null
    : F extends { readonly type: 'decimal' }
      ? Decimal | null
      : F extends { readonly type: 'boolean' }
        ? boolean
        : F extends { readonly type: 'select'; readonly multiple: true }
          ? (string | number)[]
          : F extends { readonly type: 'select' }
            ? string | number | null
            : F extends { readonly type: 'people' }
              ? PeopleEntry[]
              : F extends { readonly type: 'file' }
                ? { id: string; name: string; size: number }[]
                : F extends { readonly type: 'list'; readonly fields: infer C extends FormManifest }
                  ? ValuesOf<C>[]
                  : unknown;

/** 按清单推出的表单的值：form.values 的类型，form.Field 的 name 据此补全、写错报错 */
export type ValuesOf<M extends FormManifest> = { -readonly [K in keyof M]: ValueOf<M[K]> };

/** 字段清单：只能是字面量（0004 的 1.2，设计器要能改它）。恒等函数，给类型推断用 */
export function defineFields<const M extends FormManifest>(fields: M): M {
  return fields;
}

/** form.Form：组件库的 Form，绑好了这份表单的控制器；查看时整份只读 */
export type FlowFormFormProps = Omit<FormProps, 'controller'>;

/** form.Field：组件库的 Field，标签、必填、只读、隐藏都按清单与权限给好 */
export type FlowFieldProps<M extends FormManifest> = Omit<FieldProps, 'name'> & {
  /** 字段名；明细表的格子写成 `lines.0.price` */
  name: keyof M & string;
};

/** 表单代码里的 form：useForm 的控制器，加上绑好的 Form、Field */
export interface FlowFormApi<M extends FormManifest> extends Omit<FormController<ValuesOf<M>>, 'Field'> {
  readonly Form: TsxComponent<FlowFormFormProps>;
  readonly Field: TsxComponent<FlowFieldProps<M> | (Omit<FieldProps, 'name'> & { name: string })>;
}

/** defineFlowForm 的选项 */
export interface FlowFormOptions<M extends FormManifest> {
  /** 前端的跨字段校验：返回字段路径 → 错误信息（同 useForm 的 validate），与清单的规则合并 */
  validate?: (values: ValuesOf<M>) => FormErrors | Promise<FormErrors>;
}

/** 表单组件的属性：容器给的 context */
export interface FlowFormComponentProps {
  context: FlowFormContext;
}

/**
 * 定义一张代码表单：setup 拿到 form 与 context，返回渲染函数（setup 里可以写任意逻辑：远程数据、计算、监听）。
 * 默认导出它，容器（FlowForm）按 context 渲染。
 */
export function defineFlowForm<const M extends FormManifest>(
  fields: M,
  setup: (scope: { form: FlowFormApi<M>; context: FlowFormScope }) => () => VNodeChild,
  options: FlowFormOptions<M> = {},
): TsxComponent<FlowFormComponentProps> {
  return _component(({ context }: FlowFormComponentProps) => {
    const messages = useFlowMessages();
    const manifest: FormManifest = fields;
    const controller = useForm<FormState>({
      initial: toFormState(manifest, context.values),
      validate: async (values) => ({
        ...problemsOf(values),
        ...(await options.validate?.(values as ValuesOf<M>)),
      }),
    });

    // 节点权限与显示条件合算的状态：显示条件里的 form.x 按当前填的值算
    const states = computed(() =>
      fieldStates(manifest, {
        access: context.nodeAccess,
        isStart: context.start,
        read: (name) => (name.startsWith('form.') ? controller.values[name.slice(5)] : context.read(name)),
      }),
    );
    const stateOf = (path: string): FieldState =>
      states.value.get(manifestPath(path)) ?? { visible: false, editable: false, required: false, hiddenBy: 'access' };

    /** 清单的规则：必填、类型、长度、范围……（模型包的 mergeSubmission，与后端同一套） */
    function problemsOf(values: FormState): FormErrors {
      const { errors } = mergeSubmission(manifest, states.value, values, values);
      const out: FormErrors = {};
      for (const error of errors) out[formPath(error.field)] ??= messages.valueProblem(error.code);
      return out;
    }

    const FlowFormForm = _component(({ children, ...rest }: FlowFormFormProps) => () => (
      <Form controller={controller} readonly={context.mode === 'view'} {...rest}>
        {children}
      </Form>
    ));

    const FlowField = _component(({ name, children, ...rest }: Omit<FieldProps, 'name'> & { name: string }) => () => {
      const state = stateOf(name);
      if (!state.visible) return null;
      const field = manifest[manifestPath(name)] ?? columnOf(manifest, name);
      return (
        <Field
          name={name}
          label={field?.label}
          description={field?.description}
          required={state.required}
          readonly={!state.editable || context.mode === 'view'}
          {...rest}
        >
          {controlOf(children, field?.type === 'decimal')}
        </Field>
      );
    });

    context.attach({
      // 当作一次提交：校验并显示全部错误（组件库的规则是失焦或提交过之后才显示），之后随输入实时更新
      validate: () => controller.submit(),
      values: () => ({ ...controller.values }),
      showErrors: (errors) => {
        for (const error of errors) {
          if (error.field) controller.setError(formPath(error.field) as never, messages.valueProblem(error.code ?? ''));
        }
      },
    });

    // 控制器原样转发，只把 Form、Field 换成绑好的
    const form = new Proxy(controller, {
      get: (target, key, receiver) => (key === 'Form' ? FlowFormForm : key === 'Field' ? FlowField : Reflect.get(target, key, receiver)),
    }) as unknown as FlowFormApi<M>;
    const scope: FlowFormScope = { ...context, access: stateOf };
    return setup({ form, context: scope });
  });
}

/** 表单路径 → 清单里的路径：去掉明细表的行号（`lines.0.price` → `lines.price`） */
const manifestPath = (path: string) => path.replace(/\.\d+(?=\.|$)/g, '');

/** 明细表的一列 */
function columnOf(manifest: FormManifest, path: string) {
  const [list, column] = manifestPath(path).split('.');
  return list && column ? manifest[list]?.fields?.[column] : undefined;
}

/**
 * 交给组件库 Field 的控件：只有一个控件时拿出这一个 vnode（Field 认出"只有一个控件"才自动绑定值，包在数组里认不出）。
 * decimal 字段的控件（NumberField）补上 valueType：一开始为空时也按 Decimal 写回
 */
function controlOf(children: unknown, decimal: boolean): VNodeChild {
  const only = Array.isArray(children) && children.length === 1 ? children[0] : children;
  if (!isVNode(only)) return children as VNodeChild;
  const props = (only as VNode).props ?? {};
  if (!decimal || 'valueType' in props || 'value-type' in props) return only;
  return cloneVNode(only, { valueType: Decimal });
}
