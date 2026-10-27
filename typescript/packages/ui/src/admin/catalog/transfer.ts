/**
 * 流程、表单的导出、导入、复制（0002 §9、0004 的 5.6）：导出成一份 JSON 文件；导入与复制都走发布接口（后端照常校验），
 * 复制是取最新版本换个标识和名字再发布成新的一个。表单不导出编译结果；有前端源码的，发布前按源码重新提取字段清单、
 * 重新编译，不信文件里带的清单。
 *
 *   const transfer = processTransfer(flow);
 *   downloadJson(`${key}.json`, await transfer.exportJson(key));
 *   await transfer.publish(json, { key: 'leave-copy', name: '请假（副本）' });
 */
import { downloadFile } from '@zerodep-dev/vue-tsx-pro-ui';
import { parseDefinition, type FormManifest } from '@zerodep-dev/flow-model';
import type { FlowClient } from '../../client/flow-client';
import type { FormPublication } from '../../client/types';

/** 复制时的新标识与名字 */
export interface Rename {
  readonly key: string;
  readonly name: string;
}

/** 一类能导出、导入的东西 */
export interface Transfer {
  /** 最新版本导出成的 JSON 文字 */
  exportJson(key: string): Promise<string>;
  /** 发布一份导出的 JSON；给了 rename 时换成新的标识和名字（复制） */
  publish(json: string, rename?: Rename, note?: string): Promise<unknown>;
}

/** 流程定义：导出的就是定义本身 */
export function processTransfer(flow: FlowClient): Transfer {
  return {
    exportJson: async (key) => (await flow.definitionJson(key)).json,
    publish: (json, rename, note) => {
      const definition = parseDefinition(json);
      return flow.publishDefinition(JSON.stringify(rename ? { ...definition, ...rename } : definition), note);
    },
  };
}

/** 表单：导出的是发布时的那份（清单、前端源码、后端代码），不带编译结果 */
export function formTransfer(flow: FlowClient): Transfer {
  return {
    exportJson: async (key) => {
      const doc = await flow.formDocument(key);
      const publication: FormPublication = {
        key: doc.key,
        name: doc.name,
        ...(doc.category ? { category: doc.category } : {}),
        manifest: JSON.parse(doc.manifest),
        ...(doc.clientSource ? { clientSource: doc.clientSource } : {}),
        ...(doc.serverSource ? { serverLanguage: 'java' as const, serverSource: doc.serverSource } : {}),
      };
      return JSON.stringify(publication, null, 2);
    },
    publish: async (json, rename, note) => {
      const { clientCompiled: _ignored, compilerVersion: _alsoIgnored, ...publication } = JSON.parse(json) as FormPublication;
      const fromSource = publication.clientSource ? await fromClientSource(publication.clientSource) : {};
      return flow.publishForm({ ...publication, ...fromSource, ...rename, ...(note ? { note } : {}) });
    },
  };
}

/** 按前端源码重新提取的清单与编译结果；清单写得不对时抛出，说明是哪一处 */
async function fromClientSource(source: string): Promise<Pick<FormPublication, 'manifest' | 'clientCompiled' | 'compilerVersion'>> {
  const { compileFormSource, extractFields } = await import('../../form-tools');
  const extracted = extractFields(source);
  if (!extracted.manifest) {
    const first = extracted.problems[0]!;
    throw new Error(`${first.message} (${first.line}:${first.column})`);
  }
  return { manifest: extracted.manifest as FormManifest, ...(await compileFormSource(source)) };
}

/** 存成 JSON 文件 */
export const downloadJson = (name: string, json: string) => downloadFile(name, json, 'application/json');
