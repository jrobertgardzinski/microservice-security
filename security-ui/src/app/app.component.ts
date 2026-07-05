import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

// the e2e harness points the app at its own test-env service instance (Playwright injects
// window.SECURITY_URL before the app boots); a human on the dev server gets the stack's default
const SECURITY = (window as unknown as { SECURITY_URL?: string }).SECURITY_URL ?? 'http://localhost:8080';

type Mode = 'signin' | 'signup' | 'inbox' | 'me';

/**
 * The auth service's own face: sign in, create an account, land on the "check your mailbox"
 * screen, confirm a mailed verification link (?verify=<token>), and see who you are (/me).
 * Deliberately plain — its real job is being the specs' third entry point: the cucumber-js +
 * Playwright glue in e2e/ drives the very same register.feature and authenticate.feature the
 * JVM runners do, through the data-testid hooks below.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card">
      <ng-container *ngIf="mode === 'me'">
        <h3>Signed in</h3>
        <p>You are <b data-testid="signed-in-email">{{ me }}</b> ({{ roles.join(', ') }}).</p>
        <button data-testid="sign-out" (click)="signOut()">Sign out</button>
      </ng-container>

      <ng-container *ngIf="mode === 'inbox'">
        <h3 data-testid="inbox-screen">Check your mailbox</h3>
        <p>We sent a mail to <b>{{ email }}</b> — follow it to continue.</p>
        <button data-testid="back-to-signin" (click)="switchTo('signin')">Back to sign in</button>
      </ng-container>

      <ng-container *ngIf="mode === 'signin' || mode === 'signup'">
        <nav>
          <button data-testid="tab-signin" [class.active]="mode === 'signin'" (click)="switchTo('signin')">Sign in</button>
          <button data-testid="tab-signup" [class.active]="mode === 'signup'" (click)="switchTo('signup')">Create account</button>
        </nav>
        <form (ngSubmit)="mode === 'signup' ? signUp() : signIn()">
          <input data-testid="email" name="email" type="text" placeholder="e-mail" [(ngModel)]="email">
          <input data-testid="password" name="password" type="password" placeholder="password" [(ngModel)]="password">
          <button data-testid="submit" type="submit">{{ mode === 'signup' ? 'Create account' : 'Sign in' }}</button>
        </form>
      </ng-container>

      <p *ngIf="notice" data-testid="notice" class="notice">{{ notice }}</p>
      <div *ngIf="emailErrors.length || passwordErrors.length" data-testid="validation-errors" class="notice">
        That will not do:
        <ul data-testid="email-errors"><li *ngFor="let e of emailErrors">{{ prettify(e) }}</li></ul>
        <ul data-testid="password-errors"><li *ngFor="let e of passwordErrors">{{ prettify(e) }}</li></ul>
      </div>
    </div>
  `,
  styles: [`
    .card { background: #fff; border: 1px solid #e3e5e8; border-radius: 10px; padding: 28px; width: 22rem; }
    nav { display: flex; gap: .5rem; margin-bottom: 1rem; }
    nav button { background: none; border: none; padding: .3rem .1rem; cursor: pointer; color: #6a737d; }
    nav button.active { color: #1a7fff; border-bottom: 2px solid #1a7fff; }
    form { display: grid; gap: .6rem; }
    input { padding: .5rem; border: 1px solid #ccc; border-radius: 6px; }
    form > button, .card > button { padding: .5rem; border-radius: 6px; border: 1px solid #1a7fff; background: #1a7fff; color: #fff; cursor: pointer; }
    .notice { color: #9a3412; background: #fff7ed; border-radius: 6px; padding: .5rem .7rem; }
    ul { margin: .3rem 0 0; padding-left: 1.2rem; }
  `],
})
export class AppComponent implements OnInit {
  mode: Mode = 'signin';
  email = '';
  password = '';
  me = '';
  roles: string[] = [];
  notice: string | null = null;
  emailErrors: string[] = [];
  passwordErrors: string[] = [];

  // fetch resolutions land outside Angular's zone here, so change detection is nudged
  // explicitly after every async update — the view is small and this keeps it bulletproof
  constructor(private readonly cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    // the verification mail links here (?verify=<token>); confirm it on arrival
    const mailed = new URLSearchParams(location.search).get('verify');
    if (!mailed) return;
    history.replaceState(null, '', location.pathname);
    void fetch(`${SECURITY}/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: mailed }),
    }).then((r) => {
      this.notice = r.ok
        ? 'E-mail verified — sign in below.'
        : 'This verification link was already used or replaced by a newer one.';
      this.cdr.detectChanges();
    });
  }

  async signIn(): Promise<void> {
    this.reset();
    const r = await fetch(`${SECURITY}/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: this.email, password: this.password }),
    });
    if (r.ok) {
      const body: { accessToken: string } = await r.json();
      const meResponse = await fetch(`${SECURITY}/me`, {
        headers: { Authorization: `Bearer ${body.accessToken}` },
      });
      const meBody: { email: string; roles?: string[] } = await meResponse.json();
      this.me = meBody.email;
      this.roles = meBody.roles ?? [];
      this.mode = 'me';
    } else if (r.status === 403) {
      this.notice = 'E-mail not verified yet — follow the link in the mail first.';
    } else if (r.status === 429) {
      this.notice = 'Too many failed attempts — this source is blocked for a while.';
    } else {
      this.notice = 'Wrong e-mail or password.';
    }
    this.cdr.detectChanges();
  }

  async signUp(): Promise<void> {
    this.reset();
    const r = await fetch(`${SECURITY}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: this.email, password: this.password }),
    });
    if (r.status === 201) {
      // taken addresses answer identically (anti-enumeration) — the mail says which it was
      this.mode = 'inbox';
    } else if (r.status === 422) {
      const errors: { emailErrors?: string[]; passwordErrors?: string[] } = await r.json();
      this.emailErrors = errors.emailErrors ?? [];
      this.passwordErrors = errors.passwordErrors ?? [];
    } else {
      this.notice = `Registration failed (${r.status}).`;
    }
    this.cdr.detectChanges();
  }

  signOut(): void {
    this.me = '';
    this.roles = [];
    this.switchTo('signin');
  }

  switchTo(mode: Mode): void {
    this.mode = mode;
    this.reset();
  }

  prettify(code: string): string {
    return code.toLowerCase().replaceAll('_', ' ');
  }

  private reset(): void {
    this.notice = null;
    this.emailErrors = [];
    this.passwordErrors = [];
  }
}
