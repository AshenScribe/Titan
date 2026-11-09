package com.titan.productservice.data.exception;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

public sealed class ProductDataException extends DataAccessException
        permits ProductCheckConstraintViolationException,
                ProductNotNullViolationException,
                ProductForeignKeyViolationException,
                ProductUniqueViolationException,
                ProductStringTruncationException,
                ProductInvalidValueException,
                ProductConcurrentModificationException,
                ProductDeletedException,
                ActiveProductRequiresVariantException,
                CategoryHierarchyCycleException,
                InvalidProductStatusTransitionException,
                InvalidVariantPriceException,
                InvalidMediaPositionException,
                InvalidCurrencyException,
                DuplicateBrandSlugException,
                DuplicateCategorySlugException,
                DuplicateProductSlugException,
                DuplicateVariantSkuException,
                DuplicateVariantBarcodeException,
                DuplicatePrimaryMediaException,
                DuplicatePrimaryCategoryException,
                DuplicateProductMediaPositionException {

    public ProductDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProductDataException(String message) {
        super(message);
    }

    protected ProductDataException(String message, SQLException cause) {
        super(message, cause);
    }
}