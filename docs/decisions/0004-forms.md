# 0004 表单：代码表单加拖拽设计器

- 状态：讨论中，逐节定稿。形态已定（用户 2026-10-26）：表单写成 TSX 代码，注册后运行时编译；拖拽设计器生成代码，代码也可以自由修改。第 1 节已定（2026-10-26），其余各节待讨论
- 相关：0001 的"灵活表单"一行（本文接替它）；0002 的校验（表单是否已注册）与第 12 节；前端方案 [frontend.md](../frontend.md)

## 背景

审批节点要展示与填写业务表单，不同节点对同一张表单的字段有不同权限（只读、隐藏、可改、必填）。表单的作者有两类：
开发人员要写联动、远程数据、自定义校验；业务管理员要能自己拖出一张简单表单。两类人改的是同一张表单，不能各存一份。

可选的做法：

| 做法 | 问题 |
| --- | --- |
| JSON 配置表单 + 设计器 | 复杂联动、远程数据要靠配置项一项项加，最后变成用 JSON 写程序 |
| 纯代码表单 | 业务管理员改不了；引擎读不出有哪些字段 |
| 设计器只生成一次代码，之后手改（单向） | 手改过的表单再也回不到设计器 |
| **代码是唯一的来源，设计器读写同一份代码（双向）** | 设计器只能理解代码的一个子集，子集以外的部分要原样保留——下文的做法就是为此 |

## 已定

| 项 | 决定 |
| --- | --- |
| 来源 | 表单只存一份 TSX 源码。设计器打开时解析源码，拖拽、改属性都是改源码；手改代码后设计器重新解析显示 |
| 编译 | 保存时在浏览器的 Worker 里编译（`@zerodep-dev/vue-tsx-pro-tsx/compiler` 的 `compile`，同文档站演练场），源码与编译结果一起存。运行时只执行编译结果，审批页面不带编译器 |
| 依赖 | 表单代码只能导入登记过的模块：vue、vue-tsx-pro 的入口、接入方注册的业务组件与工具（同演练场的模块表 `run.ts`）。导入了没登记的模块，保存时报问题 |
| 后端代码 | 同前端一样在线编辑、存数据库、后端启动时加载；Spring Boot 的用 Java 写，Nuxt 的用 TS 写（用户 2026-10-26，细节见 1.5） |
| 分工 | 设计器里点得出来的只覆盖常见情况；复杂的自己改代码，前端改表单源码，后端写表单的后端代码（1.5）。设计器不追求把复杂写法也做成按钮（用户 2026-10-26） |

## 方案

### 1. 表单文件的约定（已定，用户 2026-10-26）

一张表单由三部分组成：TSX 模块里的字段清单 `fields` 与默认导出的表单组件（下面的例子），加上可选的后端代码（1.5）。
设计器只改前两样里的常见写法；后端代码只在代码里写。

```tsx
import { defineFields, defineFlowForm } from '@zerodep-dev/flow-ui/form';
import { DecimalField, FormSection, PeoplePicker, TextField } from '@zerodep-dev/vue-tsx-pro-ui';

/** 字段清单：只能是字面量（见 1.2） */
export const fields = defineFields({
  title: { label: '标题', type: 'text', required: true, maxLength: 50 },
  amount: { label: '金额', type: 'decimal', scale: 2, min: '0' },
  buyer: { label: '经办人', type: 'people', types: ['user'], max: 1 },
  lines: {
    label: '采购明细',
    type: 'list',
    minItems: 1,
    fields: {
      item: { label: '物品', type: 'text', required: true },
      quantity: { label: '数量', type: 'number', integer: true, min: 1 },
    },
  },
});

export default defineFlowForm(fields, ({ form, context }) => {
  // 这里可以写任意逻辑：远程数据、计算、监听；设计器不碰
  return () => (
    <form.Form>
      <FormSection title="基本信息">
        <form.Field name="title"><TextField /></form.Field>
        <form.Field name="amount"><DecimalField /></form.Field>
        <form.Field name="buyer"><PeoplePicker source={context.people} /></form.Field>
      </FormSection>
    </form.Form>
  );
});
```

（组件名只是示意；明细表的写法随设计器的组件清单一起定。）

#### 1.1 三部分各管什么

| 部分 | 管什么 | 谁读 | 设计器里 |
| --- | --- | --- | --- |
| `fields` | 数据的形状：有哪些字段、类型、必填与范围。**不管长什么样、怎么联动** | 引擎（提交时校验值与权限、值写进流程变量）、流程设计器（节点字段权限、连线条件的字段列表）、表单设计器（字段列表）、`defineFlowForm`（生成 zod schema） | 全部能点 |
| 默认导出 | 长什么样、怎么交互：布局、控件、联动、远程数据、前端的跨字段校验 | 表单容器（审批页面里渲染） | 常见的能点（第 2 节），其余改代码 |
| 后端代码（可选） | 带出初始值、后端校验、后端算的字段、同事务写业务表、给前端调的接口 | 引擎（发起、暂存、提交时调用） | "后端代码"页签里写代码，没有可点的 |

- `defineFields` 只是给类型推断用的恒等函数（参数类型用 `const` 泛型），`form.Field` 的 `name` 因此能补全、写错报错，值的类型也从清单推出。
- `defineFlowForm(fields, setup)`：按清单生成 zod schema 交给 `useForm`，标签、必填标记、长度与范围都从清单来，`form.Field` 不用再写 `label`；
  `setup` 拿到 `form`（`useForm` 的控制器）与 `context`（容器给的：实例与节点信息、人员数据源、当前模式是填写还是查看），返回渲染函数。
  跨字段校验、单字段异步校验照常写在 `defineFlowForm` 的第三个参数里（`validate`、`dependencies`，同 `useForm`）。
- 节点上的字段权限由容器经 `context` 交给 `form.Field`，表单代码里不写节点判断（第 4 节）。

#### 1.2 字面量的限制

`defineFields(...)` 的参数必须是 **JSON5 值**：对象、数组、字符串、数字、`true` / `false` / `null`，可以有注释、尾逗号、不加引号的键。
不能出现变量、函数调用、展开（`...`）、计算的键、模板字符串、正则、`undefined`。不满足时保存报问题，指出位置。

为什么非要字面量：

1. **设计器要能改它**。拖进一个控件就是往这个对象里加一项、改属性就是改一个值；求值得到的结果写不回源码。这是决定性的理由。
2. **不执行代码就能读**。保存时在浏览器里静态提取成字段清单 JSON 存下；TS 后端要核对时也只需解析、不用执行。
3. **规则一句话说得清**。"JSON5 值"是现成的、开发人员熟悉的定义，不用另写一份"哪些写法可以"的清单。

限制挡住的写法，各有去处：

| 想写的 | 去处 |
| --- | --- |
| 几个字段共用一份选项 | `options: { source: '供应商等级' }`：选项来自接入方登记的字典（前端加载、后端校验值在不在字典里）；或者各写一份 |
| 选项随别的字段变（省 → 市） | 清单里写字典 `source`，过滤写在渲染代码里；后端只校验值在字典里 |
| 条件必填、跨字段规则 | 前端写在 `validate` 里（即时提示）；后端要拦的写在后端代码的 `validate` 里（1.5）。后端不重跑前端代码 |
| 算出来的字段（合计） | 清单里声明这个字段，前端算出来给人看；后端代码的 `compute` 重算并覆盖，不信前端交上来的数 |
| 多语言的标签 | 暂时只有一种语言；以后要时 `label` 改成语言包的键（待定） |

**两边不一致的兜底**：运行时表单加载后，把模块真正导出的 `fields` 和存下的清单比一遍，不一致就不渲染并报问题（正常保存不会出现，防的是绕过保存流程或提取有 bug）。

#### 1.3 字段类型

类型名与条件构造器的 `ConditionFieldType` 对齐，能参与条件的直接对上；值都是 JSON，后端两种语言读法一致。

| 类型 | 值（JSON） | 专有属性 | 条件里当作 |
| --- | --- | --- | --- |
| `text` | 字符串 | `minLength`、`maxLength`、`pattern`（正则写成字符串） | `text` |
| `number` | 数字 | `min`、`max`、`integer` | `number` |
| `decimal` | **字符串**（`"19.90"`） | `scale`（小数位）、`min`、`max`（也写成字符串） | `number` |
| `boolean` | 布尔 | — | `boolean` |
| `date` | `"2026-10-26"` | `min`、`max` | `date` |
| `datetime` | 带 `Z` 的 ISO 8601（同 0001 的时间约定） | `min`、`max` | `date` |
| `select` | 选项的值；`multiple` 时是数组 | `options`（字面量列表或 `{ source }`）、`multiple` | `select` |
| `people` | `PeopleEntry` 的数组（同组件库的 `PeoplePicker`：带名字，回显不用再查） | `types`（user / department / position）、`max` | `select`（只比较 id）；可作为处理人来源（0002 §3"流程变量里的人"） |
| `file` | `{ id, name, size }` 的数组，文件本身由接入方存 | `accept`、`maxCount`、`maxSize` | 不参与 |
| `list` | 对象的数组（明细行） | `fields`（一层，里面不能再有 `list`）、`minItems`、`maxItems` | 不参与（以后要时加"行数""某列合计"） |

- 所有字段都有：`label`（必填）、`description`、`required`、`default`（同样是字面量）。
- `decimal` 用字符串：金额在 JS 里用浮点会丢精度，Java 用 `BigDecimal` 读；组件库已有按文字读数字的 `isNumericText`。
- 字段名：`^[a-z][A-Za-z0-9]*$`，最长 64，同一层里唯一。字段名会成为流程变量的键与条件里的 `field`，所以发布后改名等于删一个、加一个，校验会指出引用了旧名字的流程。

#### 1.4 提取放在哪

静态提取要解析 TSX（Babel），而模型包是零依赖的，所以分两处：

- **模型包**（TS 与 Java 各一份）：字段清单的类型、清单本身的校验（类型、属性、字段名）、按清单校验提交的值与权限、问题代号。
- **前端的表单工具**（本仓库的前端包，用组件库已有的 Babel 编译器）：从源码里提取清单、写回清单、编译。TS 后端要核对源码与清单时可以复用它，Java 后端只读存下的清单 JSON。

#### 1.5 表单的后端代码

和前端代码一样**在线编辑、存在数据库里**，后端启动时加载（用户 2026-10-26 定）。后端只支持 Spring Boot 与 Nuxt，
**各用自己的语言**：Spring Boot 的用 Java 写，Nuxt 的用 TS 写。一个部署只有一种后端，所以一张表单的后端代码只有一份，语言跟着部署走。

一张表单的后端代码是一个处理器，引擎在固定的时机调它；每个方法都可选，没有后端代码时只做清单级的校验。

```java
// Spring Boot：一个 public 类，不写 package（引擎给每个版本单独的类加载器，类名不会冲突）
import org.dpdns.zerodep.flow.form.*;
import org.springframework.beans.factory.annotation.Autowired;

public class PurchaseForm implements FormHandler {
    @Autowired BudgetService budgets;   // 接入方自己的 Spring Bean，照常注入

    @Override
    public FieldErrors validate(FormValues values, FormContext ctx) {
        return budgets.remaining(ctx.department()).compareTo(values.decimal("amount")) < 0
            ? FieldErrors.of("amount", "超出预算")
            : FieldErrors.none();
    }
    // load、compute、submit、actions 都是默认方法，按需覆盖
}
```

```ts
// Nuxt：默认导出一个处理器；import 只能取登记过的模块（同前端的模块表）
import { defineFormHandler } from '@zerodep-dev/flow/form';
import { budgets } from '#flow-modules/budget'; // 接入方登记的服务

export default defineFormHandler({
  load: async (ctx) => ({ buyer: [await ctx.currentUserEntry()] }),              // 发起时带出初始值
  validate: async (values, ctx) => ((await budgets.remaining(ctx.department)) < values.amount ? { amount: '超出预算' } : {}),
  compute: (values) => ({ total: sum(values.lines) }),                            // 后端算的字段，覆盖前端交上来的
  submit: async (values, ctx) => { await ctx.db.insert(/* ... */) },              // 与流转同一个事务，失败整个回滚
  actions: { remainingBudget: async (args, ctx) => budgets.remaining(args.department) }, // 前端 context.call('remainingBudget', args)
});
```

| 项 | 做法 |
| --- | --- |
| 调用时机 | `load`：发起、打开草稿时；`validate` → `compute` → 清单与权限校验 → `submit`：提交时，在流转的事务里依次执行；暂存只做清单校验 |
| 给前端的接口 | `actions` 经引擎的接口暴露，调用前查看权限（能看这个实例的人才能调）；前端表单用 `context.call(名字, 参数)`，不用自己拼地址 |
| 编辑 | 表单设计器里多一个"后端代码"页签（同一个代码编辑器，Java / TS 语法）；"试编译"把源码交给后端编译，错误带行列号标在编辑器里。编译不过不能发布 |
| 加载 | 启动时把所有已发布版本的后端代码一次编译、加载；某一张编译失败只让这张表单不可用并记日志，应用照常启动（可配置成启动失败） |
| 刷新 | 本实例发布后立刻换上新版本。集群里通知其他实例由接入方自己接（Kafka、Redis Stream 或别的消息队列），引擎只给扩展口与示例（用户 2026-10-26），见下面"集群里的刷新" |
| 表单改版 | 后端代码跟着表单版本走（存在同一行版本里），进行中的实例用它发起时的版本，前后端代码总是配套的 |
| 类型 | 字段清单能生成 Java record 与 TS 类型给后端代码用（编辑器里也有补全）；不用也能经 `FormValues` 按名字取 |
| 导出 | 表单（前端源码、后端源码、清单）能导出成文件、再导入，用于版本管理与在开发环境里调试 |

两种后端怎样编译、执行：

| | Spring Boot（Java） | Nuxt（TS） |
| --- | --- | --- |
| 编译 | JDK 自带的 `javax.tools.JavaCompiler`，支持完整的 Java 语法（17）。classpath 取应用自己的类加载器（Spring Boot 的嵌套 jar 要自己实现 `JavaFileManager` 列出类，可参考 Liquor 的做法，待实测） | 去掉类型（`oxc-transform` 或 esbuild 一类，只转换、不做类型检查），import 换成从模块表取（同前端） |
| 执行 | 每个版本一个类加载器加载编译出的类，用 Spring 的 `AutowireCapableBeanFactory.createBean` 实例化，`@Autowired` 照常注入；换版本时丢掉旧的类加载器 | 在本进程里执行（同前端的 `new Function` + 模块表） |
| 运行环境要求 | **运行时要有 `jdk.compiler` 模块**：用 JDK 镜像，或用 jlink 打包时带上它；只有 JRE 的镜像（如 `eclipse-temurin:17-jre`）编译不了（用户 2026-10-26 接受）。启动时缺这个模块就报清楚的错，不等到编译时才失败 | **只能部署成 Node 服务**（Nitro 的 node-server 预设）；Cloudflare 一类边缘运行时禁止动态执行代码 |
| 能调什么 | 应用里所有的类与 Bean | 只有接入方登记进模块表的服务、工具，加上引擎给的 `ctx` |

集群里的刷新（引擎不带消息队列，只给两个口子）：

| 口子 | Spring Boot | Nuxt |
| --- | --- | --- |
| 发出：本实例发布了新版本 | 接入方实现 `FormChangeBroadcaster` 的 Bean，`published(formKey, version)` 里发消息；没有这个 Bean 时什么都不发 | 引擎选项 `onFormPublished(formKey, version)` |
| 收到：别的实例发布了 | 接入方的消费者调 `flowForms.reload(formKey, version)` | 同左，`engine.forms.reload(formKey, version)` |

- `reload` 是幂等的：已经是这个版本或更新的就什么都不做；消息重复、乱序都没关系。
- 消息丢了也不会一直错下去：下次重启会加载最新版本；管理端另有"重新加载"按钮（调同一个 `reload`）。
- 不接任何消息队列时，单实例部署照常工作；多实例时其他实例要等重启才换版本。
- 示例只放文档与示例项目，不进引擎的依赖：Spring Boot 用 Redis Stream 与 Kafka 各一个，Nuxt 用 Redis Stream 一个。
- 流程定义发布后如果各实例有缓存，也走这两个口子（`published` / `reload` 带上种类：表单或流程定义），不另做一套。

参考：Java 里把脚本存在数据库、运行时加载的规则引擎 LiteFlow，Java 脚本先用过 Janino（只支持部分 Java 语法，已不推荐），后改用基于 `javax.tools` 的 Liquor，文档写明必须在 JDK 环境下运行、不能用 JRE。

安全同前端代码（第 6 节）：不做沙箱，后端代码以应用的全部权限运行，只有"表单开发"权限的人能保存、发布，每次发布留痕。

### 2. 设计器能理解的子集（待讨论）

设计器只做常见的：拖控件、排布局、改字面量属性、改字段清单。认不出的写法不报错、不改写，当成代码块原样保留，复杂的回到代码里改。

| 代码 | 设计器里 |
| --- | --- |
| `fields` 里的每一项 | 字段列表；增删改直接改这个对象字面量 |
| JSX 里登记过的组件（布局：表单、分组、栅格、标签页；字段：`form.Field` 加输入控件；展示：文字、分割线） | 画布上可拖动、选中、改属性的块 |
| 属性是字面量（字符串、数字、布尔、简单 JSON） | 属性面板里按类型编辑 |
| 属性是表达式（`disabled={amount.value > 100}`） | 属性面板里显示为"代码"，只能改那一小段代码 |
| 没登记的元素、`{条件 && ...}`、`{list.map(...)}` | 一整块"代码块"：能移动、删除，内部只能在代码里改 |
| 渲染函数以外的代码、注释、空行 | 不显示，原样保留 |

### 3. 画布与代码的对应、写回（待讨论）

参考 Onlook 一类"以代码为来源"的可视化编辑器的做法（据公开介绍：编译时给 JSX 元素注入标识、映射回源码位置、只改受影响的那段代码）：

- **对应**：设计模式下编译时多加一个 Babel 插件，给每个 JSX 元素加 `data-form-node="起始偏移"`；画布上点中元素，按标识找到源码里的节点。
- **写回**：改动落到 AST 节点的源码区间上，只替换这一段文字（同 magic-string 的做法），文件其余部分一个字符不动，手写的格式与注释都在。
  新插入的片段用自己的小打印器生成（同组件演练场的 `props/code.ts`：属性多时一行一个）。
- **撤销重做**：快照是源码字符串（`createHistory`），设计与代码两边的改动进同一个撤销栈。
- **解析失败**（代码写到一半有语法错误）：设计视图显示上次能解析的结果并提示错误，修好前不能拖拽。

### 4. 节点上的字段权限（待讨论）

- 流程定义的人工节点上写 `form: { key, fields: { amount: 'readonly', supplier: 'hidden' } }`，键取自字段清单；没写的字段按表单默认。
- 运行时由引擎的表单容器把权限交给表单：容器 provide 每个字段的权限，`Field` 按名字取，隐藏、只读、必填都在 Field 里生效，表单代码里不用写节点判断。
  vue-tsx-pro 的 `Form` 现在只有整张表单的 `disabled` / `readonly`（`provideFieldLayout`），需要补"按字段名取权限"的扩展口，或者由本仓库的 `form.Field` 包一层。
- 后端提交时按同一份权限再校验一次：只读、隐藏的字段不接受修改。

### 5. 存储与版本（待讨论）

| 项 | 做法 |
| --- | --- |
| 表 | `flow_form`（标识、名称、分类）与 `flow_form_version`（版本号、前端源码、前端编译结果、字段清单 JSON、用到的模块、后端源码与语言、发布人、时间）；发布后不可变，同流程定义。后端只存源码，启动时编译（1.5） |
| 草稿 | 同流程定义的草稿：保存不发布，版本列防多人覆盖 |
| 引用 | 流程定义发布时把引用的表单版本固定下来；表单发新版本不影响已发布的流程，要用新表单就重新发布流程 |
| 表单的值 | 提交时写进流程变量（实例上的 JSON 列）；要写业务表的，写在后端代码的 `submit` 里（1.5），与流转同一个事务 |

### 6. 安全（待讨论）

前端代码在处理人的浏览器里以页面的全部权限运行，不做沙箱（iframe 沙箱要把组件、主题、弹层全部隔开，代价太大）；
后端代码以应用的全部权限运行（1.5），能读写数据库、调任何服务，风险比前端更大。所以：

- 只有"表单开发"权限的人能保存与发布代码；只用设计器、不碰代码的业务管理员也是在改代码，同样需要这个权限（权限判断是接入方的扩展点）。
- 每次发布留痕（谁、什么时间、源码差异）。
- 依赖只能是登记过的模块（见"已定"），表单代码拿不到登记以外的库。

### 7. 放在哪（待讨论）

| 部分 | 位置 |
| --- | --- |
| 编译（TSX → 可执行代码、import 换成模块表）、执行、编辑器 | 现在都在 vue-tsx-pro 的文档站里（`apps/docs/src/playground/`：`compile.ts`、`run.ts`、`editor.ts`），不是公开的包。建议挪进 vue-tsx-pro 的公开子路径（例如 `@zerodep-dev/vue-tsx-pro-tsx/runtime-compile` 与 `@zerodep-dev/vue-tsx-pro-ui/code-editor`），文档站改用它；会动组件库的公开 API，要先确认 |
| 字段清单的类型与校验、提交值与权限的校验、问题代号 | 模型包（TS 与 Java 各一份，见 1.4） |
| 清单的提取与写回、`defineFields` / `defineFlowForm`、表单容器、设计器 | 本仓库的前端包 |

## 待讨论

1. 设计器登记哪些组件（第 2 节），接入方怎样注册自己的业务组件（名字、属性说明、能不能当字段）。
2. 编译与编辑器挪进 vue-tsx-pro 的公开子路径（第 7 节）。
3. 常见的"字段写进业务表的哪一列"要不要做成设计器里能点的映射（不写 `submit` 也能落库）。
4. 顺序：建议放在流程设计器之后——表单的字段清单是模型包的一部分，先随定义 JSON 一起定；运行时（编译、加载、字段权限）先于设计器做，审批页面先能用代码表单。

## 待实测

- 1.5 的 Spring Boot：打成可执行的 fat jar 运行，在线的 Java 代码能 import 应用自己的类、注入 Bean（嵌套 jar 的 classpath 要自己实现 `JavaFileManager`）。
