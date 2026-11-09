// ------------------------------------------------------------------------------
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
// ------------------------------------------------------------------------------

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
class BrandControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createPersistsBrandAndReturns201() throws Exception {
        String id = createBrand("titan-apparel", "Titan Apparel", "{\"country\":\"IN\"}");

        mockMvc.perform(get("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("titan-apparel")))
                .andExpect(jsonPath("$.name", equalTo("Titan Apparel")))
                .andExpect(jsonPath("$.active", equalTo(true)))
                .andExpect(jsonPath("$.metadata.country", equalTo("IN")));

        assertThat(countRows("brands")).isEqualTo(1);
    }

    @Test
    void createRejectsDuplicateSlugWith409() throws Exception {
        createBrand("same-brand", "Same Brand", null);

        mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Other\",\"slug\":\"same-brand\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Brand slug already exists")));

        assertThat(countRows("brands")).isEqualTo(1);
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \",\"slug\":\"blank\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("Request validation failed")));

        assertThat(countRows("brands")).isZero();
    }

    @Test
    void getReturnsPersistedBrand() throws Exception {
        String id = createBrand("read-me", "Read Me", null);

        mockMvc.perform(get("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(id)));
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/brands/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/brands/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByNameAndSlug() throws Exception {
        createBrand("alpha-brand", "Alpha", null);
        createBrand("beta-brand", "Beta", null);

        mockMvc.perform(get("/api/v1/admin/brands").param("name", "Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("alpha-brand")));

        mockMvc.perform(get("/api/v1/admin/brands").param("slug", "beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("beta-brand")));
    }

    @Test
    void listFiltersByActive() throws Exception {
        createBrand("active-brand", "Active", null);
        String inactiveId = createBrand("inactive-brand", "Inactive", null);
        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}/status", inactiveId)
                                .param("active", "false"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/brands").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("active-brand")));

        mockMvc.perform(get("/api/v1/admin/brands").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("inactive-brand")));
    }

    @Test
    void listRespectsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createBrand("p-" + i, "P " + i, null);
        }

        mockMvc.perform(get("/api/v1/admin/brands").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", equalTo(5)))
                .andExpect(jsonPath("$.totalPages", equalTo(3)));

        mockMvc.perform(get("/api/v1/admin/brands").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void listReturns422ForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/brands").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateMutatesAllFields() throws Exception {
        String id = createBrand("edit-me", "Edit Me", null);

        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Edited","slug":"edited","description":"d",
                                 "websiteUrl":"https://x.test","logoUrl":"https://x.test/l.png",
                                 "metadata":{"k":"v"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Edited")))
                .andExpect(jsonPath("$.slug", equalTo("edited")))
                .andExpect(jsonPath("$.metadata.k", equalTo("v")));
    }

    @Test
    void updateToExistingSlugReturns409() throws Exception {
        createBrand("taken", "Taken", null);
        String other = createBrand("other", "Other", null);

        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Brand slug already exists")));
    }

    @Test
    void updateReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidSlugWith400() throws Exception {
        String id = createBrand("valid-slug", "Valid", null);

        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slug\":\"INVALID SLUG\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesBrand() throws Exception {
        String id = createBrand("delete-me", "Delete Me", null);

        mockMvc.perform(delete("/api/v1/admin/brands/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/brands/{id}", id)).andExpect(status().isNotFound());
        assertThat(countRows("brands")).isZero();
    }

    @Test
    void deleteReturns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/brands/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsRejectedWhenReferencedByProductWith409() throws Exception {
        String id = createBrand("referenced", "Referenced", null);
        jdbc.update(
                """
                INSERT INTO products (id, brand_id, name, slug, status, version)
                VALUES (gen_random_uuid(), ?, 'Product', 'brand-product', 'DRAFT', 0)
                """,
                UUID.fromString(id));

        mockMvc.perform(delete("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Referenced resource")));

        assertThat(countRows("brands")).isEqualTo(1);
        assertThat(countProductsForBrand(id)).isEqualTo(1);
    }

    @Test
    void statusDeactivatesAndActivates() throws Exception {
        String id = createBrand("toggle", "Toggle", null);

        mockMvc.perform(patch("/api/v1/admin/brands/{id}/status", id).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));

        mockMvc.perform(patch("/api/v1/admin/brands/{id}/status", id).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(true)));
    }

    @Test
    void statusReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}/status", UUID.randomUUID())
                                .param("active", "true"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusRequiresActiveParamWith400() throws Exception {
        String id = createBrand("no-param", "No Param", null);

        mockMvc.perform(patch("/api/v1/admin/brands/{id}/status", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failedCreateDoesNotPersistPartialRow() throws Exception {
        createBrand("dup", "Dup", null);

        mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Dup2\",\"slug\":\"dup\"}"))
                .andExpect(status().isConflict());

        assertThat(countRows("brands")).isEqualTo(1);
    }

    @Test
    void failedUpdateLeavesOriginalValuesIntact() throws Exception {
        createBrand("taken", "Taken", null);
        String other = createBrand("other", "Other", null);

        mockMvc.perform(
                        patch("/api/v1/admin/brands/{id}", other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Changed\",\"slug\":\"taken\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/brands/{id}", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Other")))
                .andExpect(jsonPath("$.slug", equalTo("other")));
    }

    private String createBrand(String slug, String name, String metadataJson) throws Exception {
        String metadata = metadataJson == null ? "{}" : metadataJson;
        return mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\""
                                                + name
                                                + "\",\"slug\":\""
                                                + slug
                                                + "\",\"metadata\":"
                                                + metadata
                                                + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countProductsForBrand(String brandId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE brand_id = ?",
                Integer.class,
                UUID.fromString(brandId));
    }
}
