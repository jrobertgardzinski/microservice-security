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

    /** There are a handful of content axes (memes, comments, collections…), never dozens. */
    public static final int MAX_AXES = 16;
    /** An axis name or a rule string is a short opaque token, not a document. */
    public static final int MAX_TOKEN_LENGTH = 256;

    public PurgeChoices {
        rules = Map.copyOf(rules);
        // the payload built from this map lands in a TEXT outbox column and then in a Kafka record;
        // the map comes straight from the request body, so without a bound a caller could hand us a
        // multi-megabyte value that later exceeds max.request.size and wedges the whole outbox drain
        if (rules.size() > MAX_AXES) {
            throw new IllegalArgumentException("too many purge axes: " + rules.size() + " > " + MAX_AXES);
        }
        rules.forEach((axis, rule) -> {
            if (axis.length() > MAX_TOKEN_LENGTH || (rule != null && rule.length() > MAX_TOKEN_LENGTH)) {
                throw new IllegalArgumentException("purge axis or rule exceeds " + MAX_TOKEN_LENGTH + " chars");
            }
        });
    }

    public static PurgeChoices serviceDefaults() {
        return new PurgeChoices(Map.of());
    }
}
