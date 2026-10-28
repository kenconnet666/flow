/**
 * 操作步骤：带序号的一串动作，每步一句话（要点的按钮、要填的值用 <Code> 或粗体）。
 *
 *   <Ops items={['打开 流程管理，点 新建流程', <>在 流程标识 里填 <Code>expense</Code></>]} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import type { VNodeChild } from 'vue';

const styles = css.create({
  list: { margin: 0, paddingInlineStart: '1.5em', display: 'flex', flexDirection: 'column', gap: '_xs' },
  item: { paddingInlineStart: '0.25em' },
});

/** Ops 的属性 */
export interface OpsProps {
  items: readonly VNodeChild[];
}

/** 操作步骤 */
export const Ops = _component(({ items }: OpsProps) => () => (
  <ol {...css.attrs(styles.list)}>
    {items.map((item, i) => (
      <li key={i} {...css.attrs(styles.item)}>
        {item}
      </li>
    ))}
  </ol>
));
