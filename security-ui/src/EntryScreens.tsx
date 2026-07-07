import { Mode } from './lib';

/**
 * The signed-out screens: the sign-in / create-account tabs, the forgot-password request, the
 * choose-a-new-password form (reached from the mailed ?reset= link) and the "check your mailbox"
 * interstitial. Presentational — App owns the state and the requests.
 */

export function SignInUpScreen(props: {
  mode: Mode;
  email: string; password: string;
  setEmail: (v: string) => void; setPassword: (v: string) => void;
  switchTo: (m: Mode) => void;
  signIn: () => void; signUp: () => void;
}) {
  const { mode, email, password, setEmail, setPassword, switchTo, signIn, signUp } = props;
  return (
    <>
      <nav>
        <button data-testid="tab-signin" className={mode === 'signin' ? 'active' : ''}
                onClick={() => switchTo('signin')}>Sign in</button>
        <button data-testid="tab-signup" className={mode === 'signup' ? 'active' : ''}
                onClick={() => switchTo('signup')}>Create account</button>
      </nav>
      <form onSubmit={(e) => { e.preventDefault(); mode === 'signup' ? signUp() : signIn(); }}>
        <input data-testid="email" type="text" placeholder="e-mail" autoComplete="email"
               value={email} onChange={(e) => setEmail(e.target.value)} />
        <input data-testid="password" type="password" placeholder="password"
               autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
               value={password} onChange={(e) => setPassword(e.target.value)} />
        <button data-testid="submit" type="submit">{mode === 'signup' ? 'Create account' : 'Sign in'}</button>
      </form>
      {mode === 'signin' && (
        <p><button data-testid="forgot-password" className="linkish"
                   onClick={() => switchTo('forgot')}>Forgot password?</button></p>
      )}
    </>
  );
}

export function ForgotScreen(props: {
  email: string; setEmail: (v: string) => void;
  requestReset: () => void; switchTo: (m: Mode) => void;
}) {
  const { email, setEmail, requestReset, switchTo } = props;
  return (
    <>
      <h3 data-testid="forgot-screen">Forgot your password?</h3>
      <p>Tell us your e-mail and we will send a reset link.</p>
      <form onSubmit={(e) => { e.preventDefault(); requestReset(); }}>
        <input data-testid="forgot-email" type="text" placeholder="e-mail" autoComplete="email"
               value={email} onChange={(e) => setEmail(e.target.value)} />
        <button data-testid="forgot-submit" type="submit">Send reset link</button>
      </form>
      <button data-testid="back-to-signin" onClick={() => switchTo('signin')}>Back to sign in</button>
    </>
  );
}

export function ResetScreen(props: {
  password: string; setPassword: (v: string) => void; completeReset: () => void;
}) {
  const { password, setPassword, completeReset } = props;
  return (
    <>
      <h3 data-testid="reset-screen">Choose a new password</h3>
      <form onSubmit={(e) => { e.preventDefault(); completeReset(); }}>
        <input data-testid="reset-password" type="password" placeholder="new password"
               autoComplete="new-password"
               value={password} onChange={(e) => setPassword(e.target.value)} />
        <button data-testid="reset-submit" type="submit">Set new password</button>
      </form>
    </>
  );
}

export function InboxScreen(props: { email: string; switchTo: (m: Mode) => void }) {
  return (
    <>
      <h3 data-testid="inbox-screen">Check your mailbox</h3>
      <p>We sent a mail to <b>{props.email}</b> — follow it to continue.</p>
      <button data-testid="back-to-signin" onClick={() => props.switchTo('signin')}>Back to sign in</button>
    </>
  );
}
