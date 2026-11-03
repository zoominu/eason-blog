package com.eason.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。接口变更必须同步更新 docs/API.md。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI blogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Eason Blog API")
                .version("v1")
                .description("单作者个人技术博客后端接口，统一前缀 /api/v1"));
    }
}
