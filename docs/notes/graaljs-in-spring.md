# 思路记录：Spring 里嵌 GraalJS，引擎核心只写一份 TS

- 状态：**只作记录，不采用**。用户 2026-10-26 定：路线不变，仍是 Java（Spring Boot）与 Nuxt 两个后端，表单的后端代码 Spring Boot 用 Java、Nuxt 用 TS（[0004](../decisions/0004-forms.md) 1.5）；
  GraalJS 只作为一种思路留档，以后要重新评估时从这里接着做
- 相关：[0001](../decisions/0001-engine-direction.md) 的交付形态、[0003](../decisions/0003-model-and-frontend.md) 的模型包

## 思路

引擎核心用 TS 写一份：Nuxt 直接跑；Spring Boot 里嵌一个 JS 运行时（GraalJS）跑同一份代码。好处是引擎、模型包、表单的后端代码都只有一种语言、一份实现，不用两边对齐的场景用例；
Spring 里仍在进程内，数据库调用桥接到 Spring 的数据源，能和业务表在同一个事务里。

## 设计要点（想过、没做完）

| 项 | 做法 |
| --- | --- |
| 数据库与事务 | query TS 的驱动接口只有 `query(sql, 参数)` 与 `transaction(work)` 两个方法，换成一个 Java 实现的 JDBC 桥：连接取 `DataSourceUtils.getConnection`（在 Spring 事务里就是同一个连接），`transaction` 走 `PlatformTransactionManager`（已有事务时加入）。返回值照 mysql2 驱动的约定：DECIMAL、BIGINT、日期时间读成文字 |
| 同步与 Promise | 桥的方法是同步的，JS 一侧包成 Promise；Java 调 JS 拿到 Promise 后用 `then` 取结果。GraalJS 在每次从 Java 进入的调用返回前跑完排队的 Promise 任务——这一点没来得及实测 |
| 打包 | query TS 的核心包不依赖 Node 专有的接口（`AsyncLocalStorage` 是可选的）；MySQL 方言包只有 mysql2 驱动那个文件引用 `node:buffer`，打包时换成空壳即可。用 esbuild 打成一个 IIFE 文件，约 400 KB（未压缩） |
| 并发 | 一个 JS 上下文同一时间只能给一个线程用：Spring 里维护上下文池，共用一个 `Engine` 让各上下文复用编译过的代码（GraalJS 文档推荐的做法） |
| 引擎核心的约束 | 不能用 Node 专有的接口：数据库、时钟、编号、日志、定时调度都由宿主注入 |

## 查到的事实

- GraalJS 嵌入用 Maven 的 `org.graalvm.polyglot:polyglot` 加 `js-community`（pom 类型）。现在的文档里运行时表最老列到 JDK 21；JDK 17 只能用较老的 23.1.x 一线（未实测）。
- 在普通 OpenJDK 上只能解释执行（会打印警告，可以关掉）；要运行时编译得用 GraalVM 的 JDK，或 Oracle 的"多语言隔离"。
- JS 上下文能否跨线程共享取决于语言，JS 不行；`Engine` 可以跨线程共享，代码缓存只在同一个 `Engine` 的上下文之间共享。

## 实测到哪一步

只做了 JS 一侧与 Node 的对照，Spring 与 GraalJS 一侧没有做（用户叫停）。本机 WSL 里的 MySQL 8.4，一次命令是发起、提交、查待办三步共 10 条语句：

| 场景 | 结果 |
| --- | --- |
| 只渲染不连库（构造语句、渲染、编解码） | 每次命令约 0.43 ms |
| Node + mysql2 单线程 | 平均 27 ms，p99 40 ms（基本是 10 次往返的网络耗时） |
| 并发 4 / 16 | 吞吐 66 / 23 次每秒，16 并发时 p99 1.25 s |

16 并发时吞吐反而下降，多半是表上没建索引（按 `INSTANCE_ID` 计数、查待办都是全表扫描）加上锁等待，没有继续查。**设计引擎的表时要记得这一点**：推进与查待办用到的列都要有索引。

## 要重新评估时还缺什么

1. 桥进 Spring 事务：Java 写的行与 JS 写的行同进同退；JS 能读到同一事务里 Java 没提交的行；JS 自己开的事务能回滚；异常两个方向都能穿过去。
2. 性能：同一负载在 GraalJS（JDK 17 / 25 的解释执行，以及 GraalVM JDK）、Java 直接走 JDBC、Node 三者之间对比。
3. 并发：上下文池在 1 / 4 / 16 线程下的吞吐与延迟。
4. 体积与启动：fat jar 增加多少、建 `Engine` 与第一个上下文要多久。
