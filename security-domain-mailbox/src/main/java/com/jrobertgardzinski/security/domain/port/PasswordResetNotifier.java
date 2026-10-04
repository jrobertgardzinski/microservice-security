package com.jrobertgardzinski.security.domain.port;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;

/**
 * Outbound port that delivers a password-reset link — carrying the single-use token — to a user's
 * e-mail address. How it is delivered (SMTP, a queue, a log in tests) is left to the adapter.
 */
public interface PasswordResetNotifier {

    void sendResetLink(Email email, PasswordResetToken token);
}
