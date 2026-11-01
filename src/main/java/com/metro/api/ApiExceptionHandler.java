package com.metro.api;

import com.metro.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain error codes to RFC 9457 problem responses with a stable "code" property. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail handle(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusFor(ex.code()), ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }

    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "UNKNOWN_STATION", "CARD_NOT_FOUND", "NO_ROUTE" -> HttpStatus.NOT_FOUND;
            case "CARD_EXISTS", "IDEMPOTENCY_KEY_REUSED" -> HttpStatus.CONFLICT;
            case "INSUFFICIENT_BALANCE" -> HttpStatus.PAYMENT_REQUIRED;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
