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

package com.titan.productservice.presentation;

import static org.hamcrest.Matchers.equalTo;
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
class CategoryControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    @Test
    void categoryCrudHierarchyAndStatusWork() throws Exception {
        String root = create("root", "Root", null);
        String child = create("child", "Child", root);
        String grandchild = create("grandchild", "Grandchild", child);

        mockMvc.perform(get("/api/v1/categories/{id}/children", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/categories/{id}/descendants", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category.id", equalTo(root)))
                .andExpect(jsonPath("$[0].children[0].category.id", equalTo(child)));
        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}/status", child)
                                .queryParam("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));
        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}", child)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Updated Child\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Updated Child")));
        mockMvc.perform(delete("/api/v1/admin/categories/{id}", grandchild))
                .andExpect(status().isNoContent());
    }

    @Test
    void moveRejectsCyclesAndMissingParents() throws Exception {
        String root = create("cycle-root", "Root", null);
        String child = create("cycle-child", "Child", root);
        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", root)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + child + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", child)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingAndDeletionRestrictionsWork() throws Exception {
        String root = create("restricted-root", "Restricted", null);
        create("restricted-child", "Child", root);
        mockMvc.perform(
                        get("/api/v1/admin/categories")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .queryParam("name", "Restricted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(delete("/api/v1/admin/categories/{id}", root))
                .andExpect(status().isConflict());
    }

    private String create(String slug, String name, String parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\""
                                                + name
                                                + "\",\"slug\":\""
                                                + slug
                                                + "\",\"parentId\":"
                                                + parent
                                                + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }
}
