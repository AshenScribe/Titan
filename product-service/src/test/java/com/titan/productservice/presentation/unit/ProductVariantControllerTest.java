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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.data.entity.ProductVariantEntity;
import com.titan.productservice.data.exception.DuplicateVariantBarcodeException;
import com.titan.productservice.data.exception.DuplicateVariantSkuException;
import com.titan.productservice.data.exception.InvalidCurrencyException;
import com.titan.productservice.data.exception.InvalidVariantPriceException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.ProductVariantController;
import com.titan.productservice.presentation.dto.ProductVariantCreateRequest;
import com.titan.productservice.presentation.dto.ProductVariantUpdateRequest;
import java.math.BigDecimal;
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

@WebMvcTest(ProductVariantController.class)
@Import(ApiExceptionController.class)
class ProductVariantControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService products;

    @Test
    void createReturns201() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(ProductVariantCreateRequest.class), eq(0L)))
                .thenReturn(variantEntity(variantId, productId, "SKU-1", "ACTIVE", 0L));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(variantId.toString()))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createMapsDuplicateSkuTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new DuplicateVariantSkuException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Variant SKU already exists"));
    }

    @Test
    void createMapsDuplicateBarcodeTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new DuplicateVariantBarcodeException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"barcode\":\"BAR-1\","
                                                + "\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Variant barcode already exists"));
    }

    @Test
    void createMapsInvalidPriceTo422() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new InvalidVariantPriceException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Variant price is invalid"));
    }

    @Test
    void createMapsInvalidCurrencyTo422() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new InvalidCurrencyException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message").value("Currency must be a 3-letter ISO 4217 code"));
    }

    @Test
    void createMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new ProductConcurrentModificationException("stale"));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"99\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void createReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createVariant(eq(productId), any(), anyLong()))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsBlankSkuWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"sku\":\"\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsMissingPriceWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"sku\":\"SKU-1\",\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsNegativePriceWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":-1.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsInvalidCurrencyPatternWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"inr\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsInvalidIfMatchWith422() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                                .header("If-Match", "notanumber")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"sku\":\"SKU-1\",\"price\":10.00,\"currency\":\"INR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message")
                                .value("If-Match must contain a numeric product version"));

        verifyNoInteractions(products);
    }

    @Test
    void listReturnsVariants() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(products.listVariants(productId))
                .thenReturn(
                        List.of(
                                variantEntity(first, productId, "SKU-1", "ACTIVE", 0L),
                                variantEntity(second, productId, "SKU-2", "ACTIVE", 0L)));

        mvc.perform(get("/api/v1/admin/products/{productId}/variants", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.toString()))
                .andExpect(jsonPath("$[1].id").value(second.toString()));

        verify(products).listVariants(productId);
    }

    @Test
    void listReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listVariants(productId))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/admin/products/{productId}/variants", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsEmptyArrayWhenNoVariants() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listVariants(productId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/products/{productId}/variants", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getReturnsVariant() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.getVariant(productId, variantId))
                .thenReturn(variantEntity(variantId, productId, "SKU-1", "ACTIVE", 0L));

        mvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                productId,
                                variantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variantId.toString()));

        verify(products).getVariant(productId, variantId);
    }

    @Test
    void getReturns404WhenVariantMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.getVariant(productId, variantId))
                .thenThrow(new ProductService.ResourceNotFoundException("Variant not found"));

        mvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                productId,
                                variantId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDelegatesAndReturnsVariant() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.updateVariant(
                        eq(productId),
                        eq(variantId),
                        any(ProductVariantUpdateRequest.class),
                        eq(1L)))
                .thenReturn(variantEntity(variantId, productId, "SKU-1", "ACTIVE", 1L));

        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"price\":20.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variantId.toString()));

        verify(products)
                .updateVariant(
                        eq(productId),
                        eq(variantId),
                        any(ProductVariantUpdateRequest.class),
                        eq(1L));
    }

    @Test
    void updateMapsDuplicateSkuTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.updateVariant(eq(productId), eq(variantId), any(), anyLong()))
                .thenThrow(new DuplicateVariantSkuException(null));

        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"sku\":\"TAKEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Variant SKU already exists"));
    }

    @Test
    void updateReturns404WhenVariantMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.updateVariant(eq(productId), eq(variantId), any(), anyLong()))
                .thenThrow(new ProductService.ResourceNotFoundException("Variant not found"));

        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"price\":20.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidPriceWith400() throws Exception {
        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        UUID.randomUUID(),
                                        UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"price\":-1.00}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void updateRequiresIfMatchWith400() throws Exception {
        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        UUID.randomUUID(),
                                        UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"price\":20.00}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        verify(products).deleteVariant(productId, variantId, 1L);
    }

    @Test
    void deleteReturns404WhenVariantMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        doThrow(new ProductService.ResourceNotFoundException("Variant not found"))
                .when(products)
                .deleteVariant(productId, variantId, 1L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        doThrow(new ProductConcurrentModificationException("stale"))
                .when(products)
                .deleteVariant(productId, variantId, 1L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void activateDelegatesAndReturnsActiveVariant() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.setVariantStatus(productId, variantId, true, 1L))
                .thenReturn(variantEntity(variantId, productId, "SKU-1", "ACTIVE", 1L));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/activate",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(products).setVariantStatus(productId, variantId, true, 1L);
    }

    @Test
    void activateMapsConflictTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.setVariantStatus(productId, variantId, true, 1L))
                .thenThrow(
                        new com.titan.productservice.domain.exception.ConflictException(
                                "Illegal state"));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/activate",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateDelegatesAndReturnsInactiveVariant() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.setVariantStatus(productId, variantId, false, 1L))
                .thenReturn(variantEntity(variantId, productId, "SKU-1", "INACTIVE", 1L));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(products).setVariantStatus(productId, variantId, false, 1L);
    }

    @Test
    void deactivateReturns404WhenVariantMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.setVariantStatus(productId, variantId, false, 1L))
                .thenThrow(new ProductService.ResourceNotFoundException("Variant not found"));

        mvc.perform(
                        post(
                                        "/api/v1/admin/products/{productId}/variants/{variantId}/deactivate",
                                        productId,
                                        variantId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "123"})
    void getRejectsNonUuidProductPathWith400(String productId) throws Exception {
        mvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                productId,
                                UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRejectsNonUuidVariantPathWith400() throws Exception {
        mvc.perform(
                        get(
                                "/api/v1/admin/products/{productId}/variants/{variantId}",
                                UUID.randomUUID(),
                                "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    private static ProductVariantEntity variantEntity(
            UUID id, UUID productId, String sku, String status, long version) {
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setId(id);
        variant.setProductId(productId);
        variant.setSku(sku);
        variant.setName("Variant " + sku);
        variant.setPrice(new BigDecimal("10.00"));
        variant.setCurrency("INR");
        variant.setStatus(status);
        variant.setVersion(version);
        variant.setAttributes(Map.of());
        variant.setCreatedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        variant.setUpdatedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        return variant;
    }
}
