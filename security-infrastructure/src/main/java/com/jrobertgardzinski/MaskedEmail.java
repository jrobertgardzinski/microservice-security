package com.jrobertgardzinski;

/**
 * The one way this service writes an e-mail address into a log line.
 *
 * <p>Logs leave the process: they are shipped to Loki, kept for weeks and read by whoever can open
 * the dashboard. A full address there is personal data outside the database that the account
 * deletion path is supposed to be erasing, so the deletion path must not be the thing that copies
 * it out. Two characters and the domain are enough to recognise a report you are chasing, and not
 * enough to harvest a mailing list from a log dump.
 *
 * <p>It lives here because the same four lines were pasted, word for word, into
 * {@link AccountDeletionOrchestrator} and {@link OffboardingOutcomeListener} — and a masking rule
 * duplicated is a masking rule that will be tightened in one copy only.
 */
final class MaskedEmail {

    private MaskedEmail() {
    }

    /** Two characters and the domain: enough to recognise a report, not enough to harvest. */
    static String masked(String address) {
        if (address == null) {
            return "***";
        }
        int at = address.indexOf('@');
        return at <= 0 ? "***" : address.substring(0, Math.min(2, at)) + "***" + address.substring(at);
    }

    /**
     * The same rule applied to a request path, for the one access line
     * {@link CorrelationIdFilter} writes per request.
     *
     * <p>Two admin endpoints carry the subject's address as a PATH SEGMENT
     * ({@code PUT /admin/users/{email}/roles}, {@code PUT /admin/users/{email}/factors/reset}), so
     * every admin request used to copy a full address into the access log — and from there into
     * Loki, for weeks. Masking here rather than reshaping those two URLs is the cheaper half of the
     * choice: the URLs are a published contract with clients and pacts behind them, while this is
     * one place and it also covers whatever path carries an address next.
     *
     * <p>A segment is an address if it holds an {@code @}, encoded or not — the filter reads the
     * path through Micronaut, which may hand it over either way.
     */
    static String maskedPath(String path) {
        if (path == null) {
            return null;
        }
        String decoded = path.replaceAll("(?i)%40", "@");
        if (!decoded.contains("@")) {
            return path;
        }
        String[] segments = decoded.split("/", -1);
        StringBuilder out = new StringBuilder(decoded.length());
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(segments[i].contains("@") ? masked(segments[i]) : segments[i]);
        }
        return out.toString();
    }
}
