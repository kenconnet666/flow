package org.dpdns.zerodep.flow.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用：引入 flow-spring 之后，应用自己只写了两样东西——
 * <ul>
 *   <li>{@link ExampleOrgDirectory}：组织架构（用户、部门、角色），引擎没有默认的；</li>
 *   <li>{@link ExampleActors}：当前操作人从哪来。用 Spring Security 而且登录的 principal 实现了 {@code Actor} 时连这个也不用写。</li>
 * </ul>
 * 其余（数据源、建表、HTTP 接口）在 application.yml 里配。启动后接口在 {@code /flow/**}，见 README。
 */
@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
