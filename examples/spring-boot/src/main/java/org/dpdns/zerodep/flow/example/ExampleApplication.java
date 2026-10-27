package org.dpdns.zerodep.flow.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用：引入 flow-spring 之后，应用自己只写了这几样——
 * <ul>
 *   <li>{@link ExampleOrgDirectory}：组织架构（用户、部门、角色），引擎没有默认的；</li>
 *   <li>{@link ExampleActors}：当前操作人从哪来。用 Spring Security 而且登录的 principal 实现了 {@code Actor} 时连这个也不用写；</li>
 *   <li>{@link ExampleNotifications}：监听流转记录发通知（示例只打日志）；</li>
 *   <li>{@code extension} 包里的扩展，声明成 Bean 就注册了，各自用 {@code info()} 告诉设计器显示名、参数和结果：
 *       动作 {@code demo.push}（能故意失败，演示异常处理）、解析器 {@code demo.leader}、提交前检查 {@code demo.amount-cap}、
 *       计算值 {@code demo.amountLevel}、自定义节点 {@code x-demo-check}。</li>
 * </ul>
 * 其余（数据源、建表、HTTP 接口、节点的业务信息）在 application.yml 里配。启动后接口在 {@code /flow/**}，见 README。
 */
@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
