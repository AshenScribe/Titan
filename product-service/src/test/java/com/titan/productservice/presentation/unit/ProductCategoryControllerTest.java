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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.data.exception.DuplicatePrimaryCategoryException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.domain.service.CategoryService;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.ProductCategoryController;
import com.titan.productservice.presentation.dto.ProductCategoryAssignRequest;
import com.titan.productservice.presentation.dto.ProductCategoryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductCategoryController.class)
@Import(ApiExceptionController.class)
class ProductCategoryControllerTest {

    private static final String VERSION = "\"0\"";

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService products;

    @Test
    void replaceDelegatesAndReturnsAssignments() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.replaceCategories(eq(productId), any(), eq(0L)))
                .thenReturn(List.of(response(categoryId, true)));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", productId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"categoryIds\":[\""
                                                + categoryId
                                                + "\"],\"primaryCategoryId\":\""
                                                + categoryId
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].primary").value(true));

        verify(products).replaceCategories(eq(productId), any(), eq(0L));
    }

    @Test
    void replaceRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void replaceRejectsNullCategoryIdsWith400() throws Exception {
        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", UUID.randomUUID())
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void replaceRejectsInvalidIfMatchWith422() throws Exception {
        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", UUID.randomUUID())
                                .header("If-Match", "notanumber")
                                .contentType(APPLICATION_JSON)
                                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message")
                                .value("If-Match must contain a numeric product version"));

        verifyNoInteractions(products);
    }

    @Test
    void replaceMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.replaceCategories(eq(productId), any(), anyLong()))
                .thenThrow(new ProductConcurrentModificationException("stale"));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", productId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceMapsForeignKeyViolationTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.replaceCategories(eq(productId), any(), anyLong()))
                .thenThrow(new ProductForeignKeyViolationException(null));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", productId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Referenced resource does not exist"));
    }

    @Test
    void replaceReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.replaceCategories(eq(productId), any(), anyLong()))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/categories", productId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignDelegatesAndReturns201() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.assignCategory(
                        eq(productId),
                        eq(categoryId),
                        any(ProductCategoryAssignRequest.class),
                        eq(0L)))
                .thenReturn(List.of(response(categoryId, true)));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void assignMapsDuplicatePrimaryCategoryTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.assignCategory(eq(productId), eq(categoryId), any(), anyLong()))
                .thenThrow(new DuplicatePrimaryCategoryException(null));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"primary\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product already has a primary category"));
    }

    @Test
    void assignMapsConflictTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.assignCategory(eq(productId), eq(categoryId), any(), anyLong()))
                .thenThrow(new CategoryService.ConflictException("Category is already assigned"));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category is already assigned"));
    }

    @Test
    void assignRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        UUID.randomUUID(),
                                        UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void assignRejectsNonUuidCategoryWith400() throws Exception {
        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        UUID.randomUUID(),
                                        "not-a-uuid")
                                .header("If-Match", VERSION)
                                .contentType(APPLICATION_JSON)
                                .content("{\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void removeDelegatesAndReturns204() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION))
                .andExpect(status().isNoContent());

        verify(products).removeCategory(productId, categoryId, 0L);
    }

    @Test
    void removeReturns404WhenAssignmentMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        doThrow(new ProductService.ResourceNotFoundException("Category assignment not found"))
                .when(products)
                .removeCategory(productId, categoryId, 0L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        doThrow(new ProductConcurrentModificationException("stale"))
                .when(products)
                .removeCategory(productId, categoryId, 0L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void primaryDelegatesAndReturnsList() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.setPrimaryCategory(productId, categoryId, 0L))
                .thenReturn(List.of(response(categoryId, true)));

        mvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].primary").value(true));

        verify(products).setPrimaryCategory(productId, categoryId, 0L);
    }

    @Test
    void primaryReturns404WhenAssignmentMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(products.setPrimaryCategory(productId, categoryId, 0L))
                .thenThrow(
                        new ProductService.ResourceNotFoundException(
                                "Category assignment not found"));

        mvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                        productId,
                                        categoryId)
                                .header("If-Match", VERSION))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDelegatesAndReturnsAssignments() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(products.listCategories(productId))
                .thenReturn(List.of(response(first, true), response(second, false)));

        mvc.perform(get("/api/v1/admin/products/{productId}/categories", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].id").value(second.toString()))
                .andExpect(jsonPath("$[1].primary").value(false));

        verify(products).listCategories(productId);
    }

    @Test
    void listReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listCategories(productId))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/admin/products/{productId}/categories", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsEmptyArrayWhenNoAssignments() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listCategories(productId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/products/{productId}/categories", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void primaryRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        put(
                                "/api/v1/admin/products/{productId}/categories/{categoryId}/primary",
                                UUID.randomUUID(),
                                UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    private static ProductCategoryResponse response(UUID categoryId, boolean primary) {
        return new ProductCategoryResponse(categoryId, "Category", "category", primary);
    }
}
