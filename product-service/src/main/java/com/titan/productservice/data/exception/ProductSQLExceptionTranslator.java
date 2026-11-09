//------------------------------------------------------------------------------
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
//------------------------------------------------------------------------------

package com.titan.productservice.data.exception;

import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class ProductSQLExceptionTranslator implements SQLExceptionTranslator {

    private final SQLExceptionTranslator delegate = new SQLStateSQLExceptionTranslator();

    @Override
    public DataAccessException translate(@NonNull String task, String sql, SQLException ex) {

        String state = ex.getSQLState();
        if (state == null) {
            return delegate.translate(task, sql, ex);
        }

        return switch (state) {
            case "23505" -> translateUnique(ex);
            case "23503" -> new ProductForeignKeyViolationException(ex);
            case "23502" -> new ProductNotNullViolationException(ex);
            case "23514" -> translateCheck(ex);
            case "22001" -> new ProductStringTruncationException(ex);
            case "22P02" -> new ProductInvalidValueException(ex);
            default -> delegate.translate(task, sql, ex);
        };
    }

    private DataAccessException translateUnique(SQLException ex) {
        String constraint = constraintName(ex);
        return switch (constraint == null ? "" : constraint) {
            case "uq_brands_slug" -> new DuplicateBrandSlugException(ex);
            case "uq_categories_slug" -> new DuplicateCategorySlugException(ex);
            case "uq_products_slug" -> new DuplicateProductSlugException(ex);
            case "uq_product_variants_sku" -> new DuplicateVariantSkuException(ex);
            case "uq_product_variants_barcode" -> new DuplicateVariantBarcodeException(ex);
            case "uq_product_primary_media" -> new DuplicatePrimaryMediaException(ex);
            case "uq_product_primary_category" -> new DuplicatePrimaryCategoryException(ex);
            case "uq_product_media_position" -> new DuplicateProductMediaPositionException(ex);
            default -> new ProductUniqueViolationException("Unique constraint violation: " + constraint, ex);
        };
    }

    private DataAccessException translateCheck(SQLException ex) {
        String constraint = constraintName(ex);
        String message = serverMessage(ex);

        if (message != null) {
            if (message.contains("must have at least one active variant")) {
                return new ActiveProductRequiresVariantException(ex);
            }
            if (message.contains("Category hierarchy cycle") || message.contains("cannot be its own parent")) {
                return new CategoryHierarchyCycleException(ex);
            }
        }

        return switch (constraint == null ? "" : constraint) {
            case "products_active_requires_publication", "products_deleted_not_active" ->
                    new InvalidProductStatusTransitionException(ex);
            case "product_variants_price_check", "product_variants_compare_at_price_check" ->
                    new InvalidVariantPriceException(ex);
            case "product_media_position_check" -> new InvalidMediaPositionException(ex);
            case "product_variants_currency_check" -> new InvalidCurrencyException(ex);
            default -> new ProductCheckConstraintViolationException(ex);
        };
    }

    private static String constraintName(SQLException ex) {
        try {
            Object serverError = ex.getClass().getMethod("getServerErrorMessage").invoke(ex);
            if (serverError == null) return null;
            return (String) serverError.getClass().getMethod("getConstraint").invoke(serverError);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String serverMessage(SQLException ex) {
        try {
            Object serverError = ex.getClass().getMethod("getServerErrorMessage").invoke(ex);
            if (serverError == null) return ex.getMessage();
            return (String) serverError.getClass().getMethod("getMessage").invoke(serverError);
        } catch (ReflectiveOperationException ignored) {
            return ex.getMessage();
        }
    }

}