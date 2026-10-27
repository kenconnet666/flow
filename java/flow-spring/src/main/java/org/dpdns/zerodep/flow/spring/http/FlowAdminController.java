package org.dpdns.zerodep.flow.spring.http;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.ActorProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 引擎自带的 HTTP 接口：管理员的操作（{@code /admin/*}）与工作日历的维护（{@code /calendars/*}）。总表见 {@link FlowHttpSupport}。 */
@RestController
@RequestMapping("${flow.http.path:/flow}")
public class FlowAdminController extends FlowHttpSupport {
    public FlowAdminController(FlowEngine<?> engine, ObjectProvider<ActorProvider<?>> actors) { super(engine, actors); }

    /** 管理员改数据：{@code { instanceId, variables, form, reason }} */
    @PostMapping("/admin/change-data")
    public Map<String, Object> changeData(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/change-data", () -> ResponseBody.of(engine.changeData(actor(), RequestBody.parse(body).changeData())));
    }

    @PostMapping("/admin/terminate")
    public Map<String, Object> terminate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/terminate", () -> ResponseBody.of(engine.terminate(actor(), RequestBody.parse(body).terminate())));
    }

    @PostMapping("/admin/suspend")
    public Map<String, Object> suspend(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/suspend", () -> ResponseBody.of(engine.suspend(actor(), RequestBody.parse(body).suspend())));
    }

    @PostMapping("/admin/resume")
    public Map<String, Object> resume(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/resume", () -> ResponseBody.of(engine.resume(actor(), RequestBody.parse(body).resume())));
    }

    @PostMapping("/admin/jump")
    public Map<String, Object> jump(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/jump", () -> ResponseBody.of(engine.jump(actor(), RequestBody.parse(body).jump())));
    }

    @PostMapping("/admin/reassign")
    public Map<String, Object> reassign(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/reassign", () -> ResponseBody.of(engine.reassign(actor(), RequestBody.parse(body).reassign())));
    }

    /** 管理员把实例迁到另一个版本：{@code { instanceId, version, nodes: { 旧: 新 }, reason }} */
    @PostMapping("/admin/migrate")
    public Map<String, Object> migrate(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/migrate", () -> ResponseBody.of(engine.migrate(actor(), RequestBody.parse(body).migrate())));
    }

    /** 保存一份工作日历：{@code { name, zone, workweek, days: [{ day, workday, note }] }} */
    @PostMapping("/calendars/save")
    public Map<String, Object> saveCalendar(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("calendars/save", () -> engine.saveCalendar(actor(), RequestBody.parse(body).calendar()));
    }

    /** 删掉一份工作日历：{@code { name }} */
    @PostMapping("/calendars/delete")
    public Map<String, Object> deleteCalendar(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        RequestBody request = RequestBody.parse(body);
        return command("calendars/delete", () -> {
            engine.deleteCalendar(actor(), request.text("name"));
            return Map.of();
        });
    }

    /** 失败的作业重新来过（管理员）：{@code { jobId }} */
    @PostMapping("/admin/retry-job")
    public Map<String, Object> retryJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/retry-job", () -> Map.of("revived", engine.retryJob(actor(), RequestBody.parse(body).retryJob())));
    }

    /** 跳过失败的作业（管理员）：{@code { jobId, outcome, reason }} */
    @PostMapping("/admin/skip-job")
    public Map<String, Object> skipJob(@org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        return command("admin/skip-job", () -> ResponseBody.of(engine.skipJob(actor(), RequestBody.parse(body).skipJob())));
    }
}
