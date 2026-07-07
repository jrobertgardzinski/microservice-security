import { useEffect, useState } from 'react';
import { assertPasskey, enrolPasskey } from './webauthn';
import { Factor, Mode, SECURITY, Session, factorLabel, prettify } from './lib';
import { AccountScreen } from './AccountScreen';
import { MfaScreen } from './MfaScreen';
import { ForgotScreen, InboxScreen, ResetScreen, SignInUpScreen } from './EntryScreens';

/**
 * The auth service's own face: sign in (single- or multi-factor), create an account, the "check
 * your mailbox" screen, confirming a mailed verification link, managing sign-in factors, and /me.
 * Deliberately plain — its real job is being the specs' third entry point (the cucumber-js +
 * Playwright glue drives the same features the JVM runners do). React, like the meme gallery.
 *
 * <p>This component owns ALL state and behaviour; the screens (AccountScreen, MfaScreen, the
 * entry screens) are presentational and receive it grouped by concern. The data-testids that the
 * e2e glue clicks live in the screen files.
 */
export function App() {
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [me, setMe] = useState('');
  const [token, setToken] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [compliant, setCompliant] = useState(true);
  const [floor, setFloor] = useState({ required: 1, have: 1 });
  const [notice, setNotice] = useState<string | null>(null);
  const [emailErrors, setEmailErrors] = useState<string[]>([]);
  const [passwordErrors, setPasswordErrors] = useState<string[]>([]);
  // multi-factor sign-in
  const [mfaTicket, setMfaTicket] = useState('');
  const [nextFactor, setNextFactor] = useState('');
  const [code, setCode] = useState('');
  const [challengeData, setChallengeData] = useState('');
  // enrolment
  const [factors, setFactors] = useState<Factor[]>([]);
  const [offered, setOffered] = useState<string[]>([]);
  const [enrollingType, setEnrollingType] = useState('');
  const [enrollDisplay, setEnrollDisplay] = useState('');
  const [enrollTarget, setEnrollTarget] = useState('');
  const [enrolCode, setEnrolCode] = useState('');
  // recovery codes: shown exactly once, right after generation; only the count is retrievable later
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [recoveryUnused, setRecoveryUnused] = useState<number | null>(null);
  // password reset: the token arrives in the link (?reset=...), the new password in the form
  const [resetToken, setResetToken] = useState('');
  // password change (signed in): prove the current one, pick the next
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  // e-mail change (signed in): the confirmation link goes to the NEW address
  const [newEmail, setNewEmail] = useState('');
  // every active session of the account (family id + refresh expiry)
  const [sessions, setSessions] = useState<Session[]>([]);
  // account deletion: irreversible, so it demands a fresh step-up (password, then factors)
  const [deleting, setDeleting] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteTicket, setDeleteTicket] = useState('');
  const [deleteCode, setDeleteCode] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const mailed = params.get('verify');
    const reset = params.get('reset');
    const change = params.get('change');
    if (!mailed && !reset && !change) return;
    history.replaceState(null, '', location.pathname);
    if (reset) {
      // the link only carries the token; the new password is typed on the reset screen
      setResetToken(reset);
      setMode('reset');
      return;
    }
    if (change) {
      void fetch(`${SECURITY}/confirm-email-change`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: change }),
      }).then((r) =>
        setNotice(r.ok
          ? 'E-mail changed — sign in with your new address.'
          : 'This change link was already used or has expired.'),
      );
      return;
    }
    void fetch(`${SECURITY}/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: mailed }),
    }).then((r) =>
      setNotice(r.ok
        ? 'E-mail verified — sign in below.'
        : 'This verification link was already used or replaced by a newer one.'),
    );
  }, []);

  const reset = () => {
    setNotice(null);
    setEmailErrors([]);
    setPasswordErrors([]);
  };

  const switchTo = (next: Mode) => {
    setMode(next);
    reset();
  };

  const enterSession = async (accessToken: string) => {
    const meResponse = await fetch(`${SECURITY}/me`, { headers: { Authorization: `Bearer ${accessToken}` } });
    const meBody: { email: string; roles?: string[]; mfaCompliant?: boolean; requiredFactors?: number; haveFactors?: number } =
      await meResponse.json();
    setToken(accessToken);
    setMe(meBody.email);
    setRoles(meBody.roles ?? []);
    setCompliant(meBody.mfaCompliant ?? true);
    setFloor({ required: meBody.requiredFactors ?? 1, have: meBody.haveFactors ?? 1 });
    setMode('me');
    void loadFactors(accessToken);
    void loadSessions(accessToken);
  };

  const loadSessions = async (accessToken: string) => {
    const r = await fetch(`${SECURITY}/sessions`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (r.ok) setSessions((await r.json()).sessions ?? []);
  };

  const revokeAllSessions = async () => {
    reset();
    const r = await fetch(`${SECURITY}/sessions/revoke-all`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.ok) {
      // our own session died with the rest — back to the door
      signOut();
      setNotice('Signed out everywhere.');
    } else {
      setNotice(`Could not revoke the sessions (${r.status}).`);
    }
  };

  const signIn = async () => {
    reset();
    const r = await fetch(`${SECURITY}/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (r.status === 202) {
      // more factors owed — the first challenge is out; ask for the proof.
      // (202 is "ok" to fetch, so this MUST be checked before r.ok)
      const body: { mfaTicket: string; nextFactor: string; challengeData?: string } = await r.json();
      setMfaTicket(body.mfaTicket);
      setNextFactor(body.nextFactor);
      setChallengeData(body.challengeData ?? '');
      setCode('');
      setMode('mfa');
    } else if (r.ok) {
      await enterSession((await r.json()).accessToken);
    } else if (r.status === 403) {
      setNotice('E-mail not verified yet — follow the link in the mail first.');
    } else if (r.status === 429) {
      setNotice('Too many failed attempts — this source is blocked for a while.');
    } else {
      setNotice('Wrong e-mail or password.');
    }
  };

  const submitFactor = async (proof: string) => {
    reset();
    const r = await fetch(`${SECURITY}/authenticate/factor`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mfaTicket, proof }),
    });
    if (r.status === 202) {
      // the chain has another link — 202 is "ok" to fetch, so check it before r.ok
      const body: { nextFactor: string; challengeData?: string } = await r.json();
      setNextFactor(body.nextFactor);
      setChallengeData(body.challengeData ?? '');
      setCode('');
    } else if (r.ok) {
      await enterSession((await r.json()).accessToken);
    } else {
      const body: { status?: string; attemptsLeft?: number } = await r.json();
      setNotice(body.status === 'WRONG_CODE'
        ? `Wrong code${body.attemptsLeft != null ? ` — ${body.attemptsLeft} tries left` : ''}.`
        : 'That sign-in expired — start over.');
      if (body.status !== 'WRONG_CODE') switchTo('signin');
    }
  };

  // a passkey step needs no typing: sign the challenge and submit the assertion
  const submitPasskey = async () => {
    reset();
    try {
      const assertion = await assertPasskey(challengeData);
      if (assertion) await submitFactor(assertion);
    } catch {
      setNotice('Passkey sign-in was cancelled or failed.');
    }
  };

  // when the sign-in chain reaches a passkey, prompt the authenticator right away
  useEffect(() => {
    if (mode === 'mfa' && nextFactor === 'WEBAUTHN' && challengeData) {
      void submitPasskey();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, nextFactor, challengeData]);

  const signUp = async () => {
    reset();
    const r = await fetch(`${SECURITY}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (r.status === 201) {
      setMode('inbox');
    } else if (r.status === 422) {
      const errors: { emailErrors?: string[]; passwordErrors?: string[] } = await r.json();
      setEmailErrors(errors.emailErrors ?? []);
      setPasswordErrors(errors.passwordErrors ?? []);
    } else {
      setNotice(`Registration failed (${r.status}).`);
    }
  };

  const loadFactors = async (accessToken: string) => {
    const r = await fetch(`${SECURITY}/account/factors`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (r.ok) { const body = await r.json(); setFactors(body.have ?? []); setOffered(body.offered ?? []); }
    const rc = await fetch(`${SECURITY}/account/recovery-codes`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (rc.ok) setRecoveryUnused((await rc.json()).unused ?? 0);
  };

  const generateRecoveryCodes = async () => {
    setNotice(null);
    const r = await fetch(`${SECURITY}/account/recovery-codes`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.ok) {
      const body: { codes: string[] } = await r.json();
      setRecoveryCodes(body.codes ?? []);           // the one and only time they are visible
      setRecoveryUnused((body.codes ?? []).length);
    } else {
      setNotice(`Could not generate recovery codes (${r.status}).`);
    }
  };

  const startEnrol = async (type: string) => {
    setNotice(null);
    const body = type === 'SMS_CODE' ? JSON.stringify({ target: enrollTarget }) : '{}';
    const r = await fetch(`${SECURITY}/account/factors/${type}/enroll/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body,
    });
    if (r.status === 202) {
      const setup: { status: string; display?: string } = await r.json();
      if (type === 'WEBAUTHN') {
        // a passkey enrols in one gesture: create the credential and confirm the attestation
        await confirmPasskeyEnrol(setup.display ?? '');
        return;
      }
      setEnrollingType(type);
      setEnrollDisplay(setup.display ?? '');
      setEnrolCode('');
    } else {
      setNotice('Could not start enrolment.');
    }
  };

  const confirmPasskeyEnrol = async (display: string) => {
    try {
      const attestation = await enrolPasskey(display);
      if (!attestation) { setNotice('Passkey enrolment was cancelled.'); return; }
      const r = await fetch(`${SECURITY}/account/factors/WEBAUTHN/enroll/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ code: attestation }),
      });
      if (r.ok) {
        setNotice('passkey enrolled — you will use it next sign-in.');
        void loadFactors(token);
      } else {
        setNotice('Passkey enrolment not completed.');
      }
    } catch {
      setNotice('Passkey enrolment was cancelled or failed.');
    }
  };

  const confirmEnrol = async () => {
    const r = await fetch(`${SECURITY}/account/factors/${enrollingType}/enroll/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ code: enrolCode }),
    });
    if (r.ok) {
      setNotice(`${factorLabel(enrollingType)} enrolled — you will use it next sign-in.`);
      setEnrollingType(''); setEnrollDisplay(''); setEnrollTarget('');
      void loadFactors(token);
    } else {
      setNotice('Wrong code — enrolment not completed.');
    }
  };

  const requestReset = async () => {
    reset();
    const r = await fetch(`${SECURITY}/reset-password/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    setNotice(r.status === 202
      ? 'If that address has an account, a reset link is on its way.'
      : r.status === 429
        ? 'Too many reset requests — try again later.'
        : `Could not request a reset (${r.status}).`);
  };

  const completeReset = async () => {
    reset();
    const r = await fetch(`${SECURITY}/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: resetToken, password }),
    });
    if (r.ok) {
      setPassword('');
      switchTo('signin');
      setNotice('Password reset — sign in with your new password.');
    } else {
      const body: { status?: string } = await r.json().catch(() => ({}));
      setNotice(body.status === 'WEAK_PASSWORD'
        ? 'That password is too weak — pick a stronger one.'
        : 'This reset link was already used or has expired.');
    }
  };

  const changePassword = async () => {
    reset();
    const r = await fetch(`${SECURITY}/account/password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ currentPassword, newPassword }),
    });
    if (r.ok) {
      setCurrentPassword('');
      setNewPassword('');
      setNotice('Password changed.');
    } else {
      const body: { status?: string } = await r.json().catch(() => ({}));
      setNotice(body.status === 'WEAK_PASSWORD'
        ? 'That password is too weak — pick a stronger one.'
        : 'Wrong current password.');
    }
  };

  const requestEmailChange = async () => {
    reset();
    const r = await fetch(`${SECURITY}/account/email/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ newEmail }),
    });
    // 202 whatever the address's fate — the truth goes by mail (anti-enumeration)
    setNotice(r.status === 202
      ? 'Check the new address — we sent a confirmation link.'
      : `Could not request the change (${r.status}).`);
    if (r.status === 202) setNewEmail('');
  };

  const performDelete = async () => {
    const r = await fetch(`${SECURITY}/account/delete`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.status === 202) {
      signOut();
      setNotice('Account closing — you are signed out everywhere.');
    } else {
      setNotice(`Could not close the account (${r.status}).`);
    }
  };

  const startDelete = async () => {
    reset();
    const r = await fetch(`${SECURITY}/account/step-up`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ action: 'delete-account', password: deletePassword }),
    });
    if (r.status === 202) {
      // the chain has factors — 202 is "ok" to fetch, so check it before r.ok
      setDeleteTicket((await r.json()).stepUpTicket);
      setDeleteCode('');
    } else if (r.ok) {
      await performDelete();
    } else {
      setNotice('Wrong password.');
    }
  };

  const submitDeleteCode = async () => {
    reset();
    const r = await fetch(`${SECURITY}/account/step-up/factor`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stepUpTicket: deleteTicket, proof: deleteCode }),
    });
    if (r.status === 202) {
      setDeleteCode('');   // another link in the chain
    } else if (r.ok) {
      await performDelete();
    } else {
      setNotice('Wrong code.');
    }
  };

  const signOut = () => {
    // end the session server-side too — with a same-origin deployment the refresh cookie rides
    // along and the session family dies; cross-origin (no cookie) it is an idempotent no-op
    void fetch(`${SECURITY}/logout`, { method: 'POST', credentials: 'include', keepalive: true })
      .catch(() => { /* signing out locally must never hang on the network */ });
    setToken('');
    setMe('');
    setRoles([]);
    setFactors([]);
    switchTo('signin');
  };

  return (
    <div className="card">
      {mode === 'me' && (
        <AccountScreen
          identity={{ me, roles, compliant, floor }}
          factors={{
            have: factors, offered,
            enrollingType, enrollDisplay, enrollTarget, enrolCode,
            setEnrollTarget, setEnrolCode,
            startEnrol: (type) => void startEnrol(type),
            confirmEnrol: () => void confirmEnrol(),
          }}
          recovery={{
            codes: recoveryCodes, unused: recoveryUnused,
            generate: () => void generateRecoveryCodes(),
          }}
          sessions={{ list: sessions, revokeAll: () => void revokeAllSessions() }}
          emailChange={{ newEmail, setNewEmail, request: () => void requestEmailChange() }}
          passwordChange={{
            currentPassword, newPassword, setCurrentPassword, setNewPassword,
            change: () => void changePassword(),
          }}
          deletion={{
            deleting, password: deletePassword, ticket: deleteTicket, code: deleteCode,
            setDeleting, setPassword: setDeletePassword, setCode: setDeleteCode,
            start: () => void startDelete(),
            submitCode: () => void submitDeleteCode(),
          }}
          onSignOut={signOut}
        />
      )}

      {mode === 'mfa' && (
        <MfaScreen
          nextFactor={nextFactor}
          code={code}
          setCode={setCode}
          submitFactor={(proof) => void submitFactor(proof)}
          submitPasskey={() => void submitPasskey()}
        />
      )}

      {mode === 'forgot' && (
        <ForgotScreen email={email} setEmail={setEmail}
                      requestReset={() => void requestReset()} switchTo={switchTo} />
      )}

      {mode === 'reset' && (
        <ResetScreen password={password} setPassword={setPassword}
                     completeReset={() => void completeReset()} />
      )}

      {mode === 'inbox' && <InboxScreen email={email} switchTo={switchTo} />}

      {(mode === 'signin' || mode === 'signup') && (
        <SignInUpScreen mode={mode} email={email} password={password}
                        setEmail={setEmail} setPassword={setPassword} switchTo={switchTo}
                        signIn={() => void signIn()} signUp={() => void signUp()} />
      )}

      {notice && <p data-testid="notice" className="notice">{notice}</p>}
      {(emailErrors.length > 0 || passwordErrors.length > 0) && (
        <div data-testid="validation-errors" className="notice">
          That will not do:
          <ul data-testid="email-errors">{emailErrors.map((e) => <li key={e}>{prettify(e)}</li>)}</ul>
          <ul data-testid="password-errors">{passwordErrors.map((e) => <li key={e}>{prettify(e)}</li>)}</ul>
        </div>
      )}
    </div>
  );
}
