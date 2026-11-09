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
class ProductVariantControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void createPersistsVariantAndIncrementsProductVersion() throws Exception {
        String product = createProduct("variant-product");

        String variantId =
                mockMvc.perform(
                                post("/api/v1/admin/products/{productId}/variants", product)
                                        .header("If-Match", "\"0\"")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sku\":\"SKU-1\",\"barcode\":\"BAR-1\","
                                                        + "\"price\":19.99,\"currency\":\"INR\","
                                                        + "\"attributes\":{\"size\":\"M\"}}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.sku").value("SKU-1"))
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .toString();

        assertThat(countVariants(product)).isEqualTo(1);
        assertThat(productVersion(product)).isEqualTo(1L);
        assertThat(variantId).isNotBlank();
    }

    @Test
    void createRejectsDuplicateSkuWith409() throws Exception {
        String product = createProduct("dup-sku");
        createVariant(product, "SKU-DUP", null);

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-DUP\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Variant SKU already exists")));

        assertThat(countVariants(product)).isEqualTo(1);
    }

    @Test
    void createRejectsDuplicateBarcodeWith409() throws Exception {
        String product = createProduct("dup-barcode");
        createVariant(product, "SKU-A", "BAR-DUP");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-B\",\"barcode\":\"BAR-DUP\","
                                                + "\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Variant barcode already exists")));
    }

    @Test
    void createRejectsNegativePriceWith400() throws Exception {
        String product = createProduct("neg-price");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-N\",\"price\":-1.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest());

        assertThat(countVariants(product)).isZero();
    }

    @Test
    void createWithStaleVersionReturns412() throws Exception {
        String product = createProduct("stale-create");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(countVariants(product)).isZero();
    }

    @Test
    void createReturns404WhenProductMissing() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsVariants() throws Exception {
        String product = createProduct("list-variants");
        createVariant(product, "SKU-A", null);
        createVariant(product, "SKU-B", null);

        mockMvc.perform(get("/api/v1/admin/products/{productId}/variants", product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listReturns404WhenProductMissing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{productId}/variants", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsEmptyArrayWhenNoVariants() throws Exception {
        String product = createProduct("empty-list");

        mockMvc.perform(get("/api/v1/admin/products/{productId}/variants", product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getReturnsVariant() throws Exception {
        String product = createProduct("get-variant");
        String variant = createVariant(product, "SKU-GET", null);

        mockMvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                product,
                                variant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variant));
    }

    @Test
    void getReturns404WhenVariantMissing() throws Exception {
        String product = createProduct("get-missing");

        mockMvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                product,
                                UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMutatesFieldsAndIncrementsProductVersion() throws Exception {
        String product = createProduct("update-variant");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        variant)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":25.00,\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.price").value(25.00));

        assertThat(productVersion(product)).isEqualTo(2L);
    }

    @Test
    void updateToDuplicateSkuReturns409() throws Exception {
        String product = createProduct("update-dup-sku");
        createVariant(product, "SKU-TAKEN", null);
        String other = createVariant(product, "SKU-OTHER", null);

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        other)
                                .header("If-Match", "\"2\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sku\":\"SKU-TAKEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Variant SKU already exists")));
    }

    @Test
    void updateWithStaleVersionReturns412() throws Exception {
        String product = createProduct("update-stale");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        variant)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":25.00}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void updateReturns404WhenVariantMissing() throws Exception {
        String product = createProduct("update-missing");

        mockMvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":25.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDeactivatesVariant() throws Exception {
        String product = createProduct("delete-variant");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        variant)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        assertThat(variantStatus(variant)).isEqualTo("INACTIVE");
        assertThat(productVersion(product)).isEqualTo(2L);
    }

    @Test
    void deleteReturns404WhenVariantMissing() throws Exception {
        String product = createProduct("delete-missing");

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithStaleVersionReturns412() throws Exception {
        String product = createProduct("delete-stale");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        product,
                                        variant)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(variantStatus(variant)).isEqualTo("ACTIVE");
    }

    @Test
    void activateReactivatesVariant() throws Exception {
        String product = createProduct("reactivate-variant");
        String variant = createVariant(product, "SKU-1", null);
        deactivate(product, variant, 1L);

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/activate",
                                        product,
                                        variant)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(variantStatus(variant)).isEqualTo("ACTIVE");
    }

    @Test
    void deactivateReturnsInactiveVariant() throws Exception {
        String product = createProduct("deactivate-variant");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        product,
                                        variant)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(variantStatus(variant)).isEqualTo("INACTIVE");
    }

    @Test
    void deactivateLastActiveVariantOfPublishedProductReturns422() throws Exception {
        String product = createProduct("last-active");
        String variant = createVariant(product, "SKU-1", null);
        publish(product, 1L);

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        product,
                                        variant)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isUnprocessableEntity());

        assertThat(variantStatus(variant)).isEqualTo("ACTIVE");
        assertThat(productStatus(product)).isEqualTo("ACTIVE");
    }

    @Test
    void deactivateReturns404WhenVariantMissing() throws Exception {
        String product = createProduct("deactivate-missing");

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        product,
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRollsBackOnStaleVersion() throws Exception {
        String product = createProduct("rollback-create");

        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(countVariants(product)).isZero();
        assertThat(productVersion(product)).isZero();
    }

    @Test
    void deactivateRollsBackOnStaleVersion() throws Exception {
        String product = createProduct("rollback-deactivate");
        String variant = createVariant(product, "SKU-1", null);

        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        product,
                                        variant)
                                .header("If-Match", "\"99\""))
                .andExpect(status().isPreconditionFailed());

        assertThat(variantStatus(variant)).isEqualTo("ACTIVE");
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

    private String createVariant(String product, String sku, String barcode) throws Exception {
        long version = productVersion(product);
        StringBuilder body = new StringBuilder();
        body.append("{\"sku\":\"").append(sku).append("\"");
        if (barcode != null) {
            body.append(",\"barcode\":\"").append(barcode).append("\"");
        }
        body.append(",\"price\":10.00,\"currency\":\"INR\"}");

        return mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", product)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void deactivate(String product, String variant, long version) throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        product,
                                        variant)
                                .header("If-Match", "\"" + version + "\""))
                .andExpect(status().isOk());
    }

    private void publish(String product, long version) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", product)
                                .header("If-Match", "\"" + version + "\""))
                .andExpect(status().isOk());
    }

    private long productVersion(String productId) {
        return jdbc.queryForObject(
                "SELECT version FROM products WHERE id = ?",
                Long.class,
                UUID.fromString(productId));
    }

    private String productStatus(String productId) {
        return jdbc.queryForObject(
                "SELECT status FROM products WHERE id = ?",
                String.class,
                UUID.fromString(productId));
    }

    private String variantStatus(String variantId) {
        return jdbc.queryForObject(
                "SELECT status FROM product_variants WHERE id = ?",
                String.class,
                UUID.fromString(variantId));
    }

    private int countVariants(String productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE product_id = ?",
                Integer.class,
                UUID.fromString(productId));
    }
}
