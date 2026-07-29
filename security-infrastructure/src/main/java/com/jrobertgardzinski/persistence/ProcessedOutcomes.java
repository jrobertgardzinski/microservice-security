package com.jrobertgardzinski.persistence;

import java.time.Instant;

/**
 * Which offboarding outcomes this service has already acted on.
 *
 * <p>Exists because the producer's identity for an outcome and the consumer's were not the same
 * thing. microservice-offboarding derives an outcome's id from (saga, type) so a re-announcement is
 * byte-identical — "consumers deduplicate on the id", says its own comment — while security matched
 * outcomes to sagas by E-MAIL, which is a person, not a run. A person can have two deletion sagas,
 * and a re-announced outcome from the first one then closes the second.
 */
public interface ProcessedOutcomes {

    /**
     * @return {@code true} if this caller is the first to claim this outcome id, {@code false} if
     *         it has already been acted on — in which case the caller must do nothing at all
     */
    boolean claim(String outcomeId, String outcomeType, Instant at);
}
