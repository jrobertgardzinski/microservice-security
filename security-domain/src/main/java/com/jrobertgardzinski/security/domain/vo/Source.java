package com.jrobertgardzinski.security.domain.vo;

/**
 * The subject of brute-force accounting: who is knocking, as the boundary saw them.
 *
 * <p>IDENTITY is the {@link IpAddress} alone — it keys blocks and failure counts, so it is the
 * only field in {@code equals}/{@code hashCode}. The OBSERVED context ({@code userAgent} today;
 * machine name or browser version may join it) is forensics: it tells an operator what an
 * incident looked like, but it must never key the lockout — an attacker rotates a user-agent
 * header for free, an IP address not. Subnet or ASN may sharpen the identity axis later.
 *
 * <p>GDPR note: the observed context is personal data. It lives only as long as the failure
 * records it annotates — cleaning those records removes it.
 */
public record Source(IpAddress ipAddress, String userAgent) {

    /** A source with nothing observed about it — entry points that only know the address. */
    public static Source of(IpAddress ipAddress) {
        return new Source(ipAddress, "");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Source other && ipAddress.equals(other.ipAddress);
    }

    @Override
    public int hashCode() {
        return ipAddress.hashCode();
    }
}
