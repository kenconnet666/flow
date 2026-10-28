/**
 * 文档页的骨架：标题、简介，后面是一节一节的正文（Section）。正文是适合阅读的宽度、比界面文字大一号。
 * 页面 meta 写了 try 时，简介下面有"边看边做"：在演示里打开那一页，旁边的面板显示这一篇（面板里不再显示它）。
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
import { MonitorPlay } from '@lucide/icons';
import { Button, Container, Flex, H1, H2, H3, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';
import { useRouter } from 'vue-router';
import type { PageMeta } from './nav';
import { InDocsPanel, followAlong } from './panel';

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
export const DocPage = _component(({ meta, children }: DocPageProps) => {
  const router = useRouter();
  const inPanel = InDocsPanel.inject(false);
  return () => (
    <Container size="md" padding="none">
      <article {...css.attrs(styles.article)}>
        <Flex direction="column" gap="lg" align="start">
          <H1>{meta.title}</H1>
          <Paragraph size="lg" tone="muted" sx={styles.lead}>
            {meta.description}
          </Paragraph>
          {meta.try && !inPanel && (
            // 不在面板里时这一页就是当前路由
            <Button icon={MonitorPlay} onClick={() => followAlong(router, { path: router.currentRoute.value.path, try: meta.try })}>
              在演示里边看边做
            </Button>
          )}
        </Flex>
        {children}
      </article>
    </Container>
  );
});

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
