/**
 * 正在改的定义与某个已发布版本差在哪：流程属性，加了、删了、改了的节点、连线、泳道（改了哪些属性）。
 * 差别由模型包的 diffProcess 算（布局不算），这里只负责列出来。
 *
 *   <DefinitionDiffView version={3} diff={diffProcess(published, editing)} />
 */
import type { ItemDiff, ProcessDiff } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Badge, Flex, Text } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowMessages } from '../lang/messages';

/** DefinitionDiffView 的属性 */
export interface DefinitionDiffViewProps {
  /** 和哪个版本比 */
  version: number;
  diff: ProcessDiff;
}

const emptyItems = (items: ItemDiff) => items.added.length + items.removed.length + items.changed.length === 0;

/** 版本对比的结果 */
export const DefinitionDiffView = _component(({ version, diff }: DefinitionDiffViewProps) => {
  const messages = useFlowMessages();

  /** 一类东西的差别：每个一行，前面是加、删、改的徽标 */
  const section = (title: string, items: ItemDiff) =>
    emptyItems(items) ? null : (
      <Flex direction="column" gap="xs">
        <Text weight="medium">{title}</Text>
        {items.added.map((id) => (
          <Text key={`+${id}`}>
            <Badge tone="success">{messages.diffAdded}</Badge> {id}
          </Text>
        ))}
        {items.removed.map((id) => (
          <Text key={`-${id}`}>
            <Badge tone="danger">{messages.diffRemoved}</Badge> {id}
          </Text>
        ))}
        {items.changed.map((c) => (
          <Text key={`~${c.id}`}>
            <Badge tone="warning">{messages.diffChanged}</Badge> {c.id}：{c.keys.join('、')}
          </Text>
        ))}
      </Flex>
    );

  return () => {
    const same = diff.process.length === 0 && emptyItems(diff.nodes) && emptyItems(diff.edges) && emptyItems(diff.lanes);
    if (same) return <Alert tone="info">{messages.diffNone(version)}</Alert>;
    return (
      <Alert tone="info" title={messages.diffTitle(version)}>
        <Flex direction="column" gap="sm">
          {diff.process.length > 0 && (
            <Text>
              <Badge tone="warning">{messages.diffChanged}</Badge> {messages.diffProcess}：{diff.process.join('、')}
            </Text>
          )}
          {section(messages.diffNodes, diff.nodes)}
          {section(messages.diffEdges, diff.edges)}
          {section(messages.diffLanes, diff.lanes)}
        </Flex>
      </Alert>
    );
  };
});
