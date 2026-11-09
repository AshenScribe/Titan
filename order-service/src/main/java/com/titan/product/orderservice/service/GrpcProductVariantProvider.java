package com.titan.product.orderservice.service;

import com.titan.product.catalog.v1.GetActiveVariantRequest;
import com.titan.product.catalog.v1.ProductCatalogServiceGrpc;
import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import com.titan.product.orderservice.domain.exception.InvalidVariantException;
import com.titan.product.orderservice.domain.exception.VariantNotFoundException;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public final class GrpcProductVariantProvider implements ProductVariantProvider {

    private final ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub client;
    private final long deadlineMillis;

    public GrpcProductVariantProvider(
            ManagedChannel channel,
            @Value("${titan.product.grpc.deadline:500ms}") Duration deadline) {
        this.client = ProductCatalogServiceGrpc.newBlockingStub(channel);
        this.deadlineMillis = deadline.toMillis();
    }

    @Override
    public ProductVariant getActiveVariant(UUID variantId) {
        try {
            var response =
                    client.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                            .getActiveVariant(
                                    GetActiveVariantRequest.newBuilder()
                                            .setVariantId(variantId.toString())
                                            .build());
            return new ProductVariant(
                    UUID.fromString(response.getVariantId()),
                    UUID.fromString(response.getProductId()),
                    response.getProductName(),
                    response.getVariantName(),
                    response.getSku(),
                    new BigDecimal(response.getUnitPrice()),
                    response.getCurrency(),
                    response.getAttributesJson());
        } catch (StatusRuntimeException exception) {
            throw translate(exception, variantId);
        } catch (RuntimeException exception) {
            throw new DependencyUnavailableException(
                    "Product Service returned an invalid response");
        }
    }

    private RuntimeException translate(StatusRuntimeException exception, UUID variantId) {
        Status.Code code = exception.getStatus().getCode();
        return switch (code) {
            case NOT_FOUND ->
                    new VariantNotFoundException("Active variant not found: " + variantId);
            case INVALID_ARGUMENT -> new InvalidVariantException("Invalid variant: " + variantId);
            default -> new DependencyUnavailableException("Product Service is unavailable");
        };
    }
}
