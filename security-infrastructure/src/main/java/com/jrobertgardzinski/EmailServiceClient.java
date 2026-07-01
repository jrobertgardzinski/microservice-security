package com.jrobertgardzinski;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.Map;

/**
 * Declarative HTTP client to the standalone {@code microservice-email} service. The base URL and the
 * shared API key come from configuration ({@code email-service.*}); each call presents the key in
 * the {@code X-Api-Key} header.
 */
@Client("${email-service.url}")
public interface EmailServiceClient {

    @Post("/mails/verification")
    void sendVerificationLink(@Header("X-Api-Key") String apiKey, @Body Map<String, Object> body);

    @Post("/mails/password-reset")
    void sendPasswordResetLink(@Header("X-Api-Key") String apiKey, @Body Map<String, Object> body);
}
