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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PublicProductControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void listReturnsOnlyPublishedProducts() throws Exception {
        createDraft("draft-product");
        String published = createPublished("published-product", "{\"featured\":false}", null);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(published));
    }

    @Test
    void listExcludesSoftDeletedProducts() throws Exception {
        String published = createPublished("deleted-product", "{}", null);
        jdbc.update(
                "UPDATE products SET deleted_at = clock_timestamp(), status = 'INACTIVE' WHERE id = ?",
                UUID.fromString(published));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void searchFiltersByQueryAndPrice() throws Exception {
        createPublished("cheap-shirt", "{}", null);
        createPublishedVariant("cheap-shirt", "CHEAP-1", 100);
        createPublished("expensive-shirt", "{}", null);
        createPublishedVariant("expensive-shirt", "EXPENSIVE-1", 5000);

        mockMvc.perform(
                        get("/api/v1/products/search")
                                .param("q", "shirt")
                                .param("minPrice", "1000")
                                .param("maxPrice", "6000")
                                .param("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("expensive-shirt"));
    }

    @Test
    void featuredReturnsOnlyMetadataFeatured() throws Exception {
        String featured = createPublished("featured", "{\"featured\":true}", null);
        createPublished("not-featured", "{}", null);

        mockMvc.perform(get("/api/v1/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(featured));
    }

    @Test
    void newArrivalsSortedByPublishedAtDescending() throws Exception {
        String first = createPublished("first-arrival", "{}", null);
        Thread.sleep(10);
        String second = createPublished("second-arrival", "{}", null);

        mockMvc.perform(get("/api/v1/products/new-arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first));
    }

    @Test
    void byBrandReturnsOnlyThatBrandsProducts() throws Exception {
        String brand = createBrand("brand-a");
        String otherBrand = createBrand("brand-b");
        String matching = createPublished("brand-match", "{}", brand);
        createPublished("brand-other", "{}", otherBrand);

        mockMvc.perform(get("/api/v1/products/brands/{brandId}", brand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(matching));
    }

    @Test
    void byCategoryReturnsOnlyThatCategorysProducts() throws Exception {
        String category = createCategory("cat-a");
        String otherCategory = createCategory("cat-b");
        String matching = createPublished("cat-match", "{}", null);
        assignCategory(matching, category);
        String other = createPublished("cat-other", "{}", null);
        assignCategory(other, otherCategory);

        mockMvc.perform(get("/api/v1/products/categories/{categoryId}", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(matching));
    }

    @Test
    void getReturnsPublishedProduct() throws Exception {
        String id = createPublished("get-me", "{}", null);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.slug").value("get-me"));
    }

    @Test
    void getReturns404ForDraft() throws Exception {
        String id = createDraft("draft-only");

        mockMvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBySlugReturnsPublishedProduct() throws Exception {
        String id = createPublished("slug-me", "{}", null);

        mockMvc.perform(get("/api/v1/products/slug/{slug}", "slug-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getBySlugReturns404ForDraft() throws Exception {
        createDraft("draft-slug");

        mockMvc.perform(get("/api/v1/products/slug/{slug}", "draft-slug"))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantsReturns404ForDraftProduct() throws Exception {
        String id = createDraft("draft-variants");

        mockMvc.perform(get("/api/v1/products/{id}/variants", id)).andExpect(status().isNotFound());
    }

    @Test
    void variantReturnsActiveVariant() throws Exception {
        String id = createPublished("variant-product", "{}", null);
        String variant = createPublishedVariant("variant-product", "V-1", 100);

        mockMvc.perform(get("/api/v1/products/{productId}/variants/{variantId}", id, variant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variant));
    }

    @Test
    void variantReturns404ForInactiveVariant() throws Exception {
        String id = createPublished("variant-inactive", "{}", null);
        String variant = createPublishedVariant("variant-inactive", "V-1", 100);
        jdbc.update(
                "UPDATE product_variants SET status = 'INACTIVE' WHERE id = ?",
                UUID.fromString(variant));

        mockMvc.perform(get("/api/v1/products/{productId}/variants/{variantId}", id, variant))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantReturns404ForUnknownVariant() throws Exception {
        String id = createPublished("variant-unknown", "{}", null);

        mockMvc.perform(
                        get(
                                "/api/v1/products/{productId}/variants/{variantId}",
                                id,
                                UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void responseDoesNotLeakAdminFields() throws Exception {
        String id = createPublished("public-shape", "{}", null);
        createPublishedVariant("public-shape", "PUB-1", 100);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.variants[0].sku").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    private String createDraft(String slug) throws Exception {
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

    private String createPublished(String slug, String metadata, String brandId) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("{\"name\":\"Product ")
                .append(slug)
                .append("\",\"slug\":\"")
                .append(slug)
                .append("\"");
        if (metadata != null) {
            body.append(",\"metadata\":").append(metadata);
        }
        if (brandId != null) {
            body.append(",\"brandId\":\"").append(brandId).append("\"");
        }
        body.append("}");

        String id =
                mockMvc.perform(
                                post("/api/v1/admin/products")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body.toString()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
                        .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        createVariantInternal(id, "PUB-" + slug, 100);
        publishProduct(id);
        return id;
    }

    private void publishProduct(String productId) throws Exception {
        long version = productVersion(productId);
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", productId)
                                .header("If-Match", "\"" + version + "\""))
                .andExpect(status().isOk());
    }

    private String createVariantInternal(String productId, String sku, long price)
            throws Exception {
        long version = productVersion(productId);
        return mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\""
                                                + sku
                                                + "\",\"price\":"
                                                + price
                                                + ",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createPublishedVariant(String slug, String sku, long price) throws Exception {
        String productId =
                jdbc.queryForObject(
                        "SELECT id::text FROM products WHERE slug = ?", String.class, slug);
        return createVariantInternal(productId, sku, price);
    }

    private String createBrand(String slug) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Brand\",\"slug\":\"" + slug + "\"}"))
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
                                .content("{\"name\":\"Category\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void assignCategory(String productId, String categoryId) throws Exception {
        long version = productVersion(productId);
        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isCreated());
    }

    private void addMedia(String productId, String type, int position, boolean primary)
            throws Exception {
        long version = productVersion(productId);
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"" + version + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\""
                                                + type
                                                + "\",\"storageKey\":\"media/m-"
                                                + position
                                                + ".webp\",\"position\":"
                                                + position
                                                + ",\"primary\":"
                                                + primary
                                                + "}"))
                .andExpect(status().isCreated());
    }

    private long productVersion(String productId) {
        return jdbc.queryForObject(
                "SELECT version FROM products WHERE id = ?",
                Long.class,
                UUID.fromString(productId));
    }
}
