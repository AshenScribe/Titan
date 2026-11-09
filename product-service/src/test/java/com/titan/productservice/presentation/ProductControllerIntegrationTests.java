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
import com.titan.productservice.config.*;
import com.titan.productservice.controller.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.service.*;
import com.titan.productservice.util.*;
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
    void createsDraftAndRetrievesProduct() throws Exception {
        String response =
                mockMvc.perform(
                                post("/api/v1/admin/products")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"name":"Winter Jacket","slug":"winter-jacket","description":"Warm"}
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status", equalTo("DRAFT")))
                        .andExpect(jsonPath("$.version", equalTo(0)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/admin/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("winter-jacket")));
    }

    @Test
    void listsWithPaginationAndFilters() throws Exception {
        create("first-product", "First Product");
        create("second-product", "Second Product");

        mockMvc.perform(
                        get("/api/v1/admin/products")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .queryParam("name", "Second"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("second-product")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    @Test
    void updateRequiresMatchingAggregateVersion() throws Exception {
        String id = create("editable-product", "Editable Product");

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Updated Product\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Updated Product")))
                .andExpect(jsonPath("$.version", equalTo(1)));

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Stale Product\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void softDeleteHidesProductWhenFilteringNonDeletedProducts() throws Exception {
        String id = create("deleted-product", "Deleted Product");
        mockMvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/admin/products")
                                .queryParam("deleted", "false")
                                .queryParam("slug", "deleted-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void publishingWithoutActiveVariantFails() throws Exception {
        String id = create("empty-product", "Empty Product");
        mockMvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    private String create(String slug, String name) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }
}
