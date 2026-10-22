package com.example.price.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** General API metadata rendered by Swagger UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI priceServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Binance Price Service API")
                .description("Средняя цена торговой пары с Binance: собирается по расписанию, "
                        + "хранится в PostgreSQL и отдаётся через REST.")
                .version("1.0.0"));
    }
}
