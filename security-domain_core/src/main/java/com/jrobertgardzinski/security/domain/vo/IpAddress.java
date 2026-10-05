package com.jrobertgardzinski.security.domain.vo;

/**
 * Network address from which a request originates.
 */
public record IpAddress (String value) {

    public IpAddress {
        if (!IpAddressValidator.isValid(value)) {
            throw new IllegalArgumentException("%s is not valid!".formatted(value));
        }
    }
}
