package com.ludoarena.exception;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.game.model.EngineException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.error(e.code(), e.getMessage(), null));
    }

    @ExceptionHandler(EngineException.class)
    public ResponseEntity<ApiResponse<Void>> handleEngine(EngineException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.code(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_FAILED", "Please correct the highlighted fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadInput(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "Malformed request", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("FORBIDDEN", "You do not have permission to do that", null));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("DUPLICATE", "That record already exists", null));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("CONCURRENT_UPDATE", "The game state changed, please retry", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR", "Something went wrong", null));
    }
}
