package ru.schigreva.evently.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun eventlyOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("Evently API")
                .version("1.0")
                .description("Поиск и персональный подбор городских событий"))
            .addSecurityItem(SecurityRequirement().addList("Bearer"))
            .components(Components()
                .addSecuritySchemes("Bearer", SecurityScheme()
                    .name("Bearer")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
    }
}
