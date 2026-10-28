/**
 * 站点导航：按 pages/<分组>/<页面>.tsx 收集，分组的顺序与名字在 SECTIONS，组里按页面 meta 的 order 排。
 * 侧栏、搜索、上一篇 / 下一篇、演示旁的文档面板都从这里取，顺序一致。地址是 /docs/<分组>/<文件名转成短横线>（Designer.tsx → /docs/forms/designer）。
 */
import type { Component } from 'vue';

/** 每页导出的 meta */
export interface PageMeta {
  /** 标题：侧栏、页面标题、搜索都用它 */
  title: string;
  /** 一句话简介：页面开头、搜索结果显示 */
  description: string;
  /** 组里的顺序，越小越靠前 */
  order: number;
  /** 照着这页在演示里操作时从哪一页开始（/app/...）；写了时页面开头有"边看边做"，打开那一页、旁边是这篇文档 */
  try?: string;
}

/** 一个页面 */
export interface NavPage extends PageMeta {
  path: string;
  section: string;
  component: () => Promise<{ default: Component }>;
}

/** 分组：目录名 → 侧栏里的组名，也是侧栏里的顺序 */
const SECTIONS: [string, string][] = [
  ['start', '开始'],
  ['use', '日常使用'],
  ['build', '搭建流程'],
  ['forms', '表单'],
  ['admin', '管理'],
  ['dev', '开发'],
];

const metas = import.meta.glob<PageMeta>('../pages/*/*.tsx', { import: 'meta', eager: true });
const components = import.meta.glob<{ default: Component }>('../pages/*/*.tsx');

const kebab = (name: string) => name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();

/** 全部页面，按侧栏的顺序 */
export const allPages: NavPage[] = SECTIONS.flatMap(([dir]) =>
  Object.entries(metas)
    .filter(([file]) => file.startsWith(`../pages/${dir}/`))
    .map(([file, meta]) => ({
      ...meta,
      section: dir,
      path: `/docs/${dir}/${kebab(file.slice(`../pages/${dir}/`.length, -'.tsx'.length))}`,
      component: components[file]!,
    }))
    .sort((a, b) => a.order - b.order),
);

/** 侧栏的分组 */
export const sections = SECTIONS.map(([dir, label]) => ({ label, pages: allPages.filter((page) => page.section === dir) })).filter(
  (section) => section.pages.length > 0,
);

/** 页面所在分组的名字（搜索面板分组用） */
export const sectionLabel = (page: NavPage) => SECTIONS.find(([dir]) => dir === page.section)?.[1] ?? '';

/** 按地址找页面 */
export const pageAt = (path: string) => allPages.find((page) => page.path === path);
