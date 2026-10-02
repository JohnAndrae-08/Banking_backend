package com.example.banking.exception;

import com.example.banking.dto.ApiResponse;
import com.example.banking.monitor.FlowPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final FlowPublisher flow;

    public GlobalExceptionHandler(FlowPublisher flow) {
        this.flow = flow;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(ResourceNotFoundException ex) {
        flow.error("Request failed: resource not found", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler({InsufficientBalanceException.class, InvalidTransactionException.class,
            AccountNotActiveException.class})
    public ResponseEntity<ApiResponse<?>> handleBadRequest(RuntimeException ex) {
        flow.error("Request failed: validation/business rule", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedAccountAccessException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(UnauthorizedAccountAccessException ex) {
        flow.error("Request failed: unauthorized account access", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(DuplicateResourceException ex) {
        flow.error("Request failed: duplicate resource", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<?>> handleAuth(AuthenticationFailedException ex) {
        flow.error("Request failed: authentication", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Access denied"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleSpringAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("Authentication required"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            errors.put(e.getField(), e.getDefaultMessage());
        }
        ApiResponse<Map<String, String>> body = ApiResponse.ok("Validation failed", errors);
        body.setSuccess(false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("An unexpected error occurred"));
    }
}
