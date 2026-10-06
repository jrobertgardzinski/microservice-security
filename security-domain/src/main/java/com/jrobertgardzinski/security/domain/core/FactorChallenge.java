package com.jrobertgardzinski.security.domain.core;

/**
 * A factor chain under way: the ticket that continues it, the factor asked for now, and what the
 * client needs to answer it ({@code null} when the code went out by mail or SMS).
 */
public record FactorChallenge(String ticket, FactorType factor, String challengeData) {
}
