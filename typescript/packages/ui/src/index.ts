/**
 * 流程引擎的前端（@zerodep-dev/flow-ui）：建在 vue-tsx-pro 上，接引擎的 HTTP 接口（两个后端相同，0006 的 7.3）。
 *
 * - 客户端：createFlowClient、provideFlowClient / useFlowClient
 * - 页面级组件：收件箱 FlowInbox、待办详情 WorkItemDetail（表单、意见与处理结果、流转记录）、发起页 StartForm、
 *   处理结果的按钮区 ApprovalActions、流转记录 FlowTimeline
 * - 文案：withFlowLang 给组件库的语言加上 flow 的错误码文案；界面文案在 Lang.messages.flow 里改
 * - 表单（代码表单的写法、表单容器）在子路径 `@zerodep-dev/flow-ui/form`
 */
export {
  createFlowClient,
  provideFlowClient,
  useFlowClient,
  type FlowClient,
  type FlowClientOptions,
  type TodoPage,
} from './client/flow-client';
export type * from './client/types';
export { flowErrorCodes, flowMessages, useFlowMessages, withFlowLang, type FlowMessages } from './lang/messages';
export { ApprovalActions, type ApprovalActionsProps } from './runtime/ApprovalActions';
export { FlowInbox, type FlowInboxProps } from './runtime/FlowInbox';
export { FlowTimeline, type FlowTimelineProps } from './runtime/FlowTimeline';
export { StartForm, type StartFormProps } from './runtime/StartForm';
export { WorkItemDetail, type WorkItemDetailProps } from './runtime/WorkItemDetail';
