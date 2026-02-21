package com.jrobertgardzinski.email.specifications.mx;

import com.jrobertgardzinski.email.domain.Email;

/**
 * Port for checking whether an email domain has valid MX records.
 * The actual DNS lookup is implemented in the infrastructure layer —
 * this interface keeps the domain free from I/O.
 */
public interface MxRecordPort {

    boolean hasMxRecord(Email email);
}
