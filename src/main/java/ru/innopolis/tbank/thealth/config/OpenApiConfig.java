package ru.innopolis.tbank.thealth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "T-Health API",
                version = "0.0.1",
                description = "REST API backend for the T-Health practicum project"
        )
)
public class OpenApiConfig {
}