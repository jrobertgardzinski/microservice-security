package com.jrobertgardzinski.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/** Row of {@code passwordless_accounts}: an account with no usable password (federated-born). */
@MappedEntity("passwordless_accounts")
record PasswordlessAccountEntity(@Id String userEmail) {
}
