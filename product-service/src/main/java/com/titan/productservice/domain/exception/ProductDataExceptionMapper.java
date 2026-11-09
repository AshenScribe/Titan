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

package com.titan.productservice.domain.exception;

import com.titan.productservice.data.exception.ActiveProductRequiresVariantException;
import com.titan.productservice.data.exception.CategoryHierarchyCycleException;
import com.titan.productservice.data.exception.DuplicateBrandSlugException;
import com.titan.productservice.data.exception.DuplicateCategorySlugException;
import com.titan.productservice.data.exception.DuplicatePrimaryCategoryException;
import com.titan.productservice.data.exception.DuplicatePrimaryMediaException;
import com.titan.productservice.data.exception.DuplicateProductMediaPositionException;
import com.titan.productservice.data.exception.DuplicateProductSlugException;
import com.titan.productservice.data.exception.DuplicateVariantBarcodeException;
import com.titan.productservice.data.exception.DuplicateVariantSkuException;
import com.titan.productservice.data.exception.InvalidCurrencyException;
import com.titan.productservice.data.exception.InvalidMediaPositionException;
import com.titan.productservice.data.exception.InvalidProductStatusTransitionException;
import com.titan.productservice.data.exception.InvalidVariantPriceException;
import com.titan.productservice.data.exception.ProductCheckConstraintViolationException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.data.exception.ProductDataException;
import com.titan.productservice.data.exception.ProductDeletedException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.data.exception.ProductInvalidValueException;
import com.titan.productservice.data.exception.ProductNotNullViolationException;
import com.titan.productservice.data.exception.ProductStringTruncationException;
import com.titan.productservice.data.exception.ProductUniqueViolationException;

/**
 * Translates data-layer persistence exceptions into domain API exceptions with explicit HTTP status
 * codes. Mirrors OrderDataExceptionMapper in order-service.
 */
public final class ProductDataExceptionMapper {

    private ProductDataExceptionMapper() {}

    public static ProductApiException toApi(ProductDataException exception) {
        return switch (exception) {
            case DuplicateBrandSlugException e ->
                    new ConflictException("Brand slug already exists");
            case DuplicateCategorySlugException e ->
                    new ConflictException("Category slug already exists");
            case DuplicateProductSlugException e ->
                    new ConflictException("Product slug already exists");
            case DuplicateVariantSkuException e ->
                    new ConflictException("Variant SKU already exists");
            case DuplicateVariantBarcodeException e ->
                    new ConflictException("Variant barcode already exists");
            case DuplicatePrimaryMediaException e ->
                    new ConflictException("Product already has a primary media item");
            case DuplicatePrimaryCategoryException e ->
                    new ConflictException("Product already has a primary category");
            case DuplicateProductMediaPositionException e ->
                    new ConflictException("Duplicate product media position");
            case ProductUniqueViolationException e ->
                    new ConflictException("Resource conflicts with an existing record");
            case ProductForeignKeyViolationException e ->
                    new ConflictException("Referenced resource does not exist");
            case CategoryHierarchyCycleException e ->
                    new ConflictException("Category hierarchy cycle detected");
            case ProductConcurrentModificationException e ->
                    new ProductApiException(412, "Resource was modified by another request");
            case ActiveProductRequiresVariantException e ->
                    new ConflictException("Active product must retain at least one active variant");
            case ProductDeletedException e -> new ConflictException("Product has been deleted");

            case InvalidProductStatusTransitionException e ->
                    new ProductApiException(422, "Invalid product status transition");
            case InvalidVariantPriceException e ->
                    new ProductApiException(422, "Variant price is invalid");
            case InvalidMediaPositionException e ->
                    new ProductApiException(422, "Media position must be non-negative");
            case InvalidCurrencyException e ->
                    new ProductApiException(422, "Currency must be a 3-letter ISO 4217 code");
            case ProductCheckConstraintViolationException e ->
                    new ProductApiException(422, "Request violates a business constraint");

            case ProductNotNullViolationException e ->
                    new ProductApiException(400, "A required field is missing");
            case ProductStringTruncationException e ->
                    new ProductApiException(400, "A field value is too long");
            case ProductInvalidValueException e ->
                    new ProductApiException(400, "A field value has an invalid format");

            case ProductDataException e -> new ProductApiException(500, "Internal server error");
        };
    }
}
