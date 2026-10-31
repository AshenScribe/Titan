package com.titan.productservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Titan Product Service API")
                                .version("v1")
                                .description(
                                        "Catalog administration APIs for products. "
                                                + "Product prices and inventory are owned by separate services.")
                                .contact(new Contact().name("Titan Engineering"))
                                .license(new License().name("MIT")))
                .externalDocs(
                        new ExternalDocumentation()
                                .description("Titan project documentation")
                                .url("https://github.com/AshenScribe/Titan"))
                .components(
                        new Components()
                                .addSchemas(
                                        "ErrorResponse",
                                        new Schema<>()
                                                .type("object")
                                                .addProperty(
                                                        "timestamp",
                                                        new Schema<>()
                                                                .type("string")
                                                                .format("date-time"))
                                                .addProperty(
                                                        "status", new Schema<>().type("integer"))
                                                .addProperty("error", new StringSchema())
                                                .addProperty("message", new StringSchema())
                                                .addProperty(
                                                        "details",
                                                        new Schema<>()
                                                                .type("array")
                                                                .items(new StringSchema())))
                                .addParameters(
                                        "IfMatch",
                                        new HeaderParameter()
                                                .name("If-Match")
                                                .description(
                                                        "Expected product aggregate version, for example \"8\".")
                                                .required(true)
                                                .schema(new StringSchema().example("\"8\""))));
    }
}
