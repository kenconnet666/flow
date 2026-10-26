/**
 * 示例的"登录"：在顶栏切换当前用户，请求头 X-User 带上他的编号（示例后端 ExampleActors 按它认人，只为演示）。
 * 用户、角色、部门同 examples/spring-boot 的 ExampleOrgDirectory 与 spec/scenarios 的约定。
 */
import { createToaster, localPeopleSource, type PeopleNode } from '@zerodep-dev/vue-tsx-pro-ui';
import { ref, watch } from 'vue';

/** 示例里的人 */
export const users = [
  { id: 1001, name: '林小满', description: '门店 · 发起采购' },
  { id: 1002, name: '周启航', description: '财务部 · 财务复核' },
  { id: 1003, name: '沈若水', description: '财务部 · 财务复核' },
  { id: 1004, name: '总经理', description: '总经办 · 大额审批' },
  { id: 1005, name: '许知远', description: '总经办 · 助理（转办、加签的对象）' },
] as const;

const KEY = 'flow-demo:user';

/** 当前用户的编号；记在浏览器里，刷新后还是他 */
export const currentUser = ref<number>(readUser());

function readUser(): number {
  try {
    const saved = Number(localStorage.getItem(KEY));
    return users.some((user) => user.id === saved) ? saved : users[0].id;
  } catch {
    return users[0].id;
  }
}

watch(currentUser, (id) => {
  try {
    localStorage.setItem(KEY, String(id));
  } catch {
    // 隐私模式下存不了：只是刷新后回到第一个人
  }
});

/** 组织架构（人员字段的选择器用）：三个部门，各自的人；两个角色 */
const org: PeopleNode[] = [
  { id: 10, type: 'department', name: '门店', children: [{ id: 1001, type: 'user', name: '林小满' }] },
  {
    id: 20,
    type: 'department',
    name: '财务部',
    children: [
      { id: 1002, type: 'user', name: '周启航' },
      { id: 1003, type: 'user', name: '沈若水' },
    ],
  },
  {
    id: 30,
    type: 'department',
    name: '总经办',
    children: [
      { id: 1004, type: 'user', name: '总经理' },
      { id: 1005, type: 'user', name: '许知远' },
    ],
  },
];

/** 人员字段的数据来源 */
export const people = localPeopleSource(org, [
  { id: 1, type: 'role', name: '财务' },
  { id: 2, type: 'role', name: '总经理' },
]);

/** 应用的通知（页面里的 <Toaster controller={toaster} /> 显示它们） */
export const toaster = createToaster();
