package com.metro.domain;

/**
 * Business-rule violation. Carries a stable machine-readable code so the API layer
 * can map it to an HTTP status without parsing messages.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
