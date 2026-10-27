package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.engine.query.InstanceQueries.InstanceSearch;
import org.dpdns.zerodep.flow.core.engine.query.ListFilter;
import org.dpdns.zerodep.flow.core.engine.query.SortOrder;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.ActorProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 引擎自带的 HTTP 接口：查询（{@code /queries/*}，GET）。列表的筛选、排序见 {@link #filter}；总表见 {@link FlowHttpSupport}。 */
@RestController
@RequestMapping("${flow.http.path:/flow}")
public class FlowQueryController extends FlowHttpSupport {
    public FlowQueryController(FlowEngine<?> engine, ObjectProvider<ActorProvider<?>> actors) { super(engine, actors); }

    /** 我发起前存的草稿 */
    @GetMapping("/queries/extensions")
    public Map<String, Object> extensions() {
        return respond(() -> engine.extensions(actor()));
    }

    @GetMapping("/queries/start-drafts")
    public Map<String, Object> startDrafts() {
        return respond(() -> engine.startDrafts(actor()));
    }

    /** 一个流程、表单的草稿；没有时 data 为 null */
    @GetMapping("/queries/drafts/{kind}/{key}")
    public Map<String, Object> designDraft(@PathVariable("kind") String kind, @PathVariable("key") String key) {
        return respond(() -> engine.designDraft(actor(), kind, key));
    }

    /** 某一种的全部草稿（不带内容） */
    @GetMapping("/queries/drafts/{kind}")
    public Map<String, Object> designDrafts(@PathVariable("kind") String kind) {
        return respond(() -> engine.designDrafts(actor(), kind));
    }

    /** 外部参与方看的进度 */
    @GetMapping("/queries/instances/{id}/progress")
    public Map<String, Object> progress(@PathVariable("id") Long id) {
        return respond(() -> engine.progress(actor(), id));
    }

    /** 统计：各流程的实例数 */
    @GetMapping("/queries/stats/processes")
    public Map<String, Object> processStats() {
        return respond(() -> engine.processStats(actor()));
    }

    /** 统计：一个流程各节点的停留与超时（since 不写取 30 天前） */
    @GetMapping("/queries/stats/nodes")
    public Map<String, Object> nodeStats(@RequestParam("processKey") String processKey, @RequestParam(name = "since", required = false) String since) {
        return respond(() -> engine.nodeStats(actor(), processKey, sinceOf(since)));
    }

    /** 统计：处理人的工作量（since 不写取 30 天前） */
    @GetMapping("/queries/stats/handlers")
    public Map<String, Object> handlerStats(@RequestParam(name = "since", required = false) String since) {
        return respond(() -> engine.handlerStats(actor(), sinceOf(since)));
    }

    private static Instant sinceOf(String since) {
        return since == null || since.isBlank() ? Instant.now().minus(java.time.Duration.ofDays(30)) : Instant.parse(since);
    }

    /** 全部工作日历 */
    @GetMapping("/queries/calendars")
    public Map<String, Object> calendars() {
        return respond(() -> engine.calendars(actor()));
    }

    /** 管理端的表单列表 */
    @GetMapping("/queries/forms")
    public Map<String, Object> forms() {
        return respond(() -> engine.forms(actor()));
    }

    /** 表单的全部版本 */
    @GetMapping("/queries/forms/{key}/versions")
    public Map<String, Object> formVersions(@PathVariable("key") String key) {
        return respond(() -> engine.formVersions(actor(), key));
    }

    /** 用了这张表单的流程（最新发布的版本引用它的） */
    @GetMapping("/queries/forms/{key}/usages")
    public Map<String, Object> formUsages(@PathVariable("key") String key) {
        return respond(() -> engine.formUsages(actor(), key));
    }

    /** 表单某个版本的源码与字段清单（version 不写取最新的） */
    @GetMapping("/queries/forms/{key}/document")
    public Map<String, Object> formDocument(@PathVariable("key") String key, @RequestParam(name = "version", required = false) Integer version) {
        return respond(() -> engine.formDocument(actor(), key, version));
    }

    /** 我能发起的流程 */
    @GetMapping("/queries/startable")
    public Map<String, Object> startable() {
        return respond(() -> engine.startable(actor()));
    }

    /** 发起页：流程与开始节点上的表单 */
    @GetMapping("/queries/start/{key}")
    public Map<String, Object> startView(@PathVariable("key") String key, @RequestParam(name = "draftId", required = false) Long draftId) {
        return respond(() -> engine.startView(actor(), key, draftId));
    }

    /** 我的待办；参数名同 vue-tsx-pro 的表格请求（page、pageSize），筛选见 {@link #filter}（不支持排序） */
    @GetMapping("/queries/todo")
    public Map<String, Object> todo(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                       @RequestParam Map<String, String> query) {
        return respond(() -> engine.todo(actor(), filter(query), page, pageSize));
    }

    /** 我已办 */
    @GetMapping("/queries/done")
    public Map<String, Object> done(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                       @RequestParam Map<String, String> query) {
        return respond(() -> engine.done(actor(), filter(query), page, pageSize));
    }

    /** 管理端搜实例：{@code ?status=&processKey=&keyword=&startedBy=&page=&pageSize=} */
    @GetMapping("/queries/instances")
    public Map<String, Object> instances(@RequestParam(name = "status", required = false) String status,
                                        @RequestParam(name = "processKey", required = false) String processKey,
                                        @RequestParam(name = "keyword", required = false) String keyword,
                                        @RequestParam(name = "startedBy", required = false) Long startedBy,
                                        @RequestParam(name = "sort", required = false) String sort,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.instances(actor(), new InstanceSearch(status, processKey, keyword, startedBy, SortOrder.parse(sort)), page, pageSize));
    }

    /** 流程的全部版本 */
    @GetMapping("/queries/processes/{key}/versions")
    public Map<String, Object> versions(@PathVariable("key") String key) {
        return respond(() -> engine.versions(actor(), key));
    }

    /** 某个版本的定义 JSON（version 不写取最新的）：返回 {@code { json }}，json 是发布时的原文 */
    @GetMapping("/queries/processes/{key}/definition")
    public Map<String, Object> definition(@PathVariable("key") String key, @RequestParam(name = "version", required = false) Integer version) {
        return respond(() -> Map.of("json", engine.definitionJson(actor(), key, version)));
    }

    /** 管理端的流程列表 */
    @GetMapping("/queries/processes")
    public Map<String, Object> processes() {
        return respond(() -> engine.processes(actor()));
    }

    /** 异常：重试用完的作业；带 instanceId 时只看这个实例的 */
    @GetMapping("/queries/incidents")
    public Map<String, Object> incidents(@RequestParam(name = "instanceId", required = false) Long instanceId,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.incidents(actor(), instanceId, page, pageSize));
    }

    /** 等我指定承办人的待办 */
    @GetMapping("/queries/designations")
    public Map<String, Object> designations(@RequestParam(name = "page", defaultValue = "1") int page,
                                               @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return respond(() -> engine.designations(actor(), page, pageSize));
    }

    /** 抄送我的 */
    @GetMapping("/queries/cc")
    public Map<String, Object> ccList(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                       @RequestParam Map<String, String> query) {
        return respond(() -> engine.ccList(actor(), filter(query), page, pageSize));
    }

    /** 我发起的 */
    @GetMapping("/queries/started")
    public Map<String, Object> started(@RequestParam(name = "page", defaultValue = "1") int page,
                                       @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                       @RequestParam Map<String, String> query) {
        return respond(() -> engine.started(actor(), filter(query), page, pageSize));
    }

    /**
     * 列表的筛选与排序，都在查询串里：{@code processKey}；{@code from}、{@code to} 是 ISO 的时刻（2026-10-01T00:00:00Z）；
     * {@code sort} 同组件库 formatSorts 的写法（{@code -startedAt,title}）；{@code archived=true} 只看已归档的；{@code biz.} 开头的去掉前缀交给接入方（TodoQueryCustomizer）
     */
    private static ListFilter filter(Map<String, String> query) {
        Map<String, String> business = new LinkedHashMap<>();
        query.forEach((name, value) -> { if (name.startsWith("biz.")) business.put(name.substring("biz.".length()), value); });
        return ListFilter.builder().processKey(blankToNull(query.get("processKey"))).from(instant(query.get("from"))).to(instant(query.get("to")))
            .business(business).sort(SortOrder.parse(query.get("sort"))).archived("true".equals(query.get("archived"))).build();
    }

    private static Instant instant(String text) { return text == null || text.isBlank() ? null : Instant.parse(text); }

    private static String blankToNull(String text) { return text == null || text.isBlank() ? null : text; }

    /** 待办详情：表单、处理结果、我能不能认领与处理 */
    @GetMapping("/queries/work-items/{id}")
    public Map<String, Object> workItem(@PathVariable("id") Long id) {
        return respond(() -> engine.workItem(actor(), id));
    }

    /** 实例详情 */
    @GetMapping("/queries/instances/{id}")
    public Map<String, Object> instance(@PathVariable("id") Long id) {
        return respond(() -> engine.instance(actor(), id));
    }
}
