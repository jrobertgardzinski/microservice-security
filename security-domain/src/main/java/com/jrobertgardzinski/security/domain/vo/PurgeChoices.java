package com.jrobertgardzinski.security.domain.vo;

import java.util.Optional;

/**
 * The leaver's choice of what happens to their content elsewhere, made in the deletion wizard and
 * carried through the saga. The rules are opaque strings here on purpose — their vocabulary
 * (delete / anonymise / keep-popular thresholds) belongs to the content services, and this service
 * only ferries the choice. Empty axes mean "whatever the content service's deployment default is".
 */
public record PurgeChoices(Optional<String> memesRule, Optional<String> commentsRule) {

    public static PurgeChoices serviceDefaults() {
        return new PurgeChoices(Optional.empty(), Optional.empty());
    }
}
