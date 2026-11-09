package com.titan.product.orderservice.dto;

import com.titan.product.orderservice.data.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderApiModels {
    private OrderApiModels() {}

    public record CreateOrderRequest(
            @NotEmpty List<@Valid ItemRequest> items,
            @NotEmpty Map<String, Object> shippingAddress,
            Map<String, Object> billingAddress,
            String customerNote) {}

    public record ItemRequest(UUID variantId, @Positive int quantity) {}

    public record CancelOrderRequest(@NotBlank String reason) {}

    public record OrderResponse(
            UUID id,
            OrderStatus status,
            String currency,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal shippingAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            Map<String, Object> shippingAddress,
            Map<String, Object> billingAddress,
            String customerNote,
            List<ItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime paidAt,
            OffsetDateTime cancelledAt) {}

    public record ItemResponse(
            UUID productId,
            UUID variantId,
            String productName,
            String variantName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {}

    public record OrderSummary(
            UUID id,
            OrderStatus status,
            BigDecimal totalAmount,
            String currency,
            int itemCount,
            OffsetDateTime createdAt) {}

    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages) {}
}
