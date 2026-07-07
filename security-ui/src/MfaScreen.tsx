import { prettify } from './lib';

/**
 * The "one more step" screen of a multi-factor sign-in: a passkey prompt when the chain reached
 * WEBAUTHN (the assertion fires automatically; the button is the retry), otherwise a code form
 * with the recovery-code hint. Presentational — App owns the ticket and the submission.
 */
export function MfaScreen(props: {
  nextFactor: string;
  code: string;
  setCode: (v: string) => void;
  submitFactor: (proof: string) => void;
  submitPasskey: () => void;
}) {
  const { nextFactor, code, setCode, submitFactor, submitPasskey } = props;
  return (
    <>
      <h3 data-testid="mfa-screen">One more step</h3>
      {nextFactor === 'WEBAUTHN' ? (
        <>
          <p data-testid="passkey-prompt">Confirm with your passkey to finish signing in.</p>
          <button data-testid="passkey-retry" onClick={() => submitPasskey()}>Use passkey</button>
        </>
      ) : (
        <>
          <p>We sent a {prettify(nextFactor)} to your e-mail — enter it to finish signing in.</p>
          <form onSubmit={(e) => { e.preventDefault(); submitFactor(code); }}>
            <input data-testid="mfa-code" placeholder="sign-in code" value={code}
                   onChange={(e) => setCode(e.target.value)} autoFocus />
            <button data-testid="mfa-submit" type="submit">Sign in</button>
          </form>
          <p data-testid="recovery-hint" style={{ fontSize: '0.85rem', opacity: 0.8 }}>
            Lost access? Type one of your recovery codes instead — it works once.
          </p>
        </>
      )}
    </>
  );
}
