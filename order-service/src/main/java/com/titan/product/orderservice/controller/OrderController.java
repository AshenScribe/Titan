package com.titan.product.orderservice.controller;

import com.titan.product.orderservice.domain.exception.OrderApiException;
import com.titan.product.orderservice.domain.service.OrderService;
import com.titan.product.orderservice.dto.OrderApiModels.CancelOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.CreateOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.OrderResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderSummary;
import com.titan.product.orderservice.dto.OrderApiModels.PageResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            Principal principal, @Valid @RequestBody CreateOrderRequest request) {
        return service.create(userId(principal), request);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse get(Principal principal, @PathVariable UUID orderId) {
        return service.get(userId(principal), orderId);
    }

    @GetMapping("/users/me/orders")
    public PageResponse<OrderSummary> list(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(userId(principal), page, size);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderResponse cancel(
            Principal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        return service.cancel(userId(principal), orderId, request);
    }

    private UUID userId(Principal principal) {
        if (principal == null) {
            throw new OrderApiException(401, "Authentication is required");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new OrderApiException(401, "Authenticated principal is not a user UUID");
        }
    }
}
