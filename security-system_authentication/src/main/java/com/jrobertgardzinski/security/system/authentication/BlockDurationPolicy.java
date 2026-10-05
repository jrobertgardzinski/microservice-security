package com.jrobertgardzinski.security.system.authentication;

/**
 * Decides how long a brute-force block lasts, in minutes.
 *
 * <p>Extracted as a seam so the duration is injectable: production randomises it
 * (see {@link RandomBlockDurationPolicy}), tests pin it for determinism.
 */
public interface BlockDurationPolicy {
    int blockMinutes();
}
