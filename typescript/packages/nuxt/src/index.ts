/**
 * 审批流程引擎接到 Nuxt / Nitro（@zerodep-dev/flow-nuxt，同 Java 的 flow-spring）：
 * {@link createFlowHandler} 是引擎的 HTTP 接口（h3 的处理函数，路径与返回值同 Spring 的），{@link startFlowJobs} 定时做到期的作业。
 * 引擎本身由 `@zerodep-dev/flow-core` 的 createFlowEngine 建，组织架构与当前操作人由应用给。
 */
export { createFlowHandler, type FlowHandlerOptions } from './handler.js';
export { startFlowJobs, type FlowJobs, type FlowJobsOptions } from './jobs.js';
