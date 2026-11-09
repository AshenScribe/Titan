package com.titan.productservice.presentation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createPersistsDraftAndReturns201() throws Exception {
        String id = createProduct("test-product");

        mockMvc.perform(get("/api/v1/admin/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("test-product")))
                .andExpect(jsonPath("$.status", equalTo("DRAFT")))
                .andExpect(jsonPath("$.version", equalTo(0)));

        assertThat(countRows("products")).isEqualTo(1);
    }

    @Test
    void createRejectsDuplicateSlugWith409() throws Exception {
        createProduct("same-slug");

        mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Other\",\"slug\":\"same-slug\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Product slug already exists")));

        assertThat(countRows("products")).isEqualTo(1);
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \",\"slug\":\"blank\"}"))
                .andExpect(status().isBadRequest());

        assertThat(countRows("products")).isZero();
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByStatusAndName() throws Exception {
        createProduct("alpha");
        createProduct("beta");

        mockMvc.perform(get("/api/v1/admin/products").param("name", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("alpha")));

        mockMvc.perform(get("/api/v1/admin/products").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(2)));

        mockMvc.perform(get("/api/v1/admin/products").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void listRespectsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createProduct("p-" + i);
        }

        mockMvc.perform(get("/api/v1/admin/products").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", equalTo(5)))
                .andExpect(jsonPath("$.totalPages", equalTo(3)));
    }

    @Test
    void listReturns422ForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateMutatesFieldsAndIncrementsVersion() throws Exception {
        String id = createProduct("edit-me");

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Edited\",\"slug\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Edited")))
                .andExpect(jsonPath("$.slug", equalTo("edited")))
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    @Test
    void updateToExistingSlugReturns409() throws Exception {
        createProduct("taken");
        String other = createProduct("other");

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", other)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Product slug already exists")));
    }

    @Test
    void updateWithStaleVersionReturns412() throws Exception {
        String id = createProduct("stale-update");

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void updateReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSoftDeletesProduct() throws Exception {
        String id = createProduct("delete-me");

        mockMvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INACTIVE")))
                .andExpect(jsonPath("$.deletedAt").exists());
    }

    @Test
    void deleteWithStaleVersionReturns412() throws Exception {
        String id = createProduct("stale-delete");

        mockMvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void deleteReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/admin/products/{id}", UUID.randomUUID())
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishWithoutActiveVariantReturns409() throws Exception {
        String id = createProduct("empty-product");

        mockMvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "The operation conflicts with an existing resource or database constraint"));

        assertThat(productStatus(id)).isEqualTo("DRAFT");
        assertThat(productVersion(id)).isZero();
    }

    @Test
    void publishWithActiveVariantSucceeds() throws Exception {
        String id = createProduct("publishable");
        addActiveVariant(id);

        mockMvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.version", equalTo(2)))
                .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    void publishWithStaleVersionReturns412() throws Exception {
        String id = createProduct("stale-publish");

        mockMvc.perform(
                        post("/api/v1/admin/products/{id}/publish", id)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void unpublishActiveProductSucceeds() throws Exception {
        String id = createProduct("unpublishable");
        addActiveVariant(id);
        publish(id, 1L);

        mockMvc.perform(
                        post("/api/v1/admin/products/{id}/unpublish", id)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("DRAFT")))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
    }

    @Test
    void deactivateActiveProductSucceeds() throws Exception {
        String id = createProduct("deactivatable");
        addActiveVariant(id);
        publish(id, 1L);

        mockMvc.perform(
                        post("/api/v1/admin/products/{id}/deactivate", id)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INACTIVE")));
    }

    @Test
    void activateWithoutActiveVariantReturns409() throws Exception {
        String id = createProduct("activate-empty");

        mockMvc.perform(
                        post("/api/v1/admin/products/{id}/activate", id)
                                .header("If-Match", "\"0\""))
                .andExpect(status().isConflict());
    }

    @Test
    void failedCreateDoesNotPersistPartialRow() throws Exception {
        createProduct("dup");

        mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Dup2\",\"slug\":\"dup\"}"))
                .andExpect(status().isConflict());

        assertThat(countRows("products")).isEqualTo(1);
    }

    @Test
    void failedUpdateLeavesOriginalValuesIntact() throws Exception {
        createProduct("taken");
        String other = createProduct("other");

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", other)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Changed\",\"slug\":\"taken\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/products/{id}", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Product other")))
                .andExpect(jsonPath("$.slug", equalTo("other")))
                .andExpect(jsonPath("$.version", equalTo(0)));
    }

    private String createProduct(String slug) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Product "
                                                + slug
                                                + "\",\"slug\":\""
                                                + slug
                                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void addActiveVariant(String productId) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-"
                                                + productId
                                                + "\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isCreated());
    }

    private void publish(String productId, long version) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", productId)
                                .header("If-Match", "\"" + version + "\""))
                .andExpect(status().isOk());
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String productStatus(String productId) {
        return jdbc.queryForObject(
                "SELECT status FROM products WHERE id = ?",
                String.class,
                UUID.fromString(productId));
    }

    private long productVersion(String productId) {
        return jdbc.queryForObject(
                "SELECT version FROM products WHERE id = ?",
                Long.class,
                UUID.fromString(productId));
    }
}
