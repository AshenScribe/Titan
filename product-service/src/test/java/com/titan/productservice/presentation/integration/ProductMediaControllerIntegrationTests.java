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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ProductMediaControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createPersistsMediaAndReturns201() throws Exception {
        String product = createProduct("media-product");

        String mediaId =
                mockMvc.perform(
                                post("/api/v1/admin/products/{productId}/media", product)
                                        .header("If-Match", "\"0\"")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"mediaType\":\"IMAGE\",\"storageKey\":\"media/first.webp\","
                                                        + "\"position\":0,\"primary\":true}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                        .andExpect(jsonPath("$.storageKey").value("media/first.webp"))
                        .andExpect(jsonPath("$.primary").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
                        .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        assertThat(countMedia(product)).isEqualTo(1);
        assertThat(productVersion(product)).isEqualTo(1L);
        assertThat(mediaId).isNotBlank();
    }

    @Test
    void createWithPrimaryReplacesExistingPrimary() throws Exception {
        String product = createProduct("replace-primary");
        createMedia(product, 0, true);

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"VIDEO\",\"storageKey\":\"media/second.mp4\","
                                                + "\"position\":1,\"primary\":true}"))
                .andExpect(status().isCreated());

        assertThat(countMedia(product)).isEqualTo(2);
        assertThat(countPrimaryMedia(product)).isEqualTo(1);
        assertThat(productVersion(product)).isEqualTo(2L);
    }

    @Test
    void createWithStaleVersionReturns412() throws Exception {
        String product = createProduct("stale-create");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(countMedia(product)).isZero();
    }

    @Test
    void createRejectsInvalidMediaTypeWith400() throws Exception {
        String product = createProduct("bad-type");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"AUDIO\",\"storageKey\":\"k/1.mp3\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        assertThat(countMedia(product)).isZero();
    }

    @Test
    void listReturnsMediaOrderedByPosition() throws Exception {
        String product = createProduct("list-media");
        createMedia(product, 2, false);
        createMedia(product, 0, false);
        createMedia(product, 1, true);

        mockMvc.perform(get("/api/v1/admin/products/{productId}/media", product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[2].position").value(2));
    }

    @Test
    void listReturns404WhenProductMissing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{productId}/media", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMutatesFields() throws Exception {
        String product = createProduct("update-media");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mediaType\":\"VIDEO\",\"altText\":\"video alt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.altText").value("video alt"));

        assertThat(productVersion(product)).isEqualTo(2L);
    }

    @Test
    void updateToPrimaryClearsPreviousPrimary() throws Exception {
        String product = createProduct("update-primary");
        createMedia(product, 0, true);
        String second = createMedia(product, 1, false);

        mockMvc.perform(
                        patch("/api/v1/admin/products/{productId}/media/{mediaId}", product, second)
                                .header("If-Match", "\"2\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));

        assertThat(countPrimaryMedia(product)).isEqualTo(1);
    }

    @Test
    void updateReturns404WhenMediaMissing() throws Exception {
        String product = createProduct("update-missing");

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"altText\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithStaleVersionReturns412() throws Exception {
        String product = createProduct("stale-update-media");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"altText\":\"x\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void deleteRemovesMedia() throws Exception {
        String product = createProduct("delete-media");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        assertThat(countMedia(product)).isZero();
        assertThat(productVersion(product)).isEqualTo(2L);
    }

    @Test
    void deleteReturns404WhenMediaMissing() throws Exception {
        String product = createProduct("delete-missing-media");

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithStaleVersionReturns412() throws Exception {
        String product = createProduct("stale-delete-media");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(countMedia(product)).isEqualTo(1);
    }

    @Test
    void reorderUpdatesPositions() throws Exception {
        String product = createProduct("reorder-media");
        String first = createMedia(product, 0, false);
        String second = createMedia(product, 1, false);
        String third = createMedia(product, 2, true);

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", product)
                                .header("If-Match", "\"3\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaIds\":[\""
                                                + third
                                                + "\",\""
                                                + first
                                                + "\",\""
                                                + second
                                                + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(third))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[2].id").value(second))
                .andExpect(jsonPath("$[2].position").value(2));
    }

    @Test
    void reorderRejectsIncompleteListWith422() throws Exception {
        String product = createProduct("reorder-incomplete");
        createMedia(product, 0, false);
        createMedia(product, 1, false);

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", product)
                                .header("If-Match", "\"2\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mediaIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath(
                                "$.message", containsString("Reorder request must contain every")));
    }

    @Test
    void reorderRejectsDuplicateIdsWith422() throws Exception {
        String product = createProduct("reorder-dup");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", product)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaIds\":[\"" + mediaId + "\",\"" + mediaId + "\"]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void primarySwitchesPrimaryMedia() throws Exception {
        String product = createProduct("switch-primary");
        createMedia(product, 0, true);
        String second = createMedia(product, 1, false);

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        product,
                                        second)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.primary").value(true));

        assertThat(countPrimaryMedia(product)).isEqualTo(1);
    }

    @Test
    void primaryReturns404WhenMediaMissing() throws Exception {
        String product = createProduct("primary-missing");

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void primaryWithStaleVersionReturns412() throws Exception {
        String product = createProduct("primary-stale");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void createRollsBackOnStaleVersion() throws Exception {
        String product = createProduct("rollback-create");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(countMedia(product)).isZero();
        assertThat(productVersion(product)).isZero();
    }

    @Test
    void deleteRollsBackOnStaleVersion() throws Exception {
        String product = createProduct("rollback-delete");
        String mediaId = createMedia(product, 0, false);

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        product,
                                        mediaId)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(countMedia(product)).isEqualTo(1);
        assertThat(productVersion(product)).isEqualTo(1L);
    }

    @Test
    void createRejectsInvalidMediaTypeAtValidation() throws Exception {
        String product = createProduct("validation-type");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"AUDIO\",\"storageKey\":\"k/1.mp3\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        assertThat(countMedia(product)).isZero();
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

    private String createMedia(String product, int position, boolean primary) throws Exception {
        long version = productVersion(product);
        String type = position % 2 == 0 ? "IMAGE" : "VIDEO";
        String ext = position % 2 == 0 ? ".webp" : ".mp4";
        return mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", product)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\""
                                                + type
                                                + "\",\"storageKey\":\"media/m-"
                                                + position
                                                + ext
                                                + "\",\"position\":"
                                                + position
                                                + ",\"primary\":"
                                                + primary
                                                + "}"))
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

    private int countMedia(String productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_media WHERE product_id = ?",
                Integer.class,
                UUID.fromString(productId));
    }

    private int countPrimaryMedia(String productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_media WHERE product_id = ? AND is_primary = TRUE",
                Integer.class,
                UUID.fromString(productId));
    }
}
