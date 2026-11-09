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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.PublicProductController;
import com.titan.productservice.presentation.dto.BrandResponse;
import com.titan.productservice.presentation.dto.CategoryResponse;
import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.presentation.dto.PublicMediaResponse;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import com.titan.productservice.presentation.dto.PublicProductResponse;
import com.titan.productservice.presentation.dto.PublicVariantResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicProductController.class)
@Import(ApiExceptionController.class)
class PublicProductControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService products;

    @Test
    void listReturnsPage() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicList(any(PublicProductListQuery.class)))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPassesFiltersThrough() throws Exception {
        when(products.publicList(any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(
                        get("/api/v1/products")
                                .param("page", "1")
                                .param("size", "5")
                                .param("q", "shirt")
                                .param("minPrice", "10")
                                .param("maxPrice", "100")
                                .param("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        verify(products).publicList(any());
    }

    @Test
    void listReturns422ForInvalidPagination() throws Exception {
        when(products.publicList(any()))
                .thenThrow(new IllegalArgumentException("page must be non-negative"));

        mvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("page must be non-negative"));
    }

    @Test
    void searchDelegates() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicSearch(any()))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products/search").param("q", "shirt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()));

        verify(products).publicSearch(any());
    }

    @Test
    void featuredDelegates() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.featured(any()))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()));

        verify(products).featured(any());
    }

    @Test
    void newArrivalsDelegates() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.newArrivals(any()))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products/new-arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()));

        verify(products).newArrivals(any());
    }

    @Test
    void byBrandDelegates() throws Exception {
        UUID brandId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(products.byBrand(eq(brandId), any()))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products/brands/{brandId}", brandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()));

        verify(products).byBrand(eq(brandId), any());
    }

    @Test
    void byBrandRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/products/brands/not-a-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void byCategoryDelegates() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(products.byCategory(eq(categoryId), any()))
                .thenReturn(
                        new PageResponse<>(List.of(publicProduct(productId, "p")), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/products/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId.toString()));

        verify(products).byCategory(eq(categoryId), any());
    }

    @Test
    void byCategoryRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/products/categories/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void getReturnsProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicGet(productId)).thenReturn(publicProduct(productId, "p"));

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()));

        verify(products).publicGet(productId);
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicGet(productId))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/products/not-a-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void getBySlugReturnsProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicGetBySlug("classic-shirt"))
                .thenReturn(publicProduct(productId, "classic-shirt"));

        mvc.perform(get("/api/v1/products/slug/{slug}", "classic-shirt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.slug").value("classic-shirt"));

        verify(products).publicGetBySlug("classic-shirt");
    }

    @Test
    void getBySlugReturns404WhenMissing() throws Exception {
        when(products.publicGetBySlug("missing"))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/products/slug/{slug}", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantsReturnsActiveVariants() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.publicVariants(productId)).thenReturn(List.of(publicVariant(variantId)));

        mvc.perform(get("/api/v1/products/{productId}/variants", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(variantId.toString()));

        verify(products).publicVariants(productId);
    }

    @Test
    void variantsReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicVariants(productId))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/products/{productId}/variants", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantsReturnsEmptyList() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.publicVariants(productId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/products/{productId}/variants", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void variantReturnsActiveVariant() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.publicVariant(productId, variantId)).thenReturn(publicVariant(variantId));

        mvc.perform(get("/api/v1/products/{productId}/variants/{variantId}", productId, variantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variantId.toString()));

        verify(products).publicVariant(productId, variantId);
    }

    @Test
    void variantReturns404WhenMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(products.publicVariant(productId, variantId))
                .thenThrow(
                        new ProductService.ResourceNotFoundException("Active variant not found"));

        mvc.perform(get("/api/v1/products/{productId}/variants/{variantId}", productId, variantId))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantRejectsNonUuidVariantPathWith400() throws Exception {
        mvc.perform(
                        get(
                                "/api/v1/products/{productId}/variants/{variantId}",
                                UUID.randomUUID(),
                                "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    private static PublicProductResponse publicProduct(UUID id, String slug) {
        return new PublicProductResponse(
                id,
                "Product " + slug,
                slug,
                null,
                Map.of(),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                new BrandResponse(
                        UUID.randomUUID(),
                        "Brand",
                        "brand",
                        null,
                        null,
                        null,
                        Map.of(),
                        true,
                        null,
                        null),
                List.of(
                        new CategoryResponse(
                                UUID.randomUUID(),
                                null,
                                "Category",
                                "category",
                                null,
                                0,
                                true,
                                Map.of(),
                                null,
                                null)),
                List.of(publicVariant(UUID.randomUUID())),
                List.of(
                        new PublicMediaResponse(
                                UUID.randomUUID(), "IMAGE", "media/a.webp", "alt", 0, true)));
    }

    private static PublicVariantResponse publicVariant(UUID id) {
        return new PublicVariantResponse(
                id,
                "Variant",
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                "INR",
                Map.of("size", "M"));
    }
}
