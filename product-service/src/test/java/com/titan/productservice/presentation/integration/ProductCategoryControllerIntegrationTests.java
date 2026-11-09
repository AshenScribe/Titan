package com.titan.productservice.presentation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProductCategoryControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void replaceSetsAssignmentsAndIncrementsProductVersion() throws Exception {
        String product = createProduct("replace-product");
        String first = createCategory("first");
        String second = createCategory("second");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + first
                                                + "\",\""
                                                + second
                                                + "\"],\"primaryCategoryId\":\""
                                                + first
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].primary").value(true));

        assertThat(countAssignments(product)).isEqualTo(2);
        assertThat(productVersion(product)).isEqualTo(1L);
    }

    @Test
    void replaceWithEmptyListClearsAssignments() throws Exception {
        String product = createProduct("clear-product");
        String category = createCategory("clear-category");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryIds\":[\"" + category + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(countAssignments(product)).isZero();
    }

    @Test
    void replaceWithStaleVersionReturns412() throws Exception {
        String product = createProduct("stale-replace");
        String category = createCategory("stale-cat");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryIds\":[\"" + category + "\"]}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(countAssignments(product)).isZero();
    }

    @Test
    void replaceWithUnknownCategoryReturns404() throws Exception {
        String product = createProduct("fk-product");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Category not found")));

        assertThat(countAssignments(product)).isZero();
        assertThat(productVersion(product)).isZero();
    }

    @Test
    void replaceWithDuplicateIdsReturns409() throws Exception {
        String product = createProduct("dup-product");
        String category = createCategory("dup-cat");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + category
                                                + "\",\""
                                                + category
                                                + "\"]}"))
                .andExpect(status().isConflict());

        assertThat(countAssignments(product)).isZero();
    }

    @Test
    void replaceWithPrimaryNotInListReturns422() throws Exception {
        String product = createProduct("primary-missing");
        String category = createCategory("primary-cat");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + category
                                                + "\"],\"primaryCategoryId\":\""
                                                + UUID.randomUUID()
                                                + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void assignAddsCategoryAndReturns201() throws Exception {
        String product = createProduct("assign-product");
        String category = createCategory("assign-cat");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(category))
                .andExpect(jsonPath("$[0].primary").value(true));

        assertThat(countAssignments(product)).isEqualTo(1);
    }

    @Test
    void assignDuplicateReturns409() throws Exception {
        String product = createProduct("dup-assign");
        String category = createCategory("dup-assign-cat");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already assigned")));
    }

    @Test
    void assignUnknownCategoryReturns404() throws Exception {
        String product = createProduct("unknown-cat-product");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeDeletesAssignment() throws Exception {
        String product = createProduct("remove-product");
        String category = createCategory("remove-cat");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        assertThat(countAssignments(product)).isZero();
    }

    @Test
    void removeUnknownAssignmentReturns404() throws Exception {
        String product = createProduct("remove-missing");
        String category = createCategory("remove-missing-cat");

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void primarySwitchesPrimaryCategory() throws Exception {
        String product = createProduct("switch-product");
        String first = createCategory("switch-first");
        String second = createCategory("switch-second");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + first
                                                + "\",\""
                                                + second
                                                + "\"],\"primaryCategoryId\":\""
                                                + first
                                                + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                        product,
                                        second)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].primary").value(true));

        assertThat(countPrimaryAssignments(product)).isEqualTo(1);
    }

    @Test
    void primaryUnknownAssignmentReturns412() throws Exception {
        String product = createProduct("primary-missing-assignment");
        String category = createCategory("unassigned");

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                        product,
                                        category)
                                .header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(countAssignments(product)).isZero();
        assertThat(countPrimaryAssignments(product)).isZero();
    }

    @Test
    void listReturnsPersistedAssignments() throws Exception {
        String product = createProduct("list-product");
        String first = createCategory("list-first");
        String second = createCategory("list-second");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + first
                                                + "\",\""
                                                + second
                                                + "\"],\"primaryCategoryId\":\""
                                                + second
                                                + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/products/{productId}/categories", product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void listUnknownProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{productId}/categories", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceRollsBackOnUnknownCategory() throws Exception {
        String product = createProduct("rollback-product");
        String category = createCategory("rollback-cat");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + category
                                                + "\",\""
                                                + UUID.randomUUID()
                                                + "\"]}"))
                .andExpect(status().isNotFound());

        assertThat(countAssignments(product)).isZero();
        assertThat(productVersion(product)).isZero();
    }

    @Test
    void removeRollsBackOnStaleVersion() throws Exception {
        String product = createProduct("rollback-remove");
        String category = createCategory("rollback-remove-cat");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        product,
                                        category)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(countAssignments(product)).isEqualTo(1);
    }

    private String createProduct(String slug) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Product\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createCategory(String slug) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + slug + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private long productVersion(String productId) {
        return jdbc.queryForObject(
                "SELECT version FROM products WHERE id = ?",
                Long.class,
                UUID.fromString(productId));
    }

    private int countAssignments(String productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_categories WHERE product_id = ?",
                Integer.class,
                UUID.fromString(productId));
    }

    private int countPrimaryAssignments(String productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_categories WHERE product_id = ? AND is_primary = TRUE",
                Integer.class,
                UUID.fromString(productId));
    }
}
