-- The consumer side of a promise the producer already makes.
--
-- microservice-offboarding derives every outcome's id from (saga, type) rather than at random,
-- precisely so a re-announcement is byte-identical to the original — its own comment says
-- "consumers deduplicate on the id". Security, the only consumer, never read the id at all: it
-- matched outcomes to sagas by E-MAIL. That is not an identity, it is a person, and a person can
-- have more than one deletion saga.
--
-- The failure it allows: saga #1 ends PORTAL_PURGE_FAILED, the announcement is lost before its
-- outbox mark, and the sweeper re-publishes it every pass. Meanwhile the unblocked user signs in
-- and requests deletion again — saga #2. The re-publication from saga #1 arrives, matches only on
-- the e-mail, and unblocks the account while the portal is still erasing content for saga #2. The
-- real outcome of saga #2 is then ignored, because by then there is no STARTED saga left.
--
-- Remembering which outcome ids were already applied closes it: the second processing of the same
-- id is a no-op, whichever saga is open at the time.
create table processed_offboarding_outcomes (
    id           varchar(36) primary key,   -- the outcome's id, as offboarding derived it
    outcome_type varchar(64) not null,
    processed_at timestamp   not null
);

-- the reaper below sweeps by age; nothing ever queries by type alone
create index idx_processed_offboarding_outcomes_at
    on processed_offboarding_outcomes (processed_at);
