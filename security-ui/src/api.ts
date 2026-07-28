/**
 * The one place this UI is allowed to talk to the network, and the one place that knows what a
 * failure means.
 *
 * <p>Before this existed there were nineteen bare `fetch` calls in App.tsx and exactly two
 * try/catch blocks in the whole source tree, both for passkeys. Two consequences followed, and
 * both reached real users:
 *
 * 1. Every non-2xx that was not explicitly enumerated fell into the same `else` as a rejected
 *    password. A 500, a 502 from a proxy, a 504 while the database was down — all of them told the
 *    user "Wrong e-mail or password.", and sent them off to reset a password that was correct.
 * 2. A dropped connection rejected a promise nobody awaited (`void signIn()`), so the screen simply
 *    sat there with no message at all, and a non-JSON body (the usual shape of a proxy's 502) killed
 *    a handler mid-way through `await r.json()`.
 *
 * So: one helper that never throws for a status, one error type that carries the status, and one
 * honest sentence for the case where the service could not be reached at all.
 */

/** The service answered, but not with what was asked for. `status` is the server's. */
export class HttpError extends Error {
  constructor(readonly status: number, message?: string) {
    super(message ?? `security answered ${status}`);
    this.name = 'HttpError';
  }
}

/** The service did not answer: DNS, connection refused, TLS, the browser going offline. */
export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super('Security service unreachable.');
    this.name = 'NetworkError';
    this.cause = cause;
  }
}

/**
 * A fetch whose only failure mode is {@link NetworkError}. Statuses come back untouched, because
 * "which status means what" is the caller's decision — 403 is a real answer to a sign-in and a
 * bug anywhere else.
 */
export const request = async (url: string, init: RequestInit = {}): Promise<Response> => {
  try {
    return await fetch(url, init);
  } catch (unreachable) {
    throw new NetworkError(unreachable);
  }
};

/**
 * The body of a response that may not have one — a 502 from a proxy is HTML, a 504 is often empty.
 * Reading it must never be able to end a handler, so a parse failure is an empty object.
 */
export const bodyOf = async <T extends object>(response: Response): Promise<Partial<T>> =>
  response.json().then((body: unknown) =>
    (body !== null && typeof body === 'object' ? body as Partial<T> : {})).catch(() => ({}));

/**
 * What to put on the screen when something went wrong, in the user's terms.
 *
 * @param failure   whatever was caught
 * @param expected  messages for the statuses this call actually expects, e.g.
 *                  `{401: 'Wrong e-mail or password.'}`. Anything not listed is reported as a
 *                  server-side problem rather than blamed on the person typing.
 */
export const messageFor = (failure: unknown, expected: Record<number, string>): string => {
  if (failure instanceof NetworkError) {
    return failure.message;
  }
  if (failure instanceof HttpError) {
    return expected[failure.status] ?? `Security answered ${failure.status}. Please try again.`;
  }
  return 'Something went wrong. Please try again.';
};

/** Turns a non-ok response into an {@link HttpError}, so one `catch` can handle both failure kinds. */
export const orThrow = (response: Response): Response => {
  if (!response.ok) {
    throw new HttpError(response.status);
  }
  return response;
};
