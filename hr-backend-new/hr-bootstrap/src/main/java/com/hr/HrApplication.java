package com.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能招聘系统 Java 后端启动入口。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.hr")
@EnableJpaRepositories(basePackages = "com.hr")
@EntityScan(basePackages = "com.hr")
@EnableScheduling
public class HrApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrApplication.class, args);
    }
}
