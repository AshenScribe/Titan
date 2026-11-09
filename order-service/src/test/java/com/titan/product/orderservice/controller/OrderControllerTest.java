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
package com.titan.product.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.domain.exception.ConflictException;
import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import com.titan.product.orderservice.domain.exception.NotFoundException;
import com.titan.product.orderservice.domain.exception.OrderApiException;
import com.titan.product.orderservice.domain.service.OrderService;
import com.titan.product.orderservice.dto.OrderApiModels.CancelOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.CreateOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.ItemResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderSummary;
import com.titan.product.orderservice.dto.OrderApiModels.PageResponse;
import java.math.BigDecimal;
import java.security.Principal;
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

@WebMvcTest(OrderController.class)
@Import(OrderApiExceptionController.class)
class OrderControllerTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final Principal PRINCIPAL = () -> USER_ID;

    @Autowired private MockMvc mvc;

    @MockitoBean private OrderService service;

    @Test
    void createDelegatesToServiceAndReturns201() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.create(eq(UUID.fromString(USER_ID)), any(CreateOrderRequest.class)))
                .thenReturn(orderResponse(orderId, OrderStatus.PENDING));

        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "items": [{"variantId": "%s", "quantity": 2}],
                                  "shippingAddress": {"line1": "123 Main St"},
                                  "customerNote": "deliver fast"
                                }
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.getValue()))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication is required"));

        verifyNoInteractions(service);
    }

    @Test
    void createRejectsNonUuidPrincipal() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .principal(() -> "not-a-uuid")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authenticated principal is not a user UUID"));

        verifyNoInteractions(service);
    }

    @Test
    void createRejectsEmptyItemsWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[], "shippingAddress":{"line1":"x"}}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createRejectsMissingShippingAddressWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}]}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createRejectsNonPositiveQuantityWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":0}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createReturns422WhenServiceRejectsDuplicateVariants() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new OrderApiException(422, "Each variant may appear only once"));

        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1},{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Each variant may appear only once"));
    }

    @Test
    void createReturns503WhenProductServiceIsDown() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new DependencyUnavailableException("Product Service is unavailable"));

        mvc.perform(
                        post("/api/v1/orders")
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {"items":[{"variantId":"%s","quantity":1}],
                                 "shippingAddress":{"line1":"x"}}
                                """
                                                .formatted(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Product Service is unavailable"));
    }

    @Test
    void getDelegatesWithPrincipalAndPathId() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.get(UUID.fromString(USER_ID), orderId))
                .thenReturn(orderResponse(orderId, OrderStatus.PAID));

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.getValue()));

        verify(service).get(UUID.fromString(USER_ID), orderId);
    }

    @Test
    void getReturns404WhenServiceThrowsNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.get(any(), eq(orderId))).thenThrow(new NotFoundException("Order not found"));

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void getRejectsNonUuidPathWith400() throws Exception {
        mvc.perform(get("/api/v1/orders/not-a-uuid").principal(PRINCIPAL))
                .andExpect(status().isBadRequest());

        verify(service, never()).get(any(), any());
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void listUsesDefaultsForPageAndSize() throws Exception {
        when(service.list(UUID.fromString(USER_ID), 0, 20)).thenReturn(emptyPage());

        mvc.perform(get("/api/v1/users/me/orders").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listPassesThroughQueryParams() throws Exception {
        when(service.list(UUID.fromString(USER_ID), 2, 50))
                .thenReturn(new PageResponse<>(List.of(orderSummary()), 2, 50, 120, 3));

        mvc.perform(
                        get("/api/v1/users/me/orders")
                                .principal(PRINCIPAL)
                                .param("page", "2")
                                .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(120))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].itemCount").value(4));

        verify(service).list(UUID.fromString(USER_ID), 2, 50);
    }

    @Test
    void listReturns400WhenServiceRejectsPagination() throws Exception {
        when(service.list(any(), eq(-1), any(Integer.class)))
                .thenThrow(new OrderApiException(400, "Invalid pagination"));

        mvc.perform(get("/api/v1/users/me/orders").principal(PRINCIPAL).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid pagination"));
    }

    @Test
    void listRejectsNonIntegerPageWith400() throws Exception {
        mvc.perform(get("/api/v1/users/me/orders").principal(PRINCIPAL).param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void cancelDelegatesWithReasonAndReturnsUpdatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.cancel(
                        eq(UUID.fromString(USER_ID)), eq(orderId), any(CancelOrderRequest.class)))
                .thenReturn(orderResponse(orderId, OrderStatus.CANCELLED));

        mvc.perform(
                        post("/api/v1/orders/{orderId}/cancel", orderId)
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "reason": "changed mind"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.getValue()));
    }

    @Test
    void cancelRejectsBlankReasonWith400() throws Exception {
        mvc.perform(
                        post("/api/v1/orders/{orderId}/cancel", UUID.randomUUID())
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "reason": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void cancelReturns409WhenServiceRejectsState() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.cancel(any(), eq(orderId), any()))
                .thenThrow(new ConflictException("Order cannot be cancelled from SHIPPED"));

        mvc.perform(
                        post("/api/v1/orders/{orderId}/cancel", orderId)
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "reason": "x"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Order cannot be cancelled from SHIPPED"));
    }

    @Test
    void cancelReturns404WhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.cancel(any(), eq(orderId), any()))
                .thenThrow(new NotFoundException("Order not found"));

        mvc.perform(
                        post("/api/v1/orders/{orderId}/cancel", orderId)
                                .principal(PRINCIPAL)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "reason": "x"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private static OrderResponse orderResponse(UUID id, OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return new OrderResponse(
                id,
                status,
                "USD",
                new BigDecimal("19.99"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("69.99"),
                Map.of("line1", "x"),
                null,
                null,
                List.of(
                        new ItemResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Product",
                                "Variant",
                                "SKU",
                                new BigDecimal("19.99"),
                                1,
                                new BigDecimal("19.99"))),
                now,
                null,
                status == OrderStatus.CANCELLED ? now : null);
    }

    private static OrderSummary orderSummary() {
        return new OrderSummary(
                UUID.randomUUID(),
                OrderStatus.PENDING,
                new BigDecimal("69.99"),
                "USD",
                4,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    private static PageResponse<OrderSummary> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0);
    }
}
