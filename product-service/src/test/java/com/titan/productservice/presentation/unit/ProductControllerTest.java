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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.exception.ActiveProductRequiresVariantException;
import com.titan.productservice.data.exception.DuplicateProductSlugException;
import com.titan.productservice.data.exception.InvalidProductStatusTransitionException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.ProductController;
import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.presentation.dto.ProductCreateRequest;
import com.titan.productservice.presentation.dto.ProductResponse;
import com.titan.productservice.presentation.dto.ProductUpdateRequest;
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

@WebMvcTest(ProductController.class)
@Import(ApiExceptionController.class)
class ProductControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService products;

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.create(any(ProductCreateRequest.class)))
                .thenReturn(productEntity(id, "test-product", "DRAFT", 0L));

        mvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Test\",\"slug\":\"test-product\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.slug").value("test-product"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createMapsDuplicateSlugTo409() throws Exception {
        when(products.create(any())).thenThrow(new DuplicateProductSlugException(null));

        mvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product slug already exists"));
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"\",\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(products);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPPER", "with space", "trailing-", "-leading", "under_score"})
    void createRejectsInvalidSlugWith400(String slug) throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"OK\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void listReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.list(any()))
                .thenReturn(
                        new PageResponse<>(
                                List.of(ProductResponse.from(productEntity(id, "b", "DRAFT", 0L))),
                                0,
                                20,
                                1,
                                1));

        mvc.perform(get("/api/v1/admin/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPassesFiltersThrough() throws Exception {
        when(products.list(any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(
                        get("/api/v1/admin/products")
                                .param("page", "1")
                                .param("size", "5")
                                .param("status", "DRAFT")
                                .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        verify(products).list(any());
    }

    @Test
    void listReturns422WhenServiceRejectsPagination() throws Exception {
        when(products.list(any()))
                .thenThrow(new IllegalArgumentException("page must be non-negative"));

        mvc.perform(get("/api/v1/admin/products").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("page must be non-negative"));
    }

    @Test
    void getReturnsProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.getIncludingDeleted(id)).thenReturn(productEntity(id, "b", "DRAFT", 0L));

        mvc.perform(get("/api/v1/admin/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(products).getIncludingDeleted(id);
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.getIncludingDeleted(id))
                .thenThrow(
                        new ProductService.ResourceNotFoundException("Product not found: " + id));

        mvc.perform(get("/api/v1/admin/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/admin/products/not-a-uuid")).andExpect(status().isBadRequest());

        verify(products, never()).getIncludingDeleted(any());
    }

    @Test
    void updateDelegatesAndReturnsProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.update(eq(id), any(ProductUpdateRequest.class), eq(0L)))
                .thenReturn(productEntity(id, "updated", "DRAFT", 1L));

        mvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("updated"))
                .andExpect(jsonPath("$.version").value(1));

        verify(products).update(eq(id), any(ProductUpdateRequest.class), eq(0L));
    }

    @Test
    void updateMapsDuplicateSlugTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.update(eq(id), any(), anyLong()))
                .thenThrow(new DuplicateProductSlugException(null));

        mvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product slug already exists"));
    }

    @Test
    void updateMapsConcurrentModificationTo412() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.update(eq(id), any(), anyLong()))
                .thenThrow(new ProductConcurrentModificationException("stale"));

        mvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"99\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.update(eq(id), any(), anyLong()))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(
                        patch("/api/v1/admin/products/{id}", id)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        patch("/api/v1/admin/products/{id}", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void updateRejectsInvalidIfMatchWith422() throws Exception {
        mvc.perform(
                        patch("/api/v1/admin/products/{id}", UUID.randomUUID())
                                .header("If-Match", "notanumber")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message")
                                .value("If-Match must contain a numeric product version"));

        verifyNoInteractions(products);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());

        verify(products).softDelete(id, 0L);
    }

    @Test
    void deleteMapsConcurrentModificationTo412() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductConcurrentModificationException("stale"))
                .when(products)
                .softDelete(id, 0L);

        mvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProductService.ResourceNotFoundException("Product not found"))
                .when(products)
                .softDelete(id, 0L);

        mvc.perform(delete("/api/v1/admin/products/{id}", id).header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishDelegatesAndReturnsActiveProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "ACTIVE", 0L))
                .thenReturn(productEntity(id, "b", "ACTIVE", 1L));

        mvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(products).transition(id, "ACTIVE", 0L);
    }

    @Test
    void publishMapsActiveRequiresVariantTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "ACTIVE", 0L))
                .thenThrow(new ActiveProductRequiresVariantException(null));

        mvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("Active product must retain at least one active variant"));
    }

    @Test
    void publishMapsInvalidTransitionTo422() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "ACTIVE", 0L))
                .thenThrow(new InvalidProductStatusTransitionException(null));

        mvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid product status transition"));
    }

    @Test
    void publishReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "ACTIVE", 0L))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(post("/api/v1/admin/products/{id}/publish", id).header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateDelegatesAndReturnsActiveProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "ACTIVE", 0L))
                .thenReturn(productEntity(id, "b", "ACTIVE", 1L));

        mvc.perform(post("/api/v1/admin/products/{id}/activate", id).header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(products).transition(id, "ACTIVE", 0L);
    }

    @Test
    void unpublishDelegatesAndReturnsDraftProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "DRAFT", 1L)).thenReturn(productEntity(id, "b", "DRAFT", 2L));

        mvc.perform(post("/api/v1/admin/products/{id}/unpublish", id).header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(products).transition(id, "DRAFT", 1L);
    }

    @Test
    void deactivateDelegatesAndReturnsInactiveProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(products.transition(id, "INACTIVE", 0L))
                .thenReturn(productEntity(id, "b", "INACTIVE", 1L));

        mvc.perform(post("/api/v1/admin/products/{id}/deactivate", id).header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(products).transition(id, "INACTIVE", 0L);
    }

    @Test
    void transitionRequiresIfMatchWith400() throws Exception {
        mvc.perform(post("/api/v1/admin/products/{id}/publish", UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    private static ProductEntity productEntity(UUID id, String slug, String status, long version) {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return new ProductEntity(
                id,
                null,
                "Product " + slug,
                slug,
                null,
                status,
                Map.of(),
                version,
                null,
                null,
                now,
                now);
    }
}
