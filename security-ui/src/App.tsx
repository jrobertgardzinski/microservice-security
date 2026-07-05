import { useEffect, useState } from 'react';

// the e2e harness points the app at its own test-env service (Playwright injects window.SECURITY_URL
// before the app boots); a human on the dev server gets the stack's default
const SECURITY = (window as unknown as { SECURITY_URL?: string }).SECURITY_URL ?? 'http://localhost:8080';

type Mode = 'signin' | 'signup' | 'inbox' | 'me';

const prettify = (code: string) => code.toLowerCase().replaceAll('_', ' ');

/**
 * The auth service's own face: sign in, create an account, the "check your mailbox" screen,
 * confirming a mailed verification link (?verify=<token>), and who you are (/me). Deliberately
 * plain — its real job is being the specs' third entry point: the cucumber-js + Playwright glue in
 * e2e/ drives the very same register.feature and authenticate.feature the JVM runners do, through
 * the data-testid hooks below. React (like the meme gallery) so the portfolio speaks one frontend.
 */
export function App() {
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [me, setMe] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [emailErrors, setEmailErrors] = useState<string[]>([]);
  const [passwordErrors, setPasswordErrors] = useState<string[]>([]);

  // the verification mail links here (?verify=<token>); confirm it on arrival
  useEffect(() => {
    const mailed = new URLSearchParams(location.search).get('verify');
    if (!mailed) return;
    history.replaceState(null, '', location.pathname);
    void fetch(`${SECURITY}/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: mailed }),
    }).then((r) =>
      setNotice(
        r.ok
          ? 'E-mail verified — sign in below.'
          : 'This verification link was already used or replaced by a newer one.',
      ),
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

  const signIn = async () => {
    reset();
    const r = await fetch(`${SECURITY}/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (r.ok) {
      const body: { accessToken: string } = await r.json();
      const meResponse = await fetch(`${SECURITY}/me`, {
        headers: { Authorization: `Bearer ${body.accessToken}` },
      });
      const meBody: { email: string; roles?: string[] } = await meResponse.json();
      setMe(meBody.email);
      setRoles(meBody.roles ?? []);
      setMode('me');
    } else if (r.status === 403) {
      setNotice('E-mail not verified yet — follow the link in the mail first.');
    } else if (r.status === 429) {
      setNotice('Too many failed attempts — this source is blocked for a while.');
    } else {
      setNotice('Wrong e-mail or password.');
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
      // taken addresses answer identically (anti-enumeration) — the mail says which it was
      setMode('inbox');
    } else if (r.status === 422) {
      const errors: { emailErrors?: string[]; passwordErrors?: string[] } = await r.json();
      setEmailErrors(errors.emailErrors ?? []);
      setPasswordErrors(errors.passwordErrors ?? []);
    } else {
      setNotice(`Registration failed (${r.status}).`);
    }
  };

  const signOut = () => {
    setMe('');
    setRoles([]);
    switchTo('signin');
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    void (mode === 'signup' ? signUp() : signIn());
  };

  return (
    <div className="card">
      {mode === 'me' && (
        <>
          <h3>Signed in</h3>
          <p>
            You are <b data-testid="signed-in-email">{me}</b> ({roles.join(', ')}).
          </p>
          <button data-testid="sign-out" onClick={signOut}>Sign out</button>
        </>
      )}

      {mode === 'inbox' && (
        <>
          <h3 data-testid="inbox-screen">Check your mailbox</h3>
          <p>
            We sent a mail to <b>{email}</b> — follow it to continue.
          </p>
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
          <form onSubmit={submit}>
            <input data-testid="email" type="text" placeholder="e-mail" autoComplete="email"
                   value={email} onChange={(e) => setEmail(e.target.value)} />
            <input data-testid="password" type="password" placeholder="password"
                   autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                   value={password} onChange={(e) => setPassword(e.target.value)} />
            <button data-testid="submit" type="submit">
              {mode === 'signup' ? 'Create account' : 'Sign in'}
            </button>
          </form>
        </>
      )}

      {notice && <p data-testid="notice" className="notice">{notice}</p>}
      {(emailErrors.length > 0 || passwordErrors.length > 0) && (
        <div data-testid="validation-errors" className="notice">
          That will not do:
          <ul data-testid="email-errors">
            {emailErrors.map((e) => <li key={e}>{prettify(e)}</li>)}
          </ul>
          <ul data-testid="password-errors">
            {passwordErrors.map((e) => <li key={e}>{prettify(e)}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}
