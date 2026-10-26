// 引擎的 HTTP 接口：/flow/commands/*、/flow/queries/*……路径与返回值同 Spring 示例
import { createFlowHandler } from '@zerodep-dev/flow-nuxt';
import { currentUser, engine } from '../../flow';

export default createFlowHandler({ engine, actor: currentUser });
