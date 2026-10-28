/** 搭建流程的教程：设计器入门与六个教程，每个从新建流程画到发布，再以真实用户跑一单；图存 build/<教程>/ */
import { assignee, condition, handle, newProcess, node, open, openItem, publish, retarget, selectEdge, shot, USERS } from './lib.ts';

/** 设计器入门与教程 1：报销（条件分支）——财务审核，金额 ≥ 5000 再总经理审批 */
export async function expense() {
  const page = await open(USERS.boss, '/admin/processes');
  await shot(page, 'build/basics/01-process-list', { mark: page.getByRole('button', { name: '新建流程' }) });
  await page.getByRole('button', { name: '新建流程' }).click();
  await page.getByRole('button', { name: '发布', exact: true }).waitFor();
  await shot(page, 'build/basics/02-new', { wait: 800 });
  await shot(page, 'build/basics/03-palette', { clip: page.getByRole('group', { name: '添加节点' }), pad: 8 });
  await newProcess(page, 'expense', '报销', /采购申请单/, '报销：{form.title}');
  await shot(page, 'build/expense/01-process', { mark: [page.getByLabel('流程标识'), page.getByRole('combobox', { name: '表单' })] });
  await node(page, '审批').click();
  await page.getByLabel('名称', { exact: true }).fill('财务审核');
  await assignee(page, '财务', true);
  await shot(page, 'build/expense/02-finance', { mark: page.getByRole('group', { name: '处理人', exact: true }) });
  await page.getByRole('button', { name: '添加条件分支' }).click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('总经理审批');
  await assignee(page, '总经理', true);
  await shot(page, 'build/expense/03-branch');
  await shot(page, 'build/basics/04-problems', { clip: page.getByText(/问题（\d+）/).first().locator('xpath=../..'), pad: 6 });
  await selectEdge(page, '条件分支 → 总经理审批');
  await condition(page, '金额', '大于等于', '5000');
  await page.getByLabel('线上的文字').fill('≥ 5000');
  await shot(page, 'build/expense/04-condition', { mark: page.getByRole('group', { name: '条件', exact: true }) });
  await selectEdge(page, '条件分支 → 结束');
  await page.getByRole('switch', { name: /默认出边/ }).check();
  await page.getByLabel('线上的文字').fill('其余');
  await shot(page, 'build/expense/05-default', { mark: page.getByRole('switch', { name: /默认出边/ }) });
  await page.getByRole('tab', { name: 'JSON' }).click();
  await shot(page, 'build/basics/05-json', { wait: 800 });
  await page.getByRole('tab', { name: '设计' }).click();
  await publish(page, '首次发布');
  await shot(page, 'build/expense/06-published', { mark: page.getByRole('region', { name: '通知' }) });
  await page.context().close();

  // 跑一单：8000 元，财务（收件箱里认领）同意后到总经理
  const title = '去上海见客户';
  const start = await open(USERS.starter, '/start/expense');
  await start.getByLabel('标题').fill(title);
  await start.getByLabel('金额').fill('8000');
  await shot(start, 'build/expense/07-start', { mark: start.getByText(/提交后到/) });
  await start.getByRole('button', { name: '提交', exact: true }).click();
  await start.waitForURL('**/inbox');
  await start.context().close();
  const item = await openItem(USERS.financeA, title);
  await shot(item, 'build/expense/08-finance-item', { mark: item.getByText(/同意 →/) });
  await item.context().close();
  await handle(USERS.financeA, title);
  await handle(USERS.boss, title);
  const started = await open(USERS.starter, '/started');
  await shot(started, 'build/expense/09-done', { mark: started.getByRole('row', { name: new RegExp(title) }) });
  await started.context().close();
}

/** 教程 2：采购评审（并行两支、财务全体会签、汇合后总经理） */
export async function review() {
  const page = await open(USERS.boss, '/admin/processes/new');
  await newProcess(page, 'review', '采购评审', /采购申请单/, '采购评审：{form.title}');
  await node(page, '审批').click();
  await page.getByLabel('名称', { exact: true }).fill('技术评审');
  await assignee(page, '许知远');
  await node(page, '提交').click();
  await page.getByRole('button', { name: '添加并行' }).click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('财务评审');
  await assignee(page, '财务', true);
  await page.getByRole('combobox', { name: '会签' }).click();
  await page.getByRole('option', { name: '全部通过' }).click();
  await shot(page, 'build/review/01-countersign', { mark: page.getByRole('combobox', { name: '会签' }) });
  await node(page, '技术评审').click();
  await page.getByRole('button', { name: '添加并行' }).click();
  await page.getByLabel('名称', { exact: true }).fill('汇合');
  await shot(page, 'build/review/02-join');
  await retarget(page, '财务评审 → 结束：同意', '汇合');
  await shot(page, 'build/review/03-retarget', { mark: page.getByRole('combobox', { name: '连到', exact: true }) });
  await node(page, '汇合').click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('总经理审批');
  await assignee(page, '总经理');
  await publish(page, '首次发布');
  await shot(page, 'build/review/04-published');
  await page.context().close();

  const title = '服务器采购';
  const start = await open(USERS.starter, '/start/review');
  await start.getByLabel('标题').fill(title);
  await start.getByLabel('金额').fill('120000');
  await shot(start, 'build/review/05-start', { mark: start.getByText(/提交后到/) });
  await start.getByRole('button', { name: '提交', exact: true }).click();
  await start.waitForURL('**/inbox');
  await start.context().close();
  await handle(USERS.legal, title);
  await handle(USERS.financeA, title);
  const detail = await open(USERS.starter, '/started');
  await detail.getByRole('link', { name: new RegExp(title) }).click();
  await detail.waitForURL('**/instances/*');
  await detail.getByText('流转记录').waitFor();
  await shot(detail, 'build/review/06-midway', { wait: 1000 });
  await detail.context().close();
  await handle(USERS.financeB, title);
  await handle(USERS.boss, title);
}

/** 教程 3：合同审批（发起人起草自动跳过、法务可退回、上一步选经理、抄送财务） */
export async function contract() {
  const page = await open(USERS.boss, '/admin/processes/new');
  await newProcess(page, 'contract', '合同审批', /采购申请单/, '合同：{form.title}');
  await page.getByRole('checkbox', { name: /处理人就是发起人时/ }).check();
  await shot(page, 'build/contract/01-auto-skip', { mark: page.getByRole('checkbox', { name: /处理人就是发起人时/ }) });
  await node(page, '审批').click();
  await page.getByLabel('名称', { exact: true }).fill('起草');
  await page.getByRole('textbox', { name: '按钮上的字 1' }).fill('提交');
  await page.getByRole('button', { name: '删除结果 不同意' }).click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('法务审核');
  await assignee(page, '许知远');
  await page.getByRole('button', { name: '添加结果' }).click();
  await page.getByRole('textbox', { name: '按钮上的字 3' }).fill('退回修改');
  await page.getByRole('combobox', { name: '种类 3' }).click();
  await page.getByRole('option', { name: '退回' }).click();
  await page.getByRole('checkbox', { name: '处理时选退回到哪一步' }).check();
  await shot(page, 'build/contract/02-return', { mark: page.getByRole('group', { name: '处理结果' }) });
  await page.getByRole('button', { name: '添加抄送' }).click();
  const cc = page.getByRole('group', { name: '抄送给' });
  await cc.getByRole('button', { name: /选人/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: '角色' }).click();
  await page.getByRole('dialog').getByRole('checkbox', { name: '财务' }).check();
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await shot(page, 'build/contract/03-cc', { mark: cc });
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('经理审批');
  const who = page.getByRole('group', { name: '处理人', exact: true });
  await who.getByRole('checkbox', { name: '发起人' }).uncheck();
  await who.getByRole('checkbox', { name: /上一步的人选/ }).check();
  await who.getByRole('button', { name: /从这些人里选/ }).click();
  for (const person of ['总经理', '沈若水']) {
    await page.getByRole('dialog').getByRole('textbox', { name: /搜索/ }).fill(person);
    await page.waitForTimeout(400);
    await page.getByRole('dialog').getByRole('checkbox', { name: person }).first().check();
  }
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await who.getByRole('spinbutton', { name: /最多选几人/ }).fill('1');
  await shot(page, 'build/contract/04-pick', { mark: who });
  await publish(page, '首次发布');
  await shot(page, 'build/contract/05-published');
  await page.context().close();

  const title = '年度维保合同';
  const start = await open(USERS.starter, '/start/contract');
  await start.getByLabel('标题').fill(title);
  await start.getByLabel('金额').fill('50000');
  await start.getByRole('button', { name: '提交', exact: true }).click();
  await start.waitForURL('**/inbox');
  await start.context().close();
  let item = await openItem(USERS.legal, title);
  await item.getByLabel('意见', { exact: true }).fill('付款条款要改');
  await item.getByRole('button', { name: '退回修改', exact: true }).click();
  await shot(item, 'build/contract/06-return-dialog', { clip: item.getByRole('dialog').last() });
  await item.getByRole('dialog').last().getByRole('radio').first().check();
  await item.getByRole('dialog').last().getByRole('button', { name: '确定' }).click();
  await item.waitForURL('**/inbox');
  await item.context().close();
  await handle(USERS.starter, title, '提交');
  item = await openItem(USERS.legal, title);
  await item.getByRole('button', { name: '同意', exact: true }).click();
  const pick = item.getByRole('dialog', { name: '选择下一步的处理人' });
  await pick.getByRole('button', { name: '经理审批 的处理人' }).click();
  const picker = item.getByRole('dialog', { name: '选择人员' });
  await picker.getByRole('checkbox', { name: '沈若水' }).first().check();
  await picker.getByRole('button', { name: '确定' }).click();
  await picker.waitFor({ state: 'hidden' });
  await shot(item, 'build/contract/07-pick-dialog', { clip: pick });
  await pick.getByRole('button', { name: '确定' }).click();
  await item.waitForURL('**/inbox');
  await item.context().close();
  await handle(USERS.financeB, title);
  const detail = await open(USERS.starter, '/started');
  await detail.getByRole('link', { name: new RegExp(title) }).click();
  await detail.getByText('流转记录').waitFor();
  await detail.getByText('流转记录').scrollIntoViewIfNeeded();
  await shot(detail, 'build/contract/08-timeline', { wait: 1000 });
  await detail.context().close();
}

/** 教程 4：出差申请（包容分支：金额 ≥ 3000 财务、数量 ≥ 2 行政、其余直接汇合；汇合后等 1 分钟） */
export async function trip() {
  const page = await open(USERS.boss, '/admin/processes/new');
  await newProcess(page, 'trip', '出差申请', /采购申请单/, '出差：{form.title}');
  await node(page, '审批').click();
  await page.getByLabel('名称', { exact: true }).fill('部门审批');
  await assignee(page, '总经理');
  await page.getByRole('button', { name: '添加包容分支' }).click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('财务审核');
  await assignee(page, '周启航');
  await node(page, '包容分支').click();
  await page.getByRole('button', { name: '添加人工审批', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill('行政审批');
  await assignee(page, '许知远');
  await node(page, '财务审核').click();
  await page.getByRole('button', { name: '添加包容分支' }).click();
  await page.getByLabel('名称', { exact: true }).fill('汇合');
  await retarget(page, '行政审批 → 结束：同意', '汇合');
  await retarget(page, '包容分支 → 结束', '汇合');
  await page.getByRole('switch', { name: /默认出边/ }).check();
  await page.getByLabel('线上的文字').fill('其余');
  await selectEdge(page, '包容分支 → 财务审核');
  await condition(page, '金额', '大于等于', '3000');
  await selectEdge(page, '包容分支 → 行政审批');
  await condition(page, '数量', '大于等于', '2');
  await shot(page, 'build/trip/01-conditions', { mark: page.getByRole('group', { name: '条件', exact: true }) });
  await node(page, '汇合').click();
  await page.getByRole('button', { name: '添加定时' }).click();
  await page.getByRole('spinbutton').first().fill('1');
  await page.getByRole('combobox', { name: /等多久/ }).first().click();
  await page.getByRole('option', { name: '分钟' }).click();
  await shot(page, 'build/trip/02-timer');
  await publish(page, '首次发布');
  await shot(page, 'build/trip/03-published');
  await page.context().close();
}

/** 教程 5：大额采购（同步调用合同审批，同名字段带过去） */
export async function bigbuy() {
  const page = await open(USERS.boss, '/admin/processes/new');
  await newProcess(page, 'bigbuy', '大额采购', /采购申请单/, '大额采购：{form.title}');
  await node(page, '审批').click();
  await page.getByLabel('名称', { exact: true }).fill('总经理确认');
  await assignee(page, '总经理');
  await node(page, '提交').click();
  await page.getByRole('button', { name: '添加子流程' }).click();
  await page.getByRole('combobox', { name: '调用的流程' }).click();
  await page.getByRole('option', { name: /合同审批/ }).click();
  await page.getByRole('button', { name: /同名的 \d+ 个字段都带上/ }).click();
  await shot(page, 'build/bigbuy/01-call', { mark: page.getByRole('group', { name: '传给子流程的值' }) });
  await publish(page, '首次发布');
  await page.context().close();

  const title = '生产线改造';
  const start = await open(USERS.starter, '/start/bigbuy');
  await start.getByLabel('标题').fill(title);
  await start.getByLabel('金额').fill('900000');
  await start.getByRole('button', { name: '提交', exact: true }).click();
  await start.waitForURL('**/inbox');
  await start.goto(`${start.url().replace(/\/inbox.*/, '')}/started`);
  await start.getByRole('link', { name: `大额采购：${title}` }).click();
  await start.getByText('流转记录').waitFor();
  await shot(start, 'build/bigbuy/02-parent', { wait: 1000 });
  await start.goto(start.url().replace(/\/instances\/.*/, '/started'));
  await start.getByRole('link', { name: `合同：${title}` }).click();
  await start.getByText('上级流程').waitFor();
  await shot(start, 'build/bigbuy/03-child', { mark: start.getByText('上级流程') });
  await start.context().close();
}

/** 教程 6：外部签约（自动处理推送、等外部系统回调） */
export async function esign() {
  const page = await open(USERS.boss, '/admin/processes/new');
  await newProcess(page, 'esign', '外部签约', /采购申请单/, '签约：{form.title}');
  await node(page, '审批').click();
  await page.getByRole('button', { name: '删除', exact: true }).click();
  await node(page, '提交').click();
  await page.getByRole('button', { name: '添加自动处理' }).click();
  await page.getByRole('combobox', { name: '动作' }).click();
  await page.getByRole('option').first().click();
  await shot(page, 'build/esign/01-service', { mark: page.getByRole('combobox', { name: '动作' }) });
  await page.getByRole('button', { name: '添加等待事件' }).click();
  await page.getByLabel('名称', { exact: true }).fill('等签署回执');
  await page.getByRole('textbox', { name: '事件名' }).fill('esign.signed');
  await shot(page, 'build/esign/02-receive', { mark: page.getByRole('textbox', { name: '事件名' }) });
  await publish(page, '首次发布');
  await page.context().close();

  const title = '供应商框架协议';
  const start = await open(USERS.starter, '/start/esign');
  await start.getByLabel('标题').fill(title);
  await start.getByLabel('金额').fill('100');
  const started = start.waitForResponse((r) => r.url().includes('/commands/start'));
  await start.getByRole('button', { name: '提交', exact: true }).click();
  const id = ((await (await started).json()) as { data: { instance: { id: string } } }).data.instance.id;
  await start.waitForTimeout(3000);
  await start.goto(`${start.url().replace(/\/inbox.*/, '')}/instances/${id}`);
  await start.getByText('流转记录').waitFor();
  await shot(start, 'build/esign/03-waiting', { wait: 800 });
  await start.context().close();
}
