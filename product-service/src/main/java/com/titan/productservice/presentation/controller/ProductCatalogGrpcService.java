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

package com.titan.productservice.presentation.controller;

import com.titan.product.catalog.v1.GetActiveVariantRequest;
import com.titan.product.catalog.v1.GetActiveVariantResponse;
import com.titan.product.catalog.v1.ProductCatalogServiceGrpc;
import com.titan.productservice.data.entity.ProductVariantEntity;
import com.titan.productservice.domain.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ProductCatalogGrpcService
        extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {
    private final ProductService products;

    public ProductCatalogGrpcService(ProductService products) {
        this.products = products;
    }

    @Override
    public void getActiveVariant(
            GetActiveVariantRequest request, StreamObserver<GetActiveVariantResponse> observer) {
        try {
            ProductVariantEntity variant =
                    products.publicVariantById(UUID.fromString(request.getVariantId()));
            observer.onNext(
                    GetActiveVariantResponse.newBuilder()
                            .setVariantId(variant.getId().toString())
                            .setProductId(variant.getProductId().toString())
                            .setProductName(products.getPublic(variant.getProductId()).getName())
                            .setVariantName(variant.getName())
                            .setSku(variant.getSku())
                            .setUnitPrice(variant.getPrice().toPlainString())
                            .setCurrency(variant.getCurrency())
                            .setAttributesJson(
                                    new com.fasterxml.jackson.databind.ObjectMapper()
                                            .writeValueAsString(variant.getAttributes()))
                            .build());
            observer.onCompleted();
        } catch (IllegalArgumentException exception) {
            observer.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid variant ID")
                            .asRuntimeException());
        } catch (ProductService.ResourceNotFoundException exception) {
            observer.onError(
                    Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
        } catch (Exception exception) {
            observer.onError(
                    Status.INTERNAL
                            .withDescription("Product lookup failed")
                            .withCause(exception)
                            .asRuntimeException());
        }
    }
}
