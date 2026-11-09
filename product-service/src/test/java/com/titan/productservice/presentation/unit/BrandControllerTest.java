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

import com.titan.productservice.data.entity.BrandEntity;
import com.titan.productservice.data.exception.DuplicateBrandSlugException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.data.exception.ProductStringTruncationException;
import com.titan.productservice.domain.exception.ConflictException;
import com.titan.productservice.domain.service.BrandService;
import com.titan.productservice.domain.service.ProductService;
import com.titan.productservice.presentation.controller.ApiExceptionController;
import com.titan.productservice.presentation.controller.BrandController;
import com.titan.productservice.presentation.dto.BrandCreateRequest;
import com.titan.productservice.presentation.dto.BrandResponse;
import com.titan.productservice.presentation.dto.BrandUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandController.class)
@Import(ApiExceptionController.class)
class BrandControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private BrandService brands;

    @Test
    void createDelegatesAndReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.create(any(BrandCreateRequest.class)))
                .thenReturn(brandEntity(id, "titan-apparel", true));

        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Titan Apparel","slug":"titan-apparel"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.slug").value("titan-apparel"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createMapsDuplicateBrandSlugTo409() throws Exception {
        when(brands.create(any())).thenThrow(new DuplicateBrandSlugException(null));

        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Brand slug already exists"));
    }

    @Test
    void createMapsConflictExceptionTo409() throws Exception {
        when(brands.create(any())).thenThrow(new ConflictException("Brand already exists"));

        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"slug\":\"same\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Brand already exists"));
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"\",\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(brands);
    }

    @Test
    void createRejectsMissingNameWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPPER", "with space", "trailing-", "-leading", "under_score", ""})
    void createRejectsInvalidSlugWith400(String slug) throws Exception {
        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"OK\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brands);
    }

    @Test
    void createRejectsNameOver150CharsWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"" + "x".repeat(151) + "\",\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brands);
    }

    @Test
    void createMapsStringTruncationTo400() throws Exception {
        when(brands.create(any())).thenThrow(new ProductStringTruncationException(null));

        mvc.perform(
                        post("/api/v1/admin/brands")
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"OK\",\"slug\":\"ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A field value is too long"));
    }

    @Test
    void listDelegatesAndReturnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.list(any()))
                .thenReturn(
                        new PageResponse<>(
                                List.of(BrandResponse.from(brandEntity(id, "b", true))),
                                0,
                                20,
                                1,
                                1));

        mvc.perform(get("/api/v1/admin/brands").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(brands).list(any());
    }

    @Test
    void listPassesFiltersThrough() throws Exception {
        when(brands.list(any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mvc.perform(
                        get("/api/v1/admin/brands")
                                .param("page", "1")
                                .param("size", "5")
                                .param("name", "Titan")
                                .param("slug", "titan")
                                .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void listReturns400WhenServiceRejectsPagination() throws Exception {
        when(brands.list(any()))
                .thenThrow(new IllegalArgumentException("page must be non-negative"));

        mvc.perform(get("/api/v1/admin/brands").param("page", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("page must be non-negative"));
    }

    @Test
    void getReturnsBrand() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.get(id)).thenReturn(brandEntity(id, "b", true));

        mvc.perform(get("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(brands).get(id);
    }

    @Test
    void getReturns404WhenBrandMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.get(id))
                .thenThrow(new ProductService.ResourceNotFoundException("Brand not found: " + id));

        mvc.perform(get("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Brand not found: " + id));
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/admin/brands/not-a-uuid")).andExpect(status().isBadRequest());

        verify(brands, never()).get(any());
    }

    @Test
    void updateDelegatesAndReturnsBrand() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.update(eq(id), any(BrandUpdateRequest.class)))
                .thenReturn(brandEntity(id, "updated", true));

        mvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("updated"));

        verify(brands).update(eq(id), any(BrandUpdateRequest.class));
    }

    @Test
    void updateMapsDuplicateSlugTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.update(eq(id), any())).thenThrow(new DuplicateBrandSlugException(null));

        mvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Brand slug already exists"));
    }

    @Test
    void updateReturns404WhenBrandMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.update(eq(id), any()))
                .thenThrow(new ProductService.ResourceNotFoundException("Brand not found"));

        mvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidSlugWith400() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{\"slug\":\"UPPER\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brands);
    }

    @Test
    void updateAllowsEmptyPatch() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.update(eq(id), any())).thenReturn(brandEntity(id, "b", true));

        mvc.perform(
                        patch("/api/v1/admin/brands/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk());

        verify(brands).update(eq(id), any());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/admin/brands/{id}", id)).andExpect(status().isNoContent());

        verify(brands).delete(id);
    }

    @Test
    void deleteMapsForeignKeyViolationTo409() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doThrow(new ProductForeignKeyViolationException(null)).when(brands).delete(id);

        mvc.perform(delete("/api/v1/admin/brands/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Referenced resource does not exist"));
    }

    @Test
    void deleteReturns404WhenBrandMissing() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doThrow(new ProductService.ResourceNotFoundException("Brand not found: " + id))
                .when(brands)
                .delete(id);

        mvc.perform(delete("/api/v1/admin/brands/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void statusDeactivatesAndReturnsBrand() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.setStatus(id, false)).thenReturn(brandEntity(id, "b", false));

        mvc.perform(patch("/api/v1/admin/brands/{id}/status", id).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(brands).setStatus(id, false);
    }

    @Test
    void statusActivatesAndReturnsBrand() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.setStatus(id, true)).thenReturn(brandEntity(id, "b", true));

        mvc.perform(patch("/api/v1/admin/brands/{id}/status", id).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void statusRequiresActiveParamWith400() throws Exception {
        mvc.perform(patch("/api/v1/admin/brands/{id}/status", UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brands);
    }

    @Test
    void statusReturns404WhenBrandMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(brands.setStatus(eq(id), any(Boolean.class)))
                .thenThrow(new ProductService.ResourceNotFoundException("Brand not found"));

        mvc.perform(patch("/api/v1/admin/brands/{id}/status", id).param("active", "true"))
                .andExpect(status().isNotFound());
    }

    private static BrandEntity brandEntity(UUID id, String slug, boolean active) {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return new BrandEntity(
                id, "Brand " + slug, slug, null, null, null, Map.of(), active, now, now);
    }
}
