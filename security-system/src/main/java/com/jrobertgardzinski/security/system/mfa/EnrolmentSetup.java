package com.jrobertgardzinski.security.system.mfa;

/**
 * What beginning a factor's enrolment produces. {@code secretMaterial} is what gets stored for the
 * factor (an e-mail/phone target for the code factors; a generated TOTP secret). {@code display}
 * is anything the user must see now to complete enrolment — a TOTP {@code otpauth://} URI for a QR
 * code, or {@code null} when nothing is shown (a code was sent instead). {@code challenge} is the
 * issued challenge for challenge-response factors (the sent code), or {@code null} for possession
 * factors that verify the proof directly.
 */
public record EnrolmentSetup(String secretMaterial, String display, Challenge challenge) {
}
