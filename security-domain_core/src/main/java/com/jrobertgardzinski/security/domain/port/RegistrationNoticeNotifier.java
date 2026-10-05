package com.jrobertgardzinski.security.domain.port;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Outbound port that tells the owner of an address that someone tried to register with it while it
 * already has an account. The HTTP reply never reveals that the account exists (anti-enumeration);
 * only this mail — readable solely by the address owner — carries the truth.
 */
public interface RegistrationNoticeNotifier {

    void sendAlreadyRegistered(Email email);
}
