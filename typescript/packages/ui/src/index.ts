/**
 * 流程引擎的前端（@zerodep-dev/flow-ui）：建在 vue-tsx-pro 上，接引擎的 HTTP 接口（两个后端相同，0006 的 7.3）。
 *
 * - 客户端：createFlowClient、provideFlowClient / useFlowClient
 * - 页面级组件：待办列表 FlowInbox（我的待办、我已办）、我发起的 FlowStartedList、抄送我的 FlowCcList、
 *   待办详情 WorkItemDetail（表单、意见与处理结果、协作、流转记录）、流程详情 FlowInstanceDetail（进度、撤回、催办、留言）、
 *   发起页 StartForm；部件：处理结果的按钮区 ApprovalActions、协作 CollaborationActions、流转记录 FlowTimeline
 * - 文案：withFlowLang 给组件库的语言加上 flow 的错误码文案；界面文案在 Lang.messages.flow 里改
 * - 表单（代码表单的写法、表单容器）在子路径 `@zerodep-dev/flow-ui/form`
 */
export {
  createFlowClient,
  provideFlowClient,
  useFlowClient,
  type FlowClient,
  type FlowClientOptions,
  type FlowPage,
  type PageQuery,
  type TodoPage,
} from './client/flow-client';
export type * from './client/types';
export { flowErrorCodes, flowMessages, useFlowMessages, withFlowLang, type FlowMessages } from './lang/messages';
export { ApprovalActions, type ApprovalActionsProps } from './runtime/ApprovalActions';
export { CollaborationActions, type Collaboration, type CollaborationActionsProps } from './runtime/CollaborationActions';
export { FlowInbox, type FlowInboxProps } from './runtime/FlowInbox';
export { FlowInstanceDetail, type FlowInstanceDetailProps } from './runtime/FlowInstanceDetail';
export { FlowCcList, type FlowCcListProps } from './runtime/FlowCcList';
export { FlowStartedList, type FlowStartedListProps } from './runtime/FlowStartedList';
export { PickDialog, type PickDialogProps } from './runtime/PickDialog';
export { FlowTimeline, type FlowTimelineProps } from './runtime/FlowTimeline';
export { StartForm, type StartFormProps } from './runtime/StartForm';
export { WorkItemDetail, type WorkItemDetailProps } from './runtime/WorkItemDetail';
