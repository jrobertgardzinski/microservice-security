package com.jrobertgardzinski.persistence;

import java.util.Map;

/**
 * The {@code security_settings} table as the live level sees it: every row at once, which is what
 * the snapshot copies, and one row written, which is what an admin's decision does. Text on both
 * sides; the type enters on the ladder's rung, where the parser and the value object refuse what
 * is not legal. A missing row is a vacant level, never an error.
 */
public interface SecuritySettingsTable {

    /** Every row, name to text. One round trip. */
    Map<String, String> rows();

    /** Insert or replace the one row under this name. */
    void put(String name, String value);
}
