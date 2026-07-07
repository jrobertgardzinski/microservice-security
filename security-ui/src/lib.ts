// the e2e harness points the app at its own test-env service (Playwright injects window.SECURITY_URL
// before the app boots); a human on the dev server gets the stack's default
export const SECURITY =
  (window as unknown as { SECURITY_URL?: string }).SECURITY_URL ?? 'http://localhost:8080';

export type Mode = 'signin' | 'signup' | 'inbox' | 'mfa' | 'me' | 'forgot' | 'reset';
export type Factor = { type: string; label: string };
export type Session = { family: string; expiresAt: string };

export const prettify = (code: string) => code.toLowerCase().replaceAll('_', ' ');

export const factorLabel = (type: string) =>
  ({ EMAIL_CODE: 'e-mail code', SMS_CODE: 'SMS code', TOTP: 'authenticator app', WEBAUTHN: 'passkey' } as Record<string, string>)[type] ?? type;
