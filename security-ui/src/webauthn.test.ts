import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { assertPasskey, enrolPasskey } from './webauthn';

/**
 * Two defects from the 2026-09-08 review, both in what the browser is ASKED to do:
 *
 * - UI-11: the user handle was the e-mail — personal data written into a device this service does
 *   not own and cannot erase, and above 64 bytes the enrolment simply fails.
 * - UI-10: the assertion passed no rpId, so the browser looked for a passkey under the page's own
 *   domain. That is right only while the deployment's rp id happens to equal the host serving the
 *   page; where it does not, sign-in finds no credential and says nothing useful.
 *
 * navigator.credentials is stubbed: what is being pinned is the REQUEST, which is the half this
 * code decides. The authenticator's answer is the browser's business.
 */
describe('the passkey request', () => {
  const b64url = (bytes: Uint8Array) =>
    btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  let created: PublicKeyCredentialCreationOptions | undefined;
  let requested: PublicKeyCredentialRequestOptions | undefined;

  beforeEach(() => {
    created = undefined;
    requested = undefined;
    vi.stubGlobal('navigator', {
      credentials: {
        create: (options: CredentialCreationOptions) => {
          created = options.publicKey;
          return Promise.resolve(null);
        },
        get: (options: CredentialRequestOptions) => {
          requested = options.publicKey;
          return Promise.resolve(null);
        },
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (window as unknown as { WEBAUTHN_RP_ID?: string }).WEBAUTHN_RP_ID;
    vi.resetModules();
  });

  it('sends the opaque handle the server minted, not the address', async () => {
    const handle = b64url(new Uint8Array(32).fill(7));

    await enrolPasskey(JSON.stringify({
      challenge: 'bm9uY2U',
      rpId: 'localhost',
      rpName: 'Security',
      userId: handle,
      userName: 'someone@example.com',
    }));

    const id = new Uint8Array(created!.user.id as ArrayBuffer);
    expect(id).toHaveLength(32);
    expect(new TextDecoder().decode(id)).not.toContain('@');
    expect(created!.user.name)
      .toBe('someone@example.com');   // still what the person sees in the passkey picker
  });

  it('falls back to the address only when the server sent no handle', async () => {
    await enrolPasskey(JSON.stringify({
      challenge: 'bm9uY2U',
      rpId: 'localhost',
      rpName: 'Security',
      userName: 'someone@example.com',
    }));

    expect(new TextDecoder().decode(new Uint8Array(created!.user.id as ArrayBuffer)))
      .toBe('someone@example.com');
  });

  it('asks for the passkey under the configured relying party', async () => {
    (window as unknown as { WEBAUTHN_RP_ID?: string }).WEBAUTHN_RP_ID = 'example.com';
    vi.resetModules();
    const { assertPasskey: configured } = await import('./webauthn');

    await configured('bm9uY2U');

    expect(requested!.rpId)
      .toBe('example.com');
  });

  it('leaves it to the browser when nothing is configured', async () => {
    await assertPasskey('bm9uY2U');

    expect(requested!.rpId).toBeUndefined();
  });
});
