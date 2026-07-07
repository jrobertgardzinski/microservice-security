// The browser half of the WebAuthn factor: turn the server's challenge into a real
// navigator.credentials call and pack the result into the flat base64url envelopes the
// server verifies. All binary crosses the wire as base64url, matching WebauthnFactor.

const enc = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

const dec = (base64url: string): Uint8Array => {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64 + '=='.slice(0, (4 - (base64.length % 4)) % 4));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
};

interface CreationOptions {
  challenge: string;
  rpId: string;
  rpName: string;
  userName: string;
}

/** Enrol a passkey: run navigator.credentials.create against the server's options, return the
 *  attestation envelope the confirm endpoint expects (or null if the user cancels). */
export async function enrolPasskey(display: string): Promise<string | null> {
  const options: CreationOptions = JSON.parse(display);
  const userId = new TextEncoder().encode(options.userName);
  const credential = (await navigator.credentials.create({
    publicKey: {
      challenge: dec(options.challenge),
      rp: { id: options.rpId, name: options.rpName },
      user: { id: userId, name: options.userName, displayName: options.userName },
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }],   // ES256
      authenticatorSelection: { residentKey: 'required', userVerification: 'preferred' },
      timeout: 60000,
    },
  })) as PublicKeyCredential | null;
  if (!credential) return null;
  const response = credential.response as AuthenticatorAttestationResponse;
  return JSON.stringify({
    type: 'webauthn.create',
    credentialId: credential.id,
    publicKey: enc(response.getPublicKey()!),                // SPKI, no CBOR to parse
    clientDataJSON: enc(response.clientDataJSON),
  });
}

/** Sign in with a passkey: run navigator.credentials.get over the server's nonce, return the
 *  assertion envelope the factor endpoint expects (or null if the user cancels). */
export async function assertPasskey(challengeNonce: string): Promise<string | null> {
  const credential = (await navigator.credentials.get({
    publicKey: {
      challenge: dec(challengeNonce),
      userVerification: 'preferred',
      timeout: 60000,
    },
  })) as PublicKeyCredential | null;
  if (!credential) return null;
  const response = credential.response as AuthenticatorAssertionResponse;
  return JSON.stringify({
    type: 'webauthn.get',
    credentialId: credential.id,
    authenticatorData: enc(response.authenticatorData),
    signature: enc(response.signature),
    clientDataJSON: enc(response.clientDataJSON),
  });
}
