package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link FederatedIdentityRepository}: {@code (provider, subject)} -> the account it
 * opens. Linking the same pair again repoints it, which is what the port calls an upsert.
 *
 * <p>It exists for the scenarios that close an account: {@code DeleteAccount} severs every link,
 * and a test that wants to prove the severing happened needs something to read it back from. It
 * used to be an anonymous class inside {@code FederatedSignInSteps} — the third copy of a map of
 * strings, written because there was nowhere to put the first one.
 */
public final class FakeFederatedIdentityRepository implements FederatedIdentityRepository {

    /** Insertion-ordered so {@link #linksOf} answers in the order the links were made. */
    private final Map<String, String> byProviderAndSubject = new LinkedHashMap<>();

    private static String key(String provider, String subject) {
        return provider + "|" + subject;
    }

    @Override
    public Optional<Email> findUserBy(String provider, String subject) {
        return Optional.ofNullable(byProviderAndSubject.get(key(provider, subject))).map(Email::of);
    }

    @Override
    public void link(String provider, String subject, Email userEmail) {
        byProviderAndSubject.put(key(provider, subject), userEmail.value());
    }

    @Override
    public void unlinkAll(Email userEmail) {
        byProviderAndSubject.values().removeIf(userEmail.value()::equals);
    }

    @Override
    public void relinkAll(Email fromEmail, Email toEmail) {
        byProviderAndSubject.replaceAll(
                (key, email) -> fromEmail.value().equals(email) ? toEmail.value() : email);
    }

    /** How many identities still open this account — the reader a closure asserts on. */
    public int countLinksOf(Email userEmail) {
        return (int) byProviderAndSubject.values().stream().filter(userEmail.value()::equals).count();
    }
}
