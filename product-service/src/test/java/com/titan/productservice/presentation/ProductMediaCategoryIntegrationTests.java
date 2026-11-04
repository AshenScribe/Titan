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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProductMediaCategoryIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void mediaCrudPrimaryAndReorderIncrementAggregateVersion() throws Exception {
        String productId = createProduct("media-product");
        String firstMedia = createMedia(productId, "media/first.webp", 0, false);
        String secondMedia = createMedia(productId, "media/second.mp4", 1, true);

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", productId)
                                .header("If-Match", "\"2\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaIds\":[\""
                                                + secondMedia
                                                + "\",\""
                                                + firstMedia
                                                + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", equalTo(secondMedia)))
                .andExpect(jsonPath("$[0].position", equalTo(0)))
                .andExpect(jsonPath("$[1].position", equalTo(1)));

        mockMvc.perform(get("/api/v1/admin/products/{productId}/media", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].primary", equalTo(true)));
        mockMvc.perform(get("/api/v1/admin/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(3)));
    }

    @Test
    void categoryAssignmentReplacementAndPrimarySelectionWork() throws Exception {
        String productId = createProduct("category-product");
        String firstCategory = createCategory("first-category");
        String secondCategory = createCategory("second-category");

        mockMvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + firstCategory
                                                + "\",\""
                                                + secondCategory
                                                + "\"],\"primaryCategoryId\":\""
                                                + firstCategory
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                        productId,
                                        secondCategory)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", equalTo(secondCategory)))
                .andExpect(jsonPath("$[0].primary", equalTo(true)));

        mockMvc.perform(get("/api/v1/admin/products/{productId}/categories", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
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

    private String createMedia(String productId, String storageKey, int position, boolean primary)
            throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"" + position + "\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\""
                                                + (storageKey.endsWith(".mp4") ? "VIDEO" : "IMAGE")
                                                + "\",\"storageKey\":\""
                                                + storageKey
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
}
