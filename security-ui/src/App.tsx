import { useEffect, useState } from 'react';

// the e2e harness points the app at its own test-env service (Playwright injects window.SECURITY_URL
// before the app boots); a human on the dev server gets the stack's default
const SECURITY = (window as unknown as { SECURITY_URL?: string }).SECURITY_URL ?? 'http://localhost:8080';

type Mode = 'signin' | 'signup' | 'inbox' | 'mfa' | 'me';
type Factor = { type: string; label: string };

const prettify = (code: string) => code.toLowerCase().replaceAll('_', ' ');

/**
 * The auth service's own face: sign in (single- or multi-factor), create an account, the "check
 * your mailbox" screen, confirming a mailed verification link, managing sign-in factors, and /me.
 * Deliberately plain — its real job is being the specs' third entry point (the cucumber-js +
 * Playwright glue drives the same features the JVM runners do). React, like the meme gallery.
 */
export function App() {
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [me, setMe] = useState('');
  const [token, setToken] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [emailErrors, setEmailErrors] = useState<string[]>([]);
  const [passwordErrors, setPasswordErrors] = useState<string[]>([]);
  // multi-factor sign-in
  const [mfaTicket, setMfaTicket] = useState('');
  const [nextFactor, setNextFactor] = useState('');
  const [code, setCode] = useState('');
  // enrolment
  const [factors, setFactors] = useState<Factor[]>([]);
  const [enrolling, setEnrolling] = useState(false);
  const [enrolCode, setEnrolCode] = useState('');

  useEffect(() => {
    const mailed = new URLSearchParams(location.search).get('verify');
    if (!mailed) return;
    history.replaceState(null, '', location.pathname);
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
    const meBody: { email: string; roles?: string[] } = await meResponse.json();
    setToken(accessToken);
    setMe(meBody.email);
    setRoles(meBody.roles ?? []);
    setMode('me');
    void loadFactors(accessToken);
  };

  const signIn = async () => {
    reset();
    const r = await fetch(`${SECURITY}/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (r.ok) {
      await enterSession((await r.json()).accessToken);
    } else if (r.status === 202) {
      // more factors owed — the first challenge is out; ask for the proof
      const body: { mfaTicket: string; nextFactor: string } = await r.json();
      setMfaTicket(body.mfaTicket);
      setNextFactor(body.nextFactor);
      setCode('');
      setMode('mfa');
    } else if (r.status === 403) {
      setNotice('E-mail not verified yet — follow the link in the mail first.');
    } else if (r.status === 429) {
      setNotice('Too many failed attempts — this source is blocked for a while.');
    } else {
      setNotice('Wrong e-mail or password.');
    }
  };

  const submitFactor = async () => {
    reset();
    const r = await fetch(`${SECURITY}/authenticate/factor`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mfaTicket, proof: code }),
    });
    if (r.ok) {
      await enterSession((await r.json()).accessToken);
    } else if (r.status === 202) {
      setNextFactor((await r.json()).nextFactor);
      setCode('');
    } else {
      const body: { status?: string; attemptsLeft?: number } = await r.json();
      setNotice(body.status === 'WRONG_CODE'
        ? `Wrong code${body.attemptsLeft != null ? ` — ${body.attemptsLeft} tries left` : ''}.`
        : 'That sign-in expired — start over.');
      if (body.status !== 'WRONG_CODE') switchTo('signin');
    }
  };

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
    if (r.ok) setFactors((await r.json()).have ?? []);
  };

  const startEnrolEmail = async () => {
    setNotice(null);
    const r = await fetch(`${SECURITY}/account/factors/EMAIL_CODE/enroll/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: '{}',
    });
    if (r.status === 202) { setEnrolling(true); setEnrolCode(''); }
    else setNotice('Could not start enrolment.');
  };

  const confirmEnrolEmail = async () => {
    const r = await fetch(`${SECURITY}/account/factors/EMAIL_CODE/enroll/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ code: enrolCode }),
    });
    if (r.ok) { setEnrolling(false); setNotice('E-mail factor enrolled — you will use it next sign-in.'); void loadFactors(token); }
    else setNotice('Wrong code — enrolment not completed.');
  };

  const signOut = () => {
    setToken('');
    setMe('');
    setRoles([]);
    setFactors([]);
    switchTo('signin');
  };

  return (
    <div className="card">
      {mode === 'me' && (
        <>
          <h3>Signed in</h3>
          <p>You are <b data-testid="signed-in-email">{me}</b> ({roles.join(', ')}).</p>

          <h4>Sign-in factors</h4>
          <ul data-testid="factor-list">
            {factors.map((f) => <li key={f.type}>{f.label}</li>)}
            {factors.length === 0 && <li><i>password only</i></li>}
          </ul>
          {!enrolling && !factors.some((f) => f.type === 'EMAIL_CODE') && (
            <button data-testid="add-email-factor" onClick={startEnrolEmail}>Add e-mail code factor</button>
          )}
          {enrolling && (
            <div>
              <input data-testid="enroll-code" placeholder="code from your e-mail"
                     value={enrolCode} onChange={(e) => setEnrolCode(e.target.value)} />
              <button data-testid="enroll-submit" onClick={confirmEnrolEmail}>Confirm</button>
            </div>
          )}
          <p><button data-testid="sign-out" onClick={signOut}>Sign out</button></p>
        </>
      )}

      {mode === 'mfa' && (
        <>
          <h3 data-testid="mfa-screen">One more step</h3>
          <p>We sent a {prettify(nextFactor)} to your e-mail — enter it to finish signing in.</p>
          <form onSubmit={(e) => { e.preventDefault(); void submitFactor(); }}>
            <input data-testid="mfa-code" placeholder="sign-in code" value={code}
                   onChange={(e) => setCode(e.target.value)} autoFocus />
            <button data-testid="mfa-submit" type="submit">Sign in</button>
          </form>
        </>
      )}

      {mode === 'inbox' && (
        <>
          <h3 data-testid="inbox-screen">Check your mailbox</h3>
          <p>We sent a mail to <b>{email}</b> — follow it to continue.</p>
          <button data-testid="back-to-signin" onClick={() => switchTo('signin')}>Back to sign in</button>
        </>
      )}

      {(mode === 'signin' || mode === 'signup') && (
        <>
          <nav>
            <button data-testid="tab-signin" className={mode === 'signin' ? 'active' : ''}
                    onClick={() => switchTo('signin')}>Sign in</button>
            <button data-testid="tab-signup" className={mode === 'signup' ? 'active' : ''}
                    onClick={() => switchTo('signup')}>Create account</button>
          </nav>
          <form onSubmit={(e) => { e.preventDefault(); void (mode === 'signup' ? signUp() : signIn()); }}>
            <input data-testid="email" type="text" placeholder="e-mail" autoComplete="email"
                   value={email} onChange={(e) => setEmail(e.target.value)} />
            <input data-testid="password" type="password" placeholder="password"
                   autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                   value={password} onChange={(e) => setPassword(e.target.value)} />
            <button data-testid="submit" type="submit">{mode === 'signup' ? 'Create account' : 'Sign in'}</button>
          </form>
        </>
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
