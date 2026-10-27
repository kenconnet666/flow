/**
 * 表单的标签页：里面的每个"分组"（FormSection）是一页，分组的标题是标签。表单设计器里把"标签页"拖进来，再往里拖分组就是加一页：
 *
 *   <FormTabs aria-label="请假单">
 *     <FormSection title="基本信息">…</FormSection>
 *     <FormSection title="证明材料">…</FormSection>
 *   </FormTabs>
 *
 * - 各页都渲染着（只是藏起来），没切到的页里的字段照样取值、校验；
 * - 设计器的画布上（元素带 data-form-node）各页摊开排，每个字段都能选中、拖动。
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Flex, Tab, TabList, TabPanel, Tabs } from '@zerodep-dev/vue-tsx-pro-ui';
import { cloneVNode, Fragment, isVNode, type VNode, type VNodeChild } from 'vue';

/** FormTabs 的属性；别的属性落到外层 */
export interface FormTabsProps {
  /** 标签栏的无障碍名字 */
  'aria-label'?: string;
  children?: VNodeChild;
}

/** 标签页：每个分组一页 */
export const FormTabs = _component(({ children, 'aria-label': label, ...rest }: FormTabsProps & Record<string, unknown>) => {
  let current = _state('0');
  return () => {
    const pages = flatten(children);
    if ('data-form-node' in rest) {
      return (
        <Flex direction="column" gap="md" {...rest}>
          {pages}
        </Flex>
      );
    }
    return (
      <Tabs _value={current} {...rest}>
        <TabList {...(label ? { 'aria-label': label } : {})}>
          {pages.map((page, i) => (
            <Tab key={i} value={String(i)}>
              {titleOf(page) ?? String(i + 1)}
            </Tab>
          ))}
        </TabList>
        {pages.map((page, i) => (
          // 标题已经在标签上：页里的分组不再显示一遍
          <TabPanel key={i} value={String(i)}>
            {cloneVNode(page, { title: undefined })}
          </TabPanel>
        ))}
      </Tabs>
    );
  };
});

/** 子元素拍平成一列元素：展开数组与 Fragment（列表渲染、插槽包的那一层）；条件渲染留下的空值、文字不算一页 */
function flatten(children: VNodeChild): VNode[] {
  if (Array.isArray(children)) return children.flatMap((child) => flatten(child as VNodeChild));
  if (!isVNode(children)) return [];
  return children.type === Fragment ? flatten(children.children as VNodeChild) : [children];
}

function titleOf(page: VNode): string | undefined {
  const title = (page.props as { title?: unknown } | null)?.title;
  return typeof title === 'string' ? title : undefined;
}
