import { describe, expect, it, vi } from 'vitest';

import { bodyOf, HttpError, messageFor, NetworkError, orThrow, request } from './api';

/**
 * The rules this UI had none of.
 *
 * <p>Every failure that was not explicitly enumerated used to reach the user as "Wrong e-mail or
 * password." — a 500, a proxy's 502, a 504 while the database was down. People were sent to reset
 * a password that was correct. And with nineteen bare `fetch` calls and two try/catch blocks in
 * the whole source tree, a dropped connection rejected a promise nobody awaited and the screen
 * simply said nothing at all.
 */
describe('what the user is told when something goes wrong', () => {
  it('blames the credentials only for the statuses that are about credentials', () => {
    const expected = { 401: 'Wrong e-mail or password.', 429: 'Too many failed attempts.' };

    expect(messageFor(new HttpError(401), expected)).toBe('Wrong e-mail or password.');
    expect(messageFor(new HttpError(429), expected)).toBe('Too many failed attempts.');
  });

  it('does not blame the user for a server fault — the whole point of this module', () => {
    const expected = { 401: 'Wrong e-mail or password.' };

    for (const serverFault of [500, 502, 503, 504]) {
      const message = messageFor(new HttpError(serverFault), expected);

      expect(message).not.toContain('Wrong');
      expect(message).toContain(String(serverFault));
    }
  });

  it('says the service is unreachable when it is, rather than leaving the screen silent', () => {
    expect(messageFor(new NetworkError(), { 401: 'Wrong e-mail or password.' }))
      .toBe('Security service unreachable.');
  });

  it('has something to say even about a failure it has never seen', () => {
    expect(messageFor(new TypeError('undefined is not a function'), {})).toBeTruthy();
  });
});

describe('reading a response that may not have a body', () => {
  it('survives a body that is not JSON — the usual shape of a proxy 502', async () => {
    const html = new Response('<html>502 Bad Gateway</html>', { status: 502 });

    await expect(bodyOf(html)).resolves.toEqual({});
  });

  it('survives an empty body', async () => {
    await expect(bodyOf(new Response(null, { status: 504 }))).resolves.toEqual({});
  });

  it('survives JSON that is not an object', async () => {
    await expect(bodyOf(new Response('"a string"', { status: 400 }))).resolves.toEqual({});
  });

  it('still returns the fields when there really is a body', async () => {
    const answer = new Response(JSON.stringify({ status: 'WRONG_CODE', attemptsLeft: 2 }),
      { status: 400 });

    await expect(bodyOf<{ status: string; attemptsLeft: number }>(answer))
      .resolves.toEqual({ status: 'WRONG_CODE', attemptsLeft: 2 });
  });
});

describe('the request helper', () => {
  it('turns an unreachable service into a NetworkError instead of an unhandled rejection', async () => {
    vi.stubGlobal('fetch', () => Promise.reject(new TypeError('Failed to fetch')));

    await expect(request('/authenticate')).rejects.toBeInstanceOf(NetworkError);

    vi.unstubAllGlobals();
  });

  it('hands back a non-ok response untouched — which status means what is the caller\'s call', async () => {
    vi.stubGlobal('fetch', () => Promise.resolve(new Response(null, { status: 403 })));

    await expect(request('/authenticate').then((r) => r.status)).resolves.toBe(403);

    vi.unstubAllGlobals();
  });

  it('orThrow carries the status, so one catch can handle both kinds of failure', () => {
    expect(() => orThrow(new Response(null, { status: 500 }))).toThrow(HttpError);
    try {
      orThrow(new Response(null, { status: 503 }));
    } catch (failure) {
      expect((failure as HttpError).status).toBe(503);
    }
  });
});
