/** 文档站的路由：首页是"介绍"，其余来自 site/nav（按页面文件收集），找不到的回首页 */
import { createRouter, createWebHistory } from 'vue-router';
import { allPages } from './site/nav';

export function createDocsRouter() {
  return createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
      { path: '/', redirect: allPages[0]!.path },
      ...allPages.map((page) => ({ path: page.path, component: page.component, meta: { title: page.title } })),
      { path: '/:missing(.*)*', redirect: '/' },
    ],
    // 换页回到顶部；带锚点时滚到那一节
    scrollBehavior: (to, _from, saved) => saved ?? (to.hash ? { el: decodeURIComponent(to.hash), top: 72 } : { top: 0 }),
  });
}
