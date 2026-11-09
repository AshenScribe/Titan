package com.titan.productservice.exception;

import com.titan.productservice.presentation.dto.ErrorResponse;
import com.titan.productservice.service.CategoryService.ConflictException;
import com.titan.productservice.service.ProductService.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> optimisticConflict(OptimisticLockingFailureException exception) {
        return response(HttpStatus.PRECONDITION_FAILED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrityConflict(DataIntegrityViolationException exception) {
        return response(
                HttpStatus.CONFLICT,
                "The operation conflicts with an existing resource or database constraint",
                List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> businessConflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorResponse> invalidOperation(RuntimeException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        List<String> details =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .toList();
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return response(
                HttpStatus.BAD_REQUEST, "Request parameter has an invalid format", List.of());
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(
                        new ErrorResponse(
                                OffsetDateTime.now(),
                                status.value(),
                                status.getReasonPhrase(),
                                message,
                                details));
    }
}
