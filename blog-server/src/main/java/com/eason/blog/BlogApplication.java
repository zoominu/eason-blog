package com.eason.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Eason Blog 后端入口（模块化单体）。
 *
 * <p>分层约定：controller -> service -> mapper；Entity/DTO/VO 严格分离。
 * 数据库结构只允许通过 Flyway 迁移变更。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.eason.blog.module.**.mapper")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
