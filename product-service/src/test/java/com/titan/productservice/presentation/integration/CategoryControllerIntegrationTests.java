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
    void createPersistsCategoryAndReturns201() throws Exception {
        String id = createCategory("root", "Root", null, null);

        mockMvc.perform(get("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug", equalTo("root")))
                .andExpect(jsonPath("$.name", equalTo("Root")))
                .andExpect(jsonPath("$.active", equalTo(true)));

        assertThat(countRows("categories")).isEqualTo(1);
    }

    @Test
    void createPersistsParentAndSortOrder() throws Exception {
        String parent = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", parent, 5);

        mockMvc.perform(get("/api/v1/admin/categories/{id}", child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId", equalTo(parent)))
                .andExpect(jsonPath("$.sortOrder", equalTo(5)));
    }

    @Test
    void createRejectsDuplicateSlugWith409() throws Exception {
        createCategory("same", "Same", null, null);

        mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Other\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Category slug already exists")));

        assertThat(countRows("categories")).isEqualTo(1);
    }

    @Test
    void createRejectsMissingParentWith409() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"X\",\"slug\":\"x\",\"parentId\":\""
                                                + UUID.randomUUID()
                                                + "\"}"))
                .andExpect(status().isNotFound());

        assertThat(countRows("categories")).isZero();
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \",\"slug\":\"blank\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("Request validation failed")));

        assertThat(countRows("categories")).isZero();
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByNameAndActive() throws Exception {
        createCategory("alpha", "Alpha", null, null);
        createCategory("beta", "Beta", null, null);
        String gamma = createCategory("gamma", "Gamma", null, null);
        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}/status", gamma)
                                .param("active", "false"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/categories").param("name", "Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", equalTo("alpha")));

        mockMvc.perform(get("/api/v1/admin/categories").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listFiltersByParentId() throws Exception {
        String parent = createCategory("parent", "Parent", null, null);
        createCategory("child-a", "Child A", parent, null);
        createCategory("child-b", "Child B", parent, null);

        mockMvc.perform(get("/api/v1/admin/categories").param("parentId", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listRespectsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createCategory("p-" + i, "P " + i, null, null);
        }

        mockMvc.perform(get("/api/v1/admin/categories").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", equalTo(5)))
                .andExpect(jsonPath("$.totalPages", equalTo(3)));
    }

    @Test
    void listReturns422ForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateMutatesFields() throws Exception {
        String id = createCategory("edit-me", "Edit Me", null, null);

        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Edited\",\"slug\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Edited")))
                .andExpect(jsonPath("$.slug", equalTo("edited")));
    }

    @Test
    void updateToExistingSlugReturns409() throws Exception {
        createCategory("taken", "Taken", null, null);
        String other = createCategory("other", "Other", null, null);

        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}", other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Category slug already exists")));
    }

    @Test
    void updateReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesLeafCategory() throws Exception {
        String id = createCategory("delete-me", "Delete Me", null, null);

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/categories/{id}", id)).andExpect(status().isNotFound());
        assertThat(countRows("categories")).isZero();
    }

    @Test
    void deleteReturns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/categories/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsRejectedWhenCategoryHasChildren() throws Exception {
        String parent = createCategory("parent", "Parent", null, null);
        createCategory("child", "Child", parent, null);

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", parent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Referenced resource")));

        assertThat(countRows("categories")).isEqualTo(2);
    }

    @Test
    void deleteIsRejectedWhenReferencedByProduct() throws Exception {
        String categoryId = createCategory("referenced", "Referenced", null, null);
        jdbc.update(
                """
                INSERT INTO products (id, name, slug, status, version)
                VALUES (gen_random_uuid(), 'Product', 'ref-product', 'DRAFT', 0)
                """);
        jdbc.update(
                """
                INSERT INTO product_categories (product_id, category_id, is_primary)
                SELECT id, ?, FALSE FROM products WHERE slug = 'ref-product'
                """,
                UUID.fromString(categoryId));

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", categoryId))
                .andExpect(status().isConflict());

        assertThat(countRows("categories")).isEqualTo(1);
    }

    @Test
    void statusDeactivatesAndActivates() throws Exception {
        String id = createCategory("toggle", "Toggle", null, null);

        mockMvc.perform(patch("/api/v1/admin/categories/{id}/status", id).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));

        mockMvc.perform(patch("/api/v1/admin/categories/{id}/status", id).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(true)));
    }

    @Test
    void statusReturns404ForUnknownId() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/categories/{id}/status", UUID.randomUUID())
                                .param("active", "true"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveReparentsCategory() throws Exception {
        String oldParent = createCategory("old-parent", "Old", null, null);
        String newParent = createCategory("new-parent", "New", null, null);
        String child = createCategory("child", "Child", oldParent, null);

        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", child)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + newParent + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId", equalTo(newParent)));
    }

    @Test
    void moveToNullParentPromotesToRoot() throws Exception {
        String parent = createCategory("parent", "Parent", null, null);
        String child = createCategory("child", "Child", parent, null);

        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", child)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    void moveRejectsCycleWith409() throws Exception {
        String root = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", root, null);

        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", root)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + child + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/categories/{id}", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    void moveReturns404WhenParentMissing() throws Exception {
        String id = createCategory("child", "Child", null, null);

        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void treeReturnsFullHierarchy() throws Exception {
        String root = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", root, null);
        createCategory("grandchild", "Grandchild", child, null);

        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category.id", equalTo(root)))
                .andExpect(jsonPath("$[0].children[0].category.id", equalTo(child)))
                .andExpect(jsonPath("$[0].children[0].children", hasSize(1)));
    }

    @Test
    void childrenReturnsOnlyDirectChildren() throws Exception {
        String root = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", root, null);
        createCategory("grandchild", "Grandchild", child, null);

        mockMvc.perform(get("/api/v1/categories/{id}/children", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", equalTo(child)));
    }

    @Test
    void childrenReturns404ForUnknownParent() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}/children", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void descendantsReturnsWholeSubtree() throws Exception {
        String root = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", root, null);
        createCategory("grandchild", "Grandchild", child, null);
        createCategory("unrelated", "Unrelated", null, null);

        mockMvc.perform(get("/api/v1/categories/{id}/descendants", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void descendantsReturns404ForUnknownParent() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}/descendants", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedCreateDoesNotPersistPartialRow() throws Exception {
        createCategory("dup", "Dup", null, null);

        mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Dup2\",\"slug\":\"dup\"}"))
                .andExpect(status().isConflict());

        assertThat(countRows("categories")).isEqualTo(1);
    }

    @Test
    void failedMoveLeavesParentUnchanged() throws Exception {
        String root = createCategory("root", "Root", null, null);
        String child = createCategory("child", "Child", root, null);

        mockMvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", root)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + child + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/categories/{id}", root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/categories/{id}", child))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId", equalTo(root)));
    }

    private String createCategory(String slug, String name, String parentId, Integer sortOrder)
            throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("{\"name\":\"").append(name).append("\",\"slug\":\"").append(slug).append("\"");
        if (parentId != null) {
            body.append(",\"parentId\":\"").append(parentId).append("\"");
        }
        if (sortOrder != null) {
            body.append(",\"sortOrder\":").append(sortOrder);
        }
        body.append("}");

        return mockMvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
