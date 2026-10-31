package com.titan.productservice.presentation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.TestcontainersConfiguration;
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
class BrandControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createsGetsListsUpdatesAndChangesStatus() throws Exception {
        String id = createBrand("titan-apparel", "Titan Apparel");

        mockMvc.perform(get("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("titan-apparel")));
        mockMvc.perform(
                        get("/api/v1/admin/brands")
                                .queryParam("page", "0")
                                .queryParam("size", "10")
                                .queryParam("name", "Titan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Titan Clothing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Titan Clothing")));
        mockMvc.perform(patch("/api/v1/admin/brands/{id}/status", id).queryParam("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));
    }

    @Test
    void duplicateSlugAndReferencedDeletionReturnConflicts() throws Exception {
        String first = createBrand("same-brand", "Same Brand");
        mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Other\",\"slug\":\"same-brand\"}"))
                .andExpect(status().isConflict());
        String brandId = first;
        jdbc.update(
                """
                INSERT INTO products (id, brand_id, name, slug, status, version)
                VALUES (gen_random_uuid(), ?, 'Product', 'brand-product', 'DRAFT', 0)
                """,
                java.util.UUID.fromString(brandId));
        mockMvc.perform(delete("/api/v1/admin/brands/{id}", brandId))
                .andExpect(status().isConflict());
    }

    @Test
    void missingBrandReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/brands/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    private String createBrand(String slug, String name) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }
}
