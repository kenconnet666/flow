/** 介绍：流程引擎是什么、能做什么、由哪几块组成 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';

export const meta = {
  title: '介绍',
  description: '一个嵌进 Spring Boot 业务系统的审批流程引擎：在浏览器里画流程、画表单，业务人员在待办里处理，程序员按需扩展。',
  order: 1,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="what" title="能做什么">
      <Paragraph>占位</Paragraph>
    </Section>
  </DocPage>
));
