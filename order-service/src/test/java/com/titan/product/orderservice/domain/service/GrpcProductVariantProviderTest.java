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
package com.titan.product.orderservice.domain.service;

import com.titan.product.catalog.v1.GetActiveVariantRequest;
import com.titan.product.catalog.v1.GetActiveVariantResponse;
import com.titan.product.catalog.v1.ProductCatalogServiceGrpc;
import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import com.titan.product.orderservice.domain.exception.InvalidVariantException;
import com.titan.product.orderservice.domain.exception.VariantNotFoundException;
import com.titan.product.orderservice.service.GrpcProductVariantProvider;
import com.titan.product.orderservice.service.ProductVariantProvider.ProductVariant;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcProductVariantProviderTest {

    private Server server;
    private ManagedChannel channel;
    private GrpcProductVariantProvider provider;
    private final AtomicReference<GetActiveVariantRequest> lastRequest = new AtomicReference<>();
    private FakeCatalogService fake;

    @BeforeEach
    void startServer() throws IOException {
        fake = new FakeCatalogService();
        server = NettyServerBuilder.forPort(0).addService(fake).build().start();
        channel =
                NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        provider = new GrpcProductVariantProvider(channel, Duration.ofSeconds(2));
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void mapsSuccessfulResponseToProductVariant() {
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        setResponse(
                GetActiveVariantResponse.newBuilder()
                        .setVariantId(variantId.toString())
                        .setProductId(productId.toString())
                        .setProductName("Mechanical Keyboard")
                        .setVariantName("US Layout")
                        .setSku("KB-US-001")
                        .setUnitPrice("149.99")
                        .setCurrency("USD")
                        .setAttributesJson("{\"switch\":\"brown\"}")
                        .build());

        ProductVariant result = provider.getActiveVariant(variantId);

        Assertions.assertThat(result.variantId()).isEqualTo(variantId);
        Assertions.assertThat(result.productId()).isEqualTo(productId);
        Assertions.assertThat(result.productName()).isEqualTo("Mechanical Keyboard");
        Assertions.assertThat(result.variantName()).isEqualTo("US Layout");
        Assertions.assertThat(result.sku()).isEqualTo("KB-US-001");
        Assertions.assertThat(result.unitPrice()).isEqualByComparingTo("149.99");
        Assertions.assertThat(result.currency()).isEqualTo("USD");
        Assertions.assertThat(result.attributes()).isEqualTo("{\"switch\":\"brown\"}");
    }

    @Test
    void sendsVariantIdAsStringInRequest() {
        UUID variantId = UUID.randomUUID();
        setResponse(defaultResponse(variantId));

        provider.getActiveVariant(variantId);

        Assertions.assertThat(lastRequest.get()).isNotNull();
        Assertions.assertThat(lastRequest.get().getVariantId()).isEqualTo(variantId.toString());
    }

    @Test
    void mapsNotFoundToVariantNotFoundException() {
        setError(Status.NOT_FOUND.withDescription("no such variant"));

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(UUID.randomUUID()))
                .isInstanceOf(VariantNotFoundException.class)
                .hasMessageContaining("Active variant not found");
    }

    @Test
    void mapsInvalidArgumentToInvalidVariantException() {
        setError(Status.INVALID_ARGUMENT.withDescription("bad uuid"));

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(UUID.randomUUID()))
                .isInstanceOf(InvalidVariantException.class)
                .hasMessageContaining("Invalid variant");
    }

    @Test
    void mapsUnavailableToDependencyUnavailableException() {
        setError(Status.UNAVAILABLE.withDescription("server down"));

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(UUID.randomUUID()))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("Product Service is unavailable");
    }

    @Test
    void mapsDeadlineExceededToDependencyUnavailableException() {
        setError(Status.DEADLINE_EXCEEDED);

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(UUID.randomUUID()))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void mapsInternalToDependencyUnavailableException() {
        setError(Status.INTERNAL);

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(UUID.randomUUID()))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void mapsUnparseableResponseToDependencyUnavailableException() {
        UUID requestedId = UUID.randomUUID();
        setResponse(
                GetActiveVariantResponse.newBuilder()
                        .setVariantId("not-a-uuid")
                        .setProductId(requestedId.toString())
                        .setUnitPrice("1.00")
                        .setCurrency("USD")
                        .build());

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(requestedId))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("Product Service returned an invalid response");
    }

    @Test
    void mapsUnparseableUnitPriceToDependencyUnavailableException() {
        UUID variantId = UUID.randomUUID();
        setResponse(
                GetActiveVariantResponse.newBuilder()
                        .setVariantId(variantId.toString())
                        .setProductId(UUID.randomUUID().toString())
                        .setUnitPrice("not-a-decimal")
                        .setCurrency("USD")
                        .build());

        Assertions.assertThatThrownBy(() -> provider.getActiveVariant(variantId))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("Product Service returned an invalid response");
    }

    private void setResponse(GetActiveVariantResponse response) {
        fake.next = new Reply(response, null);
    }

    private void setError(Status status) {
        fake.next = new Reply(null, status.asRuntimeException());
    }

    private static GetActiveVariantResponse defaultResponse(UUID variantId) {
        return GetActiveVariantResponse.newBuilder()
                .setVariantId(variantId.toString())
                .setProductId(UUID.randomUUID().toString())
                .setProductName("P")
                .setVariantName("V")
                .setSku("SKU")
                .setUnitPrice("1.00")
                .setCurrency("USD")
                .setAttributesJson("{}")
                .build();
    }

    private record Reply(GetActiveVariantResponse response, StatusRuntimeException error) {}

    private final class FakeCatalogService
            extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

        private volatile Reply next;

        @Override
        public void getActiveVariant(
                GetActiveVariantRequest request,
                StreamObserver<GetActiveVariantResponse> observer) {
            lastRequest.set(request);
            final Reply reply = next;
            if (reply == null) {
                observer.onError(
                        Status.INTERNAL.withDescription("no reply set").asRuntimeException());
                return;
            }
            if (reply.error() != null) {
                observer.onError(reply.error());
                return;
            }
            observer.onNext(reply.response());
            observer.onCompleted();
        }
    }
}
