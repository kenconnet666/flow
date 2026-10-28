/**
 * 文档页的骨架：标题、简介，后面是一节一节的正文（Section）。正文是适合阅读的宽度、比界面文字大一号。
 *
 *   export const meta = { title: '教程 1：报销', description: '…', order: 2 };
 *   export default _component(() => () => (
 *     <DocPage meta={meta}>
 *       <Section id="draw" title="画流程">…</Section>
 *     </DocPage>
 *   ));
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import * as css from '@zerodep-dev/vue-tsx-pro-tsx/styles';
import { Container, Flex, H1, H2, H3, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';
import type { PageMeta } from './nav';

const styles = css.create({
  article: { display: 'flex', flexDirection: 'column', gap: '2.5rem', fontSize: '15px', lineHeight: '_relaxed' },
  lead: { maxWidth: '48rem', lineHeight: '_relaxed' },
});

/** DocPage 的属性 */
export interface DocPageProps {
  meta: PageMeta;
  children?: VNodeChild;
}

/** 文档页 */
export const DocPage = _component(({ meta, children }: DocPageProps) => () => (
  <Container size="md" padding="none">
    <article {...css.attrs(styles.article)}>
      <Flex direction="column" gap="lg">
        <H1>{meta.title}</H1>
        <Paragraph size="lg" tone="muted" sx={styles.lead}>
          {meta.description}
        </Paragraph>
      </Flex>
      {children}
    </article>
  </Container>
));

/** Section 的属性 */
export interface SectionProps {
  /** 锚点：本页目录与链接用 */
  id: string;
  title: string;
  /** 小节（h3）；默认是节（h2） */
  sub?: boolean;
  children?: VNodeChild;
}

/** 一节：带锚点的标题，下面是内容 */
export const Section = _component(({ id, title, sub = false, children }: SectionProps) => () => (
  <Flex direction="column" gap="md">
    {sub ? (
      <H3 id={id} anchor>
        {title}
      </H3>
    ) : (
      <H2 id={id} size="xl" anchor>
        {title}
      </H2>
    )}
    {children}
  </Flex>
));
