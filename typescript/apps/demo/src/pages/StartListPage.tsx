/**
 * 发起：我发起前存的草稿（接着填、放弃），以及我能发起的流程，点了进发起页。
 */
import { FlowStartDrafts, useFlowClient } from '@zerodep-dev/flow-ui';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Card,
  Empty,
  Grid,
  H1,
  Link,
  Paragraph,
  Flex,
  Skeleton,
  useErrorMessage,
  useRequest,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useRouter } from 'vue-router';

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
        <Grid minItemWidth="15rem" gap="md">
          {processes.data.map((process) => (
            <Card key={process.key}>
              <Link href={`/start/${process.key}`}>{process.name}</Link>
              <Paragraph tone="muted">{`${process.category ?? '未分类'} · 第 ${process.version} 版`}</Paragraph>
            </Card>
          ))}
        </Grid>
      )}
    </Flex>
  );
});
