/**
 * 示例的外壳：左侧是"发起""我的待办""我已办""我发起的""抄送我的""流程监控"，顶栏切换当前用户（换人之后页面重新加载数据）。
 */
import { Activity, CircleCheck, FileText, Inbox, Mail, Send, Workflow } from '@lucide/icons';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { NavItem, NavList, Select, Toaster } from '@zerodep-dev/vue-tsx-pro-ui';
import { AppShell, AppShellBrand } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { RouterView } from 'vue-router';
import { currentUser, toaster, users } from './session';

export const App = _component(() => () => (
  <AppShell
    brand={
      <AppShellBrand href="/" icon={Workflow}>
        流程引擎示例
      </AppShellBrand>
    }
    header={
      <Select
        aria-label="当前用户"
        items={users.map((user) => ({ value: String(user.id), label: user.name, description: user.description }))}
        _value={String(currentUser.value)}
        onUpdate:_value={(id: string | null) => id && (currentUser.value = Number(id))}
      />
    }
    sidebar={
      <NavList aria-label="导航">
        <NavItem href="/" icon={Send}>
          发起
        </NavItem>
        <NavItem href="/inbox" icon={Inbox}>
          我的待办
        </NavItem>
        <NavItem href="/done" icon={CircleCheck}>
          我已办
        </NavItem>
        <NavItem href="/started" icon={FileText}>
          我发起的
        </NavItem>
        <NavItem href="/cc" icon={Mail}>
          抄送我的
        </NavItem>
        <NavItem href="/admin" icon={Activity}>
          流程监控
        </NavItem>
      </NavList>
    }
  >
    {/* 换人之后各页重新挂载、重新请求 */}
    <RouterView key={currentUser.value} />
    <Toaster controller={toaster} />
  </AppShell>
));
