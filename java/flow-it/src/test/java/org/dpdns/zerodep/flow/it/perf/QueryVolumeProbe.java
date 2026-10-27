package org.dpdns.zerodep.flow.it.perf;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.query.InstanceQueries.InstanceSearch;
import org.dpdns.zerodep.flow.core.engine.query.ListFilter;
import org.dpdns.zerodep.flow.core.engine.query.SortOrder;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 数据量下的查询耗时（阶段三）：先经引擎造一批真实的数据（发起、认领、处理、抄送、收件箱），再把每张表按编号偏移复制若干份
 * （每份的人也换一批：编号加一个大数），得到几万个实例、几十万条流转记录；然后对原来那批人跑各个列表与详情，记下耗时。
 * 只在 {@code -Dflow.perf=true} 时跑（几分钟）；结果打在标准输出，按耗时看要不要调索引（0007 第 4 节）。
 *
 * <pre>
 *   . infra/mysql/live-env.sh && ./mvnw -o test -pl java/flow-it -Dtest=QueryVolumeProbe -Dflow.perf=true
 * </pre>
 */
@DisplayName("数据量下的查询耗时")
@EnabledIfSystemProperty(named = "flow.perf", matches = "true")
class QueryVolumeProbe {
    /** 经引擎造的实例数 */
    private static final int BASE = 1000;
    /** 复制几份（含原来的）：实例数 = BASE × COPIES */
    private static final int COPIES = Integer.getInteger("flow.perf.copies", 50);
    /** 每份的编号偏移、人的编号偏移 */
    private static final long ID_OFFSET = 10_000_000L;
    private static final long USER_OFFSET = 100_000L;

    private static final List<TestUser> STARTERS = new ArrayList<>();
    private static final TestUser FINANCE = TestUser.of(2001L, "财务甲", 1L);
    private static final TestUser BOSS = TestUser.of(2002L, "总经理");

    static {
        for (long i = 0; i < 20; i++) STARTERS.add(TestUser.of(1000L + i, "员工" + i));
    }

    private static final String PROCESS = """
        { "key": "buy", "name": "采购", "title": "采购",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "review", "type": "task", "name": "财务复核", "assignees": [{ "role": 1 }], "outcomes": [{ "key": "ok", "label": "通过" }] },
                     { "id": "approve", "type": "task", "name": "总经理审批", "assignees": [{ "user": 2002 }], "due": { "hours": 8 },
                       "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "review" }, { "id": "e2", "from": "review", "to": "approve", "outcome": "ok" },
                     { "id": "e3", "from": "approve", "to": "end", "outcome": "ok" } ] }
        """;

    /** 每张表里存编号的列（复制时加编号偏移）与存人的列（加人的偏移） */
    private static final Set<String> ID_COLUMNS = Set.of("id", "instance_id", "parent_instance_id", "root_instance_id", "work_item_id", "parent_item_id");
    private static final Set<String> USER_COLUMNS = Set.of("started_by", "assignee_id", "completed_by", "from_user_id", "recipient_id", "actor_id",
        "designator_id", "target_id");
    private static final List<String> TABLES = List.of("flow_instance", "flow_form_data", "flow_work_item", "flow_candidate", "flow_event", "flow_cc",
        "flow_job", "flow_wait", "flow_step");

    @Test
    void 各个列表与详情的耗时() {
        LiveMysql.recreateTables();
        List<TestUser> everyone = new ArrayList<>(STARTERS);
        everyone.add(FINANCE);
        everyone.add(BOSS);
        EasyEntityQuery db = LiveMysql.easyQuery();
        FlowEngine<TestUser> engine = FlowEngine.<TestUser>builder(db).orgDirectory(new TestOrg(everyone)).build();
        engine.publishDefinition(BOSS, PROCESS, null);

        long seeding = System.nanoTime();
        Long sampleInstance = null;
        for (int n = 0; n < BASE; n++) {
            TestUser starter = STARTERS.get(n % STARTERS.size());
            var started = engine.start(starter, StartCommand.of("buy", Map.of()));
            sampleInstance = started.getInstance().getId();
            if (n % 10 < 7) {   // 七成走完财务复核
                Long review = started.getCreated().get(0).getId();
                engine.claim(FINANCE, new ClaimCommand(review));
                var reviewed = engine.complete(FINANCE, CompleteCommand.of(review, "ok"));
                if (n % 10 < 5) engine.complete(BOSS, CompleteCommand.of(reviewed.getCreated().get(0).getId(), "ok"));   // 五成走完
            }
            if (n % 4 == 0) engine.cc(BOSS, new CcCommand(started.getInstance().getId(), List.of(starter.getId()), "知会"));
        }
        System.out.printf("seeded %d instances in %d ms%n", BASE, (System.nanoTime() - seeding) / 1_000_000);

        long copying = System.nanoTime();
        for (int k = 1; k < COPIES; k++) for (String table : TABLES) copy(db, table, k);
        for (String table : TABLES) db.sqlQueryMap("ANALYZE TABLE " + table);   // ANALYZE 返回一行四列的结果，不能走 sqlExecute；结果不用
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String table : List.of("flow_instance", "flow_work_item", "flow_candidate", "flow_event", "flow_cc")) {
            counts.put(table, db.sqlQuery("SELECT COUNT(*) FROM " + table, Long.class).get(0));
        }
        System.out.printf("copied to %d copies in %d ms: %s%n", COPIES, (System.nanoTime() - copying) / 1_000_000, counts);

        TestUser starter = STARTERS.get(0);
        Long instanceId = sampleInstance;
        Long openItem = db.sqlQuery("SELECT id FROM flow_work_item WHERE state = 'open' AND instance_id < " + ID_OFFSET + " LIMIT 1", Long.class).get(0);
        Map<String, Supplier<Object>> queries = new LinkedHashMap<>();
        queries.put("todo (finance, role inbox)", () -> engine.todo(FINANCE, 1, 20));
        queries.put("todo (boss, assigned)", () -> engine.todo(BOSS, 1, 20));
        queries.put("done (boss)", () -> engine.done(BOSS, 1, 20));
        queries.put("done (boss, page 50)", () -> engine.done(BOSS, 50, 20));
        queries.put("started (starter)", () -> engine.started(starter, 1, 20));
        queries.put("started (starter, sort -title)", () -> engine.started(starter, ListFilter.builder().sort(SortOrder.parse("-title")).build(), 1, 20));
        queries.put("ccList (starter)", () -> engine.ccList(starter, ListFilter.NONE, 1, 20));
        queries.put("instances (admin, all)", () -> engine.instances(BOSS, new InstanceSearch(null, null, null, null, SortOrder.NONE), 1, 20));
        queries.put("instances (admin, active buy)", () -> engine.instances(BOSS, new InstanceSearch("active", "buy", null, null, SortOrder.NONE), 1, 20));
        queries.put("instances (admin, keyword)", () -> engine.instances(BOSS, new InstanceSearch(null, null, "采购", null, SortOrder.NONE), 1, 20));
        queries.put("instance detail", () -> engine.instance(starter, instanceId));
        queries.put("work item detail", () -> engine.workItem(FINANCE, openItem));
        queries.put("stats processes", () -> engine.processStats(BOSS));
        queries.put("stats nodes (30 days)", () -> engine.nodeStats(BOSS, "buy", Instant.now().minusSeconds(30L * 86400)));
        queries.put("stats handlers (30 days)", () -> engine.handlerStats(BOSS, Instant.now().minusSeconds(30L * 86400)));

        System.out.println("query timings (median of 5, ms):");
        queries.forEach((name, query) -> {
            query.get();   // 预热
            List<Long> times = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                long t = System.nanoTime();
                query.get();
                times.add((System.nanoTime() - t) / 1_000_000);
            }
            times.sort(Long::compare);
            System.out.printf("  %-34s %5d%n", name, times.get(2));
        });
    }

    /** 把一张表的第一份复制成第 k 份：编号列加 k 个编号偏移，人的列加 k 个人的偏移 */
    private static void copy(EasyEntityQuery db, String table, int k) {
        List<String> columns = db.sqlQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
            + "ORDER BY ORDINAL_POSITION", String.class, List.of(table));
        String targets = columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
        String values = columns.stream().map(c -> ID_COLUMNS.contains(c) ? "`" + c + "` + " + (k * ID_OFFSET)
            : USER_COLUMNS.contains(c) ? "`" + c + "` + " + (k * USER_OFFSET) : "`" + c + "`").collect(Collectors.joining(", "));
        // 只复制第一份（编号在偏移以内的）；表单的值以实例编号为主键
        String first = (table.equals("flow_form_data") ? "`instance_id`" : "`id`") + " < " + ID_OFFSET;
        db.sqlExecute("INSERT INTO " + table + " (" + targets + ") SELECT " + values + " FROM " + table + " WHERE " + first);
    }
}
