/**
 * 发起：我发起前存的草稿（接着填、放弃），以及我能发起的流程，点了进发起页。
 */
import { FlowStartDrafts, useFlowClient, type StartableProcess } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Card,
  Empty,
  Grid,
  H1,
  H2,
  Link,
  Flex,
  Skeleton,
  useErrorMessage,
  useRequest,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useRouter } from 'vue-router';

/** 按分类分组：一个分类都没写时不分（键是 undefined），没写分类的放最后 */
function groups(list: readonly StartableProcess[]): [string | null | undefined, StartableProcess[]][] {
  if (list.every((p) => p.category == null)) return [[undefined, [...list]]];
  const byCategory = new Map<string | null, StartableProcess[]>();
  for (const p of list) byCategory.set(p.category, [...(byCategory.get(p.category) ?? []), p]);
  return [...byCategory.entries()].sort(([a], [b]) => (a == null ? 1 : b == null ? -1 : a.localeCompare(b, 'zh')));
}

export const StartListPage = _component(() => {
  const flow = useFlowClient();
  const router = useRouter();
  const errorText = useErrorMessage();
  const processes = useRequest((_: undefined, signal) => flow.startable(signal));
  return () => (
    <Flex direction="column" gap="lg">
      <H1>发起</H1>
      <FlowStartDrafts onOpen={(draft) => void router.push(`/start/${draft.processKey}?draft=${draft.id}`)} />
      {processes.error ? (
        <Alert tone="danger">{errorText(processes.error)}</Alert>
      ) : !processes.data ? (
        <Skeleton />
      ) : processes.data.length === 0 ? (
        <Empty title="没有能发起的流程" />
      ) : (
        // 有分类时按分类分组（没写分类的放最后的"其他"）；版本号对发起的人没用，不显示
        groups(processes.data).map(([category, list]) => (
          <Flex key={category ?? ''} direction="column" gap="sm">
            {category !== undefined && <H2>{category ?? '其他'}</H2>}
            <Grid minItemWidth="15rem" gap="md">
              {list.map((process) => (
                <Card key={process.key}>
                  <Link href={`/start/${process.key}`}>{process.name}</Link>
                </Card>
              ))}
            </Grid>
          </Flex>
        ))
      )}
    </Flex>
  );
});
