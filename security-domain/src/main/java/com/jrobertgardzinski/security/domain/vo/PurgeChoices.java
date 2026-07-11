package com.jrobertgardzinski.security.domain.vo;

import java.util.Map;

/**
 * The leaver's choice of what happens to their content elsewhere, made in the deletion wizard and
 * ferried through the offboarding saga. Both the axis NAMES (memes, comments, …) and the rules
 * are opaque here on purpose — their vocabulary belongs to the content services and their
 * orchestrator; identity only carries the map. An empty map means "whatever each content
 * service's deployment default is". (This used to name the portal's axes as fields — foreign
 * domain inside an identity value object, and the reason the saga was extracted.)
 */
public record PurgeChoices(Map<String, String> rules) {

    public PurgeChoices {
        rules = Map.copyOf(rules);
    }

    public static PurgeChoices serviceDefaults() {
        return new PurgeChoices(Map.of());
    }
}
