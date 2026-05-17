package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.OtpCode;

/**
 * Delivers a freshly generated OTP code to the user's mailbox.
 * Implementations integrate with the actual mail transport.
 */
public interface EmailOtpSenderPort {

    void send(Email recipient, OtpCode code);
}
