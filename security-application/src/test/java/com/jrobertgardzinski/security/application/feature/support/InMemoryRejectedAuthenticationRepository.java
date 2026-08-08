package com.jrobertgardzinski.security.application.feature.support;

import com.jrobertgardzinski.security.domain.entity.RejectedAuthentication;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.FailuresCount;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The application layer's own twin of the rejected-authentication register, kept in step with the
 * port after the lockout stopped being keyed by the source alone.
 *
 * <p>Both counts match on the same fields the domain says they should — the whole
 * {@link LockoutSubject} for the tight per-victim count, the {@link Source} alone for the ceiling
 * that catches spraying — so this twin cannot quietly disagree with the JDBC adapter and the
 * infrastructure twin about what "the same subject" means. Two implementations of one port drifting
 * on exactly that question is how a green test came to prove the wrong thing before.
 */
public final class InMemoryRejectedAuthenticationRepository implements RejectedAuthenticationRepository {

    private final List<RejectedAuthentication> records = new ArrayList<>();
    private long sequence = 0;

    @Override
    public RejectedAuthentication create(RejectedAuthenticationDetails details) {
        RejectedAuthentication record = new RejectedAuthentication(details, new RejectedAuthenticationId(++sequence));
        records.add(record);
        return record;
    }

    @Override
    public FailuresCount countFailuresOnAccount(LockoutSubject subject, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.subject().equals(subject) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    @Override
    public FailuresCount countFailuresFromSource(Source source, LocalDateTime since) {
        long count = records.stream()
                .map(RejectedAuthentication::details)
                .filter(details -> details.source().equals(source) && details.time().isAfter(since))
                .count();
        return new FailuresCount((int) count);
    }

    @Override
    public void removeAllFor(LockoutSubject subject) {
        // ONLY this pair, never the whole source: wiping everything an address ever did is what
        // made one known-good credential a reset button for every account behind it
        records.removeIf(record -> record.details().subject().equals(subject));
    }
}
