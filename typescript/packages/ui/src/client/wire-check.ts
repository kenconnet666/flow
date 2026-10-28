/**
 * 后端有没有把 /flow 的响应写成 typed JSON：这是接入方的配置（Spring 里的 WireResponseAdvice，接入文档 1.6），引擎不替它转换。
 * 引擎的结果里处处有编号 id，配好时还原成 bigint；没配时是普通数字，同时小数成了浮点、大编号丢精度、日期是字符串，
 * 页面不报错却悄悄算错。看到普通数字的 id 时在控制台说一次怎么改；只检查，不改数据。
 *
 *   const check = createWireCheck();
 *   const data = check(await api.get('/queries/todo'));
 */

/** 提示的文字 */
export const WIRE_MISSING =
  '[flow] /flow 接口的响应不是 typed JSON（编号 id 是普通数字，应当还原成 bigint）：后端没把它写成 typed JSON。' +
  '在 Spring 里加 WireResponseAdvice，basePackages 要含 org.dpdns.zerodep.flow.spring；前端的 createHttpClient 配上 wire。见接入文档 1.6';

/** 往下看几层：引擎的结果在头几层就有 id，不必走完整棵树 */
const DEPTH = 4;
/** 列表只看前几行 */
const ROWS = 3;

/** 建一个检查函数：原样返回数据，第一次发现普通数字的 id 时调 report（默认 console.error），之后不再说 */
export function createWireCheck(report: (message: string) => void = (message) => console.error(message)) {
  let reported = false;
  return <T>(data: T): T => {
    if (!reported && hasNumberId(data, DEPTH)) {
      reported = true;
      report(WIRE_MISSING);
    }
    return data;
  };
}

function hasNumberId(value: unknown, depth: number): boolean {
  if (depth < 0 || value === null || typeof value !== 'object') return false;
  if (Array.isArray(value)) return value.slice(0, ROWS).some((item) => hasNumberId(item, depth - 1));
  const record = value as Record<string, unknown>;
  if (typeof record['id'] === 'number') return true;
  return Object.values(record).some((child) => hasNumberId(child, depth - 1));
}
