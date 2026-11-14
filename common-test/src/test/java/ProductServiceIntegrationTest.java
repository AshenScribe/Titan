//------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
//------------------------------------------------------------------------------

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

class ProductServiceIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                                                            .connectTimeout(Duration.ofSeconds(5))
                                                            .build();

    static Network network = Network.newNetwork();
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine").withNetwork(network)
                                                                                       .withNetworkAliases("postgres")
                                                                                       .withDatabaseName("product_db")
                                                                                       .withUsername("postgres")
                                                                                       .withPassword("postgres");
    static RedisContainer redis = new RedisContainer("redis:7.2-alpine").withNetwork(network)
                                                                        .withNetworkAliases("redis");
    static ProductServiceContainer productService = new ProductServiceContainer().withNetwork(network)
                                                                                 .withPostgres(postgres)
                                                                                 .withRedis(redis);

    @BeforeAll
    static void startEnvironment() {
        Startables.deepStart(postgres, redis, productService)
                  .join();
    }

    @Test
    @DisplayName("Should create a draft product and verify aggregate version and real-time logs")
    void createProduct_success_returnsCreatedAndLogs() throws Exception {
        String baseUrl = productService.getHttpBaseUrl();
        String uniqueSlug = "oxford-shirt-" + UUID.randomUUID()
                                                  .toString()
                                                  .substring(0, 8);

        String requestBody = """
                {
                    "name": "Classic Oxford Shirt",
                    "slug": "%s",
                    "description": "Premium cotton button-down shirt"
                }
                """.formatted(uniqueSlug);

        HttpRequest createRequest = HttpRequest.newBuilder()
                                               .uri(URI.create(baseUrl + "/api/v1/admin/products"))
                                               .header("Content-Type", "application/json")
                                               .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                               .build();

        HttpResponse<String> createResponse = HTTP_CLIENT.send(createRequest, HttpResponse.BodyHandlers.ofString());

        Assertions.assertThat(createResponse.statusCode())
                  .isEqualTo(201);

        JsonNode createdJson = MAPPER.readTree(createResponse.body());
        String productId = createdJson.get("id")
                                      .asText();
        Assertions.assertThat(productId)
                  .isNotEmpty();
        Assertions.assertThat(createdJson.get("slug")
                                         .asText())
                  .isEqualTo(uniqueSlug);
        Assertions.assertThat(createdJson.get("status")
                                         .asText())
                  .isEqualTo("DRAFT");
        Assertions.assertThat(createdJson.get("version")
                                         .asLong())
                  .isEqualTo(0L);

        HttpRequest getRequest = HttpRequest.newBuilder()
                                            .uri(URI.create(baseUrl + "/api/v1/admin/products/" + productId))
                                            .GET()
                                            .build();

        HttpResponse<String> getResponse = HTTP_CLIENT.send(getRequest, HttpResponse.BodyHandlers.ofString());
        Assertions.assertThat(getResponse.statusCode())
                  .isEqualTo(200);

        JsonNode fetchedJson = MAPPER.readTree(getResponse.body());
        Assertions.assertThat(fetchedJson.get("name")
                                         .asText())
                  .isEqualTo("Classic Oxford Shirt");

        Path logDir = Paths.get("build", "output", "container");
        Assertions.assertThat(Files.exists(logDir))
                  .isTrue();

        String shortId = productService.getContainerId()
                                       .substring(0, 12);
        Path logFile = logDir.resolve("product-service-" + shortId + ".log");
        Assertions.assertThat(Files.exists(logFile))
                  .isTrue();
        Assertions.assertThat(Files.size(logFile))
                  .isGreaterThan(0);
    }
}