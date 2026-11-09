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

package com.titan.productservice.presentation.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.exception.CategoryHierarchyCycleException;
import com.titan.productservice.data.exception.DuplicateCategorySlugException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.domain.service.CategoryService;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.CategoryController;
import com.titan.productservice.presentation.dto.CategoryCreateRequest;
import com.titan.productservice.presentation.dto.CategoryListQuery;
import com.titan.productservice.presentation.dto.CategoryParentRequest;
import com.titan.productservice.presentation.dto.CategoryResponse;
import com.titan.productservice.presentation.dto.CategoryTreeNode;
import com.titan.productservice.presentation.dto.CategoryUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import(ApiExceptionController.class)
class CategoryControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CategoryService categories;

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.create(any(CategoryCreateRequest.class)))
                .thenReturn(categoryEntity(id, "root", null, true));

        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Root\",\"slug\":\"root\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.slug").value("root"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createMapsDuplicateSlugTo409() throws Exception {
        when(categories.create(any())).thenThrow(new DuplicateCategorySlugException(null));

        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category slug already exists"));
    }

    @Test
    void createMapsHierarchyCycleTo409() throws Exception {
        when(categories.create(any())).thenThrow(new CategoryHierarchyCycleException(null));

        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category hierarchy cycle detected"));
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"\",\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(categories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPPER", "with space", "trailing-", "-leading", "under_score"})
    void createRejectsInvalidSlugWith400(String slug) throws Exception {
        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"OK\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categories);
    }

    @Test
    void createRejectsNegativeSortOrderWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/categories")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"OK\",\"slug\":\"ok\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categories);
    }

    @Test
    void listReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.list(any(CategoryListQuery.class)))
                .thenReturn(
                        new PageResponse<>(
                                List.of(CategoryResponse.from(categoryEntity(id, "b", null, true))),
                                0,
                                20,
                                1,
                                1));

        mvc.perform(get("/api/v1/admin/categories").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPassesFiltersThrough() throws Exception {
        when(categories.list(any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(
                        get("/api/v1/admin/categories")
                                .param("page", "1")
                                .param("size", "5")
                                .param("name", "Men")
                                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void listReturns422WhenServiceRejectsPagination() throws Exception {
        when(categories.list(any()))
                .thenThrow(new IllegalArgumentException("page must be non-negative"));

        mvc.perform(get("/api/v1/admin/categories").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("page must be non-negative"));
    }

    @Test
    void getReturnsCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.get(id)).thenReturn(categoryEntity(id, "b", null, true));

        mvc.perform(get("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(categories).get(id);
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.get(id))
                .thenThrow(
                        new ProductService.ResourceNotFoundException("Category not found: " + id));

        mvc.perform(get("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found: " + id));
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/admin/categories/not-a-uuid")).andExpect(status().isBadRequest());

        verify(categories, never()).get(any());
    }

    @Test
    void updateDelegatesAndReturnsCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.update(eq(id), any(CategoryUpdateRequest.class)))
                .thenReturn(categoryEntity(id, "updated", null, true));

        mvc.perform(
                        patch("/api/v1/admin/categories/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("updated"));

        verify(categories).update(eq(id), any(CategoryUpdateRequest.class));
    }

    @Test
    void updateMapsDuplicateSlugTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.update(eq(id), any())).thenThrow(new DuplicateCategorySlugException(null));

        mvc.perform(
                        patch("/api/v1/admin/categories/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category slug already exists"));
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.update(eq(id), any()))
                .thenThrow(new ProductService.ResourceNotFoundException("Category not found"));

        mvc.perform(
                        patch("/api/v1/admin/categories/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidSlugWith400() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(
                        patch("/api/v1/admin/categories/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"UPPER\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categories);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/admin/categories/{id}", id)).andExpect(status().isNoContent());

        verify(categories).delete(id);
    }

    @Test
    void deleteMapsForeignKeyViolationTo409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductForeignKeyViolationException(null)).when(categories).delete(id);

        mvc.perform(delete("/api/v1/admin/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Referenced resource does not exist"));
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductService.ResourceNotFoundException("Category not found: " + id))
                .when(categories)
                .delete(id);

        mvc.perform(delete("/api/v1/admin/categories/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void statusDeactivatesAndReturnsCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.setStatus(id, false)).thenReturn(categoryEntity(id, "b", null, false));

        mvc.perform(patch("/api/v1/admin/categories/{id}/status", id).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(categories).setStatus(id, false);
    }

    @Test
    void statusRequiresActiveParamWith400() throws Exception {
        mvc.perform(patch("/api/v1/admin/categories/{id}/status", UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categories);
    }

    @Test
    void statusReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.setStatus(eq(id), any(Boolean.class)))
                .thenThrow(new ProductService.ResourceNotFoundException("Category not found"));

        mvc.perform(patch("/api/v1/admin/categories/{id}/status", id).param("active", "true"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveDelegatesAndReturnsCategory() throws Exception {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(categories.move(eq(id), any(CategoryParentRequest.class)))
                .thenReturn(categoryEntity(id, "moved", parentId, true));

        mvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"parentId\":\"" + parentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        verify(categories).move(eq(id), any(CategoryParentRequest.class));
    }

    @Test
    void moveMapsCycleTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.move(eq(id), any())).thenThrow(new CategoryHierarchyCycleException(null));

        mvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"parentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category hierarchy cycle detected"));
    }

    @Test
    void moveReturns404WhenParentMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.move(eq(id), any()))
                .thenThrow(new ProductService.ResourceNotFoundException("Category not found"));

        mvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"parentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveAllowsNullParent() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.move(eq(id), any())).thenReturn(categoryEntity(id, "root", null, true));

        mvc.perform(
                        put("/api/v1/admin/categories/{id}/parent", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"parentId\":null}"))
                .andExpect(status().isOk());

        verify(categories).move(eq(id), any());
    }

    @Test
    void treeReturnsHierarchy() throws Exception {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        CategoryTreeNode child =
                new CategoryTreeNode(
                        CategoryResponse.from(categoryEntity(childId, "child", rootId, true)),
                        List.of());
        CategoryTreeNode root =
                new CategoryTreeNode(
                        CategoryResponse.from(categoryEntity(rootId, "root", null, true)),
                        List.of(child));
        when(categories.tree()).thenReturn(List.of(root));

        mvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category.id").value(rootId.toString()))
                .andExpect(jsonPath("$[0].children[0].category.id").value(childId.toString()));
    }

    @Test
    void childrenReturnsDirectChildren() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(categories.children(parentId))
                .thenReturn(
                        List.of(
                                CategoryResponse.from(
                                        categoryEntity(childId, "child", parentId, true))));

        mvc.perform(get("/api/v1/categories/{id}/children", parentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(childId.toString()));

        verify(categories).children(parentId);
    }

    @Test
    void childrenReturns404WhenParentMissing() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(categories.children(parentId))
                .thenThrow(new ProductService.ResourceNotFoundException("Category not found"));

        mvc.perform(get("/api/v1/categories/{id}/children", parentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void descendantsReturnsAll() throws Exception {
        UUID rootId = UUID.randomUUID();
        UUID grandchildId = UUID.randomUUID();
        when(categories.descendants(rootId))
                .thenReturn(
                        List.of(
                                CategoryResponse.from(
                                        categoryEntity(grandchildId, "gc", rootId, true))));

        mvc.perform(get("/api/v1/categories/{id}/descendants", rootId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(grandchildId.toString()));

        verify(categories).descendants(rootId);
    }

    private static CategoryEntity categoryEntity(
            UUID id, String slug, UUID parentId, boolean active) {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return new CategoryEntity(
                id, parentId, "Category " + slug, slug, null, Map.of(), 0, active, now, now);
    }
}
