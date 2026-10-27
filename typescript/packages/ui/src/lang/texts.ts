/**
 * 两种语言的文案共用的小工具：流转记录里事件数据的读法、问题代号的取法、哪些事件后面接节点名。
 */

/** 一个问题代号的说明：参数 → 文字 */
export type ProblemText = (p: Readonly<Record<string, string>>) => string;

/** 认不出的代号显示代号本身 */
export const problemText = (texts: Record<string, ProblemText>, code: string, params: Readonly<Record<string, string>>) =>
  Object.hasOwn(texts, code) ? texts[code]!(params) : code;

/** 标题后面接节点名的实例级事件（"跳转到 财务复核""收到事件 等对方盖章"） */
export const NODE_EVENTS = new Set(['instance.jumped', 'signal.received', 'node.overdue', 'node.skipped', 'job.failed', 'job.skipped']);

/** 事件数据里的名字（引擎存的快照） */
export const text = (value: unknown) => (typeof value === 'string' ? value : null);
export const names = (value: unknown) => (Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []);
/** 到达记录里的代理（后端 OrgDirectory.standIn 换了人时写的 standIns）：谁代谁 */
export const standInsOf = (data: Readonly<Record<string, unknown>>) =>
  (Array.isArray(data['standIns']) ? data['standIns'] : []).flatMap((s: Record<string, unknown>) => {
    const userName = text(s['userName']);
    const standInName = text(s['standInName']);
    return userName && standInName ? [{ userName, standInName }] : [];
  });
