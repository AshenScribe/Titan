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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.exception.DuplicatePrimaryMediaException;
import com.titan.productservice.data.exception.InvalidMediaPositionException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.ProductMediaController;
import com.titan.productservice.presentation.dto.ProductMediaCreateRequest;
import com.titan.productservice.presentation.dto.ProductMediaReorderRequest;
import com.titan.productservice.presentation.dto.ProductMediaUpdateRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductMediaController.class)
@Import(ApiExceptionController.class)
class ProductMediaControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService products;

    @Test
    void createReturns201() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.createMedia(eq(productId), any(ProductMediaCreateRequest.class), eq(0L)))
                .thenReturn(mediaEntity(mediaId, productId, "IMAGE", 0, true));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    void createMapsDuplicatePrimaryMediaTo409() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createMedia(eq(productId), any(), anyLong()))
                .thenThrow(new DuplicatePrimaryMediaException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product already has a primary media item"));
    }

    @Test
    void createMapsInvalidPositionTo422() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createMedia(eq(productId), any(), anyLong()))
                .thenThrow(new InvalidMediaPositionException(null));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Media position must be non-negative"));
    }

    @Test
    void createMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.createMedia(eq(productId), any(), anyLong()))
                .thenThrow(new ProductConcurrentModificationException("stale"));

        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", productId)
                                .header("If-Match", "\"99\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void createRejectsInvalidMediaTypeWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"AUDIO\",\"storageKey\":\"k/1.mp3\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsMissingStorageKeyWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"position\":0,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsNegativePositionWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":-1,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsMissingIfMatchWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void createRejectsInvalidIfMatchWith422() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/products/{productId}/media", UUID.randomUUID())
                                .header("If-Match", "notanumber")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"mediaType\":\"IMAGE\",\"storageKey\":\"k/1.png\","
                                                + "\"position\":0,\"primary\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message")
                                .value("If-Match must contain a numeric product version"));

        verifyNoInteractions(products);
    }

    @Test
    void listReturnsMedia() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.listMedia(productId))
                .thenReturn(List.of(mediaEntity(mediaId, productId, "IMAGE", 0, true)));

        mvc.perform(get("/api/v1/admin/products/{productId}/media", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mediaId.toString()));

        verify(products).listMedia(productId);
    }

    @Test
    void listReturns404WhenProductMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listMedia(productId))
                .thenThrow(new ProductService.ResourceNotFoundException("Product not found"));

        mvc.perform(get("/api/v1/admin/products/{productId}/media", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsEmptyArrayWhenNoMedia() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.listMedia(productId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/products/{productId}/media", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void updateDelegatesAndReturnsMedia() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.updateMedia(
                        eq(productId), eq(mediaId), any(ProductMediaUpdateRequest.class), eq(1L)))
                .thenReturn(mediaEntity(mediaId, productId, "VIDEO", 1, false));

        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"1\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaType\":\"VIDEO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaType").value("VIDEO"));

        verify(products)
                .updateMedia(
                        eq(productId), eq(mediaId), any(ProductMediaUpdateRequest.class), eq(1L));
    }

    @Test
    void updateReturns404WhenMediaMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.updateMedia(eq(productId), eq(mediaId), any(), anyLong()))
                .thenThrow(new ProductService.ResourceNotFoundException("Media not found"));

        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"1\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"altText\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidMediaTypeWith400() throws Exception {
        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        UUID.randomUUID(),
                                        UUID.randomUUID())
                                .header("If-Match", "\"1\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaType\":\"AUDIO\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        verify(products).deleteMedia(productId, mediaId, 1L);
    }

    @Test
    void deleteReturns404WhenMediaMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        doThrow(new ProductService.ResourceNotFoundException("Media not found"))
                .when(products)
                .deleteMedia(productId, mediaId, 1L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        doThrow(new ProductConcurrentModificationException("stale"))
                .when(products)
                .deleteMedia(productId, mediaId, 1L);

        mvc.perform(
                        delete(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void reorderDelegatesAndReturnsMedia() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(products.reorderMedia(eq(productId), any(ProductMediaReorderRequest.class), eq(2L)))
                .thenReturn(
                        List.of(
                                mediaEntity(second, productId, "VIDEO", 0, true),
                                mediaEntity(first, productId, "IMAGE", 1, false)));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", productId)
                                .header("If-Match", "\"2\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaIds\":[\"" + second + "\",\"" + first + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[1].position").value(1));
    }

    @Test
    void reorderRejectsEmptyMediaIdsWith400() throws Exception {
        mvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", UUID.randomUUID())
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void reorderRejectsIncompleteListWith422() throws Exception {
        UUID productId = UUID.randomUUID();
        when(products.reorderMedia(eq(productId), any(), anyLong()))
                .thenThrow(
                        new IllegalArgumentException(
                                "Reorder request must contain every media exactly once"));

        mvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", productId)
                                .header("If-Match", "\"0\"")
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.message")
                                .value("Reorder request must contain every media exactly once"));
    }

    @Test
    void primaryDelegatesAndReturnsMedia() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.setPrimaryMedia(productId, mediaId, 2L))
                .thenReturn(mediaEntity(mediaId, productId, "IMAGE", 0, true));

        mvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"2\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.primary").value(true));

        verify(products).setPrimaryMedia(productId, mediaId, 2L);
    }

    @Test
    void primaryReturns404WhenMediaMissing() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.setPrimaryMedia(productId, mediaId, 0L))
                .thenThrow(new ProductService.ResourceNotFoundException("Media not found"));

        mvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"0\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void primaryMapsConcurrentModificationTo412() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(products.setPrimaryMedia(productId, mediaId, 0L))
                .thenThrow(new ProductConcurrentModificationException("stale"));

        mvc.perform(
                        put(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                        productId,
                                        mediaId)
                                .header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "123"})
    void getRejectsNonUuidProductPathWith400(String productId) throws Exception {
        mvc.perform(get("/api/v1/admin/products/{productId}/media", productId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRequiresIfMatchWith400() throws Exception {
        mvc.perform(
                        patch(
                                        "/api/v1/admin/products/{productId}/media/{mediaId}",
                                        UUID.randomUUID(),
                                        UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"altText\":\"x\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void deleteRequiresIfMatchWith400() throws Exception {
        mvc.perform(
                        delete(
                                "/api/v1/admin/products/{productId}/media/{mediaId}",
                                UUID.randomUUID(),
                                UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void reorderRequiresIfMatchWith400() throws Exception {
        mvc.perform(
                        put("/api/v1/admin/products/{productId}/media/reorder", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"mediaIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    @Test
    void primaryRequiresIfMatchWith400() throws Exception {
        mvc.perform(
                        put(
                                "/api/v1/admin/products/{productId}/media/{mediaId}/primary",
                                UUID.randomUUID(),
                                UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(products);
    }

    private static ProductMediaEntity mediaEntity(
            UUID id, UUID productId, String mediaType, int position, boolean primary) {
        ProductMediaEntity media = new ProductMediaEntity();
        media.setId(id);
        media.setProductId(productId);
        media.setMediaType(mediaType);
        media.setStorageKey("k/" + id + ".bin");
        media.setAltText("alt");
        media.setPosition(position);
        media.setPrimary(primary);
        media.setCreatedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        return media;
    }
}
