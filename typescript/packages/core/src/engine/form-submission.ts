/**
 * 收表单（0004 的 4.5，同 Java 的 FormSubmission）：按节点上的字段权限，能改的字段取提交的值，其余用存着的值，再查必填与值的类型。
 * 规则都在模型包（fieldStates、mergeSubmission），前端与两个后端用同一份。
 */
import { FlowError, fieldStates, mergeSubmission, type FormManifest, type NodeFormAccess } from '@zerodep-dev/flow-model';
import type { JsonRecord } from '../store/types.js';

/**
 * 合并出新的表单值；有问题时报 `flow.validation-failed`，errors 里是按字段的问题。
 *
 * @param isStart 是不是发起：发起时没写权限的字段默认能改
 * @param read    取 `sys.`、流程变量这类引用（显示条件里可能用到）
 */
export function mergeForm(
  manifest: FormManifest,
  access: NodeFormAccess | undefined,
  isStart: boolean,
  stored: JsonRecord,
  submitted: JsonRecord,
  read: (name: string) => unknown,
): Record<string, unknown> {
  // 显示条件按提交后的值算：同一次提交里改了的字段，立刻影响别的字段显不显示
  const afterSubmit = { ...stored, ...submitted };
  const readAfterSubmit = (name: string) => (name.startsWith('form.') ? (afterSubmit[name.slice('form.'.length)] ?? null) : read(name));
  const states = fieldStates(manifest, { access, isStart, read: readAfterSubmit });
  const submission = mergeSubmission(manifest, states, stored, submitted);
  if (submission.errors.length > 0) {
    throw new FlowError('flow.validation-failed', {
      message: `the form has ${submission.errors.length} problem(s)`,
      errors: submission.errors.map((e) => ({ code: e.code, field: e.field })),
    });
  }
  return submission.values;
}
