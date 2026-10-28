/**
 * 把六个搭建教程画出的流程与报销单从示例后端导出成 tutorials.json（"重置演示"教程做完那一档发布它们）。
 * 截图脚本按教程真实操作过一遍之后（`pnpm seed` → `pnpm shots`）在 typescript/apps/demo 下 `pnpm seed:export`；
 * 后端不在 http://localhost:8080 时设 FLOW_BACKEND。顺序就是发布的顺序：被引用的在前（报销单在报销之前、合同审批在大额采购之前）。
 */
import { writeFileSync } from 'node:fs';

const backend = process.env.FLOW_BACKEND ?? 'http://localhost:8080';
const FORMS = ['expense-form'];
const PROCESSES = ['expense', 'review', 'contract', 'trip', 'bigbuy', 'esign'];

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${backend}/flow${path}`, { headers: { 'x-user': '1004' } });
  const result = (await response.json()) as { code: string; data?: T };
  if (result.code !== 'ok') throw new Error(`${path}: ${result.code}`);
  return result.data as T;
}

interface FormDocument {
  key: string;
  name: string;
  category: string | null;
  manifest: string;
  clientSource: string | null;
  serverLanguage: string | null;
  serverSource: string | null;
}

const forms = [];
for (const key of FORMS) {
  const doc = await get<FormDocument>(`/queries/forms/${key}/document`);
  forms.push({
    key: doc.key,
    name: doc.name,
    category: doc.category,
    manifest: JSON.parse(doc.manifest) as object,
    clientSource: doc.clientSource,
    serverLanguage: doc.serverLanguage,
    serverSource: doc.serverSource,
    note: '教程做完',
  });
}
const processes = [];
for (const key of PROCESSES) {
  const { json } = await get<{ json: string }>(`/queries/processes/${key}/definition`);
  processes.push(JSON.parse(json) as object);
}

writeFileSync(new URL('tutorials.json', import.meta.url), `${JSON.stringify({ forms, processes }, null, 2)}\n`);
console.log(`导出了 ${forms.length} 张表单、${processes.length} 个流程`);
