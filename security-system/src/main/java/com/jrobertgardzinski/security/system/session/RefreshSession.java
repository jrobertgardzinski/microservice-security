package com.jrobertgardzinski.security.system.session;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.AuthorizationDataRepository;
import com.jrobertgardzinski.security.domain.vo.SessionRefreshRequest;
import com.jrobertgardzinski.security.domain.vo.SessionStatus;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.token.RefreshToken;

import java.time.Clock;

public class RefreshSession {
    private final AuthorizationDataRepository authorizationDataRepository;
    private final Clock clock;
    private final SessionTokensConfig config;
    private final AccessTokenMint accessTokenMint;

    public RefreshSession(AuthorizationDataRepository authorizationDataRepository, Clock clock,
                          SessionTokensConfig config, AccessTokenMint accessTokenMint) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.clock = clock;
        this.config = config;
        this.accessTokenMint = accessTokenMint;
    }

    public RefreshSessionResult execute(SessionRefreshRequest request) {
        RefreshToken refreshToken = request.refreshToken();

        return authorizationDataRepository.findByRefreshToken(refreshToken)
                .<RefreshSessionResult>map(session -> {
                    if (session.status() == SessionStatus.ROTATED) {
                        // this refresh token was already rotated away — a replay signals theft
                        authorizationDataRepository.revokeFamily(session.family());
                        return new RefreshSessionResult.ReuseDetected();
                    }
                    if (session.refreshTokenExpiration().hasExpired(clock)) {
                        return new RefreshSessionResult.Expired(session.email());
                    }
                    // Single-use: rotate the presented token out, issue a new one in the same family.
                    //
                    // The rotation is CONDITIONAL and its result decides the branch. Reading the
                    // status above and writing unconditionally here was a check-then-act: two truly
                    // simultaneous refreshes both saw ACTIVE, both wrote ROTATED and both minted a
                    // successor, so one session forked into two live chains — and a thief refreshing
                    // alongside the victim walked away with a lineage nothing would ever flag.
                    //
                    // Losing the race is therefore treated exactly like presenting an already-rotated
                    // token, because from here the two are indistinguishable. The honest cost: two
                    // browser tabs refreshing at once now revoke the family and sign the user out,
                    // where before one of them silently won. That is the conservative side of the
                    // trade and the one OAuth's own guidance takes — a benign double refresh costs a
                    // sign-in, an undetected stolen token costs the account. Softening it needs a
                    // grace window with a replayable successor, which is a schema change and a
                    // separate decision.
                    //
                    // Rotation and creation go to the repository as ONE call, because they have to
                    // be one step. As two, a concurrent revokeFamily — the very thing the branch
                    // below performs — could land between them, destroying the lineage and then
                    // having a successor written into it: a live session inside a family the
                    // service reports as revoked.
                    return authorizationDataRepository.rotateAndCreate(
                                    refreshToken,
                                    () -> SessionTokens.createFor(session.email(), config, clock, accessTokenMint),
                                    session.family())
                            .<RefreshSessionResult>map(RefreshSessionResult.Refreshed::new)
                            .orElseGet(() -> {
                                authorizationDataRepository.revokeFamily(session.family());
                                return new RefreshSessionResult.ReuseDetected();
                            });
                })
                .orElseGet(RefreshSessionResult.NotFound::new);
    }
}
