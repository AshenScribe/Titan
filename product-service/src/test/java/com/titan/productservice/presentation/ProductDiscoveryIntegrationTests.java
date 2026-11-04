package com.titan.productservice;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProductDiscoveryIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void searchFeaturedNewArrivalsAndScopedListingsUsePublicFilters() throws Exception {
        String brandId = createBrand();
        String categoryId = createCategory();
        String featuredId = createProduct("featured-shirt", "{\"featured\":true}", brandId);
        createVariant(featuredId, "FEATURED-1", 1499);
        assignCategory(featuredId, categoryId);
        publish(featuredId, 2);
        String regularId = createProduct("regular-shirt", "{}", brandId);
        createVariant(regularId, "REGULAR-1", 2999);
        publish(regularId, 1);

        mockMvc.perform(
                        get("/api/v1/products/search")
                                .queryParam("q", "shirt")
                                .queryParam("minPrice", "1000")
                                .queryParam("maxPrice", "2000")
                                .queryParam("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(featuredId)));
        mockMvc.perform(get("/api/v1/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(featuredId)));
        mockMvc.perform(get("/api/v1/products/new-arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get("/api/v1/products/brands/{brandId}", brandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get("/api/v1/products/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", equalTo(featuredId)));
    }

    @Test
    void discoveryNeverReturnsDraftProducts() throws Exception {
        createProduct("draft-only", "{\"featured\":true}", null);

        mockMvc.perform(get("/api/v1/products/search").queryParam("q", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
        mockMvc.perform(get("/api/v1/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    private String createBrand() throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Brand\",\"slug\":\"brand\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createCategory() throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Category\",\"slug\":\"category\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createProduct(String slug, String metadata, String brandId) throws Exception {
        String brand = brandId == null ? "" : ",\"brandId\":\"" + brandId + "\"";
        return mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\""
                                                + slug
                                                + "\",\"slug\":\""
                                                + slug
                                                + "\",\"metadata\":"
                                                + metadata
                                                + brand
                                                + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void createVariant(String productId, String sku, int price) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\""
                                                + sku
                                                + "\",\"price\":"
                                                + price
                                                + ",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated());
    }

    private void publish(String productId, long version) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/publish", productId)
                                .header("If-Match", "\"" + version + "\""))
                .andExpect(status().isOk());
    }

    private void assignCategory(String productId, String categoryId) throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isCreated());
    }
}
